package net.krona.climora.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.architectury.platform.Platform;
import net.krona.climora.Climora;
import net.krona.climora.config.ClimoraConfig;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Looks once per launch on Modrinth for a newer file of this mod built for the running Minecraft version
 * and loader. Releases, betas and alphas all count; the type is shown to the player. The request runs on a
 * background thread and any failure (offline, blocked, project not found) is silent: the check never delays
 * or breaks the game. Turn it off with {@code checkForUpdates} in the config.
 * <p>
 * Modrinth's {@code version_number} must contain the mod's own version ({@code 0.3}, {@code 1.0.0-rc.1});
 * a Minecraft version in the same string ({@code 1.21.1-0.3}) is ignored.
 */
public final class UpdateChecker {
    public static final String MOD_NAME = "Climora";
    private static final String SLUG = "climora";
    private static final String API = "https://api.modrinth.com/v2/project/" + SLUG + "/version";
    private static final Pattern NUMBERS = Pattern.compile("\\d+(?:\\.\\d+)*");
    private static final Pattern CLEAN = Pattern.compile("\\d+(?:\\.\\d+)*(?:-(?:alpha|beta|rc|pre)[.\\w]*)?", Pattern.CASE_INSENSITIVE);

    private static volatile Update found;
    private static boolean started;

    private UpdateChecker() {
    }

    /** Stability of a file, as the sites report it. */
    public enum Type {
        RELEASE, BETA, ALPHA;

        static Type of(String text) {
            if ("beta".equalsIgnoreCase(text)) {
                return BETA;
            }
            return "alpha".equalsIgnoreCase(text) ? ALPHA : RELEASE;
        }

        /** Suffix of the translation key: empty for a release. */
        public String key() {
            return this == RELEASE ? "" : "." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** A published file: its version text, stability and page. */
    record Candidate(String number, Type type, String url) {
    }

    /** A file newer than the installed one. */
    public record Update(String current, String latest, Type type, String url) {
    }

    /** The newer file, or {@code null} while the check is running, failed, or found nothing. */
    public static Update result() {
        return found;
    }

    /** Starts the background check. Safe to call on every side; only the first call does anything. */
    public static synchronized void start() {
        if (started || !ClimoraConfig.client().checkForUpdates) {
            return;
        }
        started = true;
        String current;
        try {
            current = Platform.getMod(Climora.MOD_ID).getVersion();
        } catch (RuntimeException e) {
            return;
        }
        String minecraft = Platform.getMinecraftVersion();
        String loader = loader();
        String query = "?loaders=" + encode("[\"" + loader + "\"]") + "&game_versions=" + encode("[\"" + minecraft + "\"]");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        fetch(client, API + query, current).thenAccept(body -> {
            Update update = choose(modrinthFiles(body, SLUG), current, minecraft);
            if (update != null) {
                found = update;
                Climora.LOGGER.info("{} {} ({}) is available (installed: {}): {}", MOD_NAME, update.latest(),
                        update.type().name().toLowerCase(Locale.ROOT), current, update.url());
            }
        });
    }

    /** The response body, or an empty string when Modrinth cannot be reached or does not know the mod. */
    private static CompletableFuture<String> fetch(HttpClient client, String url, String current) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "krona04/" + SLUG + "/" + current + " (update check)")
                .header("Accept", "application/json")
                .GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> response.statusCode() == 200 ? response.body() : "")
                .exceptionally(error -> {
                    Climora.LOGGER.debug("Update check of {} failed: {}", url, error.toString());
                    return "";
                });
    }

    private static String loader() {
        return Platform.isFabric() ? "fabric" : "neoforge";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Files of a Modrinth version list (already filtered by the site to our loader and Minecraft version). */
    static List<Candidate> modrinthFiles(String body, String slug) {
        List<Candidate> result = new ArrayList<>();
        for (JsonObject version : objects(body)) {
            String number = string(version, "version_number");
            String id = string(version, "id");
            if (number != null) {
                result.add(new Candidate(number, Type.of(string(version, "version_type")),
                        "https://modrinth.com/mod/" + slug + (id != null ? "/version/" + id : "")));
            }
        }
        return result;
    }

    /** The newest candidate, if it is newer than {@code current}.*/
    static Update choose(List<Candidate> candidates, String current, String minecraft) {
        List<Integer> installed = numbers(current, minecraft);
        if (installed == null) {
            return null;
        }
        Candidate best = null;
        List<Integer> bestNumbers = null;
        for (Candidate candidate : candidates) {
            List<Integer> parsed = numbers(candidate.number(), minecraft);
            if (parsed == null) {
                continue;
            }
            int order = compare(parsed, installed);
            // 1.0.0-rc.2 is older than 1.0.0 although the numbers are equal.
            boolean newer = order > 0
                    || (order == 0 && isPreRelease(current) && candidate.type() == Type.RELEASE && !isPreRelease(candidate.number()));
            if (newer && (best == null || compare(parsed, bestNumbers) > 0)) {
                best = candidate;
                bestNumbers = parsed;
            }
        }
        return best == null ? null : new Update(current, shown(best.number(), minecraft), best.type(), best.url());
    }

    /** What to show as the new version: the numbers with a pre-release tag, without file-name decoration. */
    private static String shown(String number, String minecraft) {
        Matcher matcher = CLEAN.matcher(number.replace(minecraft, " "));
        return matcher.find() ? matcher.group() : number;
    }

    /** Objects of the JSON array in the response. */
    private static List<JsonObject> objects(String body) {
        List<JsonObject> result = new ArrayList<>();
        try {
            JsonElement root = JsonParser.parseString(body);
            if (root.isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray()) {
                    if (element.isJsonObject()) {
                        result.add(element.getAsJsonObject());
                    }
                }
            }
        } catch (RuntimeException e) {
            // not JSON: no files
        }
        return result;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    /** The mod's own numbers from a version or file name, or {@code null} when there are none. */
    private static List<Integer> numbers(String version, String minecraft) {
        if (version == null) {
            return null;
        }
        // The Minecraft version often sits next to the mod's own: 1.21.1-0.3, mod-0.3+1.21.1, mod-0.3.jar.
        Matcher matcher = NUMBERS.matcher(version.replace(minecraft, " "));
        if (!matcher.find()) {
            return null;
        }
        List<Integer> result = new ArrayList<>();
        try {
            for (String part : matcher.group().split("\\.")) {
                result.add(Integer.parseInt(part));
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return result;
    }

    private static boolean isPreRelease(String version) {
        return version != null && version.matches("(?i).*-(alpha|beta|rc|pre|snapshot).*");
    }

    private static int compare(List<Integer> a, List<Integer> b) {
        for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
            int x = i < a.size() ? a.get(i) : 0;
            int y = i < b.size() ? b.get(i) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }
}
