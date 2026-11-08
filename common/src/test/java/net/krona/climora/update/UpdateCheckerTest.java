package net.krona.climora.update;

import net.krona.climora.update.UpdateChecker.Candidate;
import net.krona.climora.update.UpdateChecker.Type;
import net.krona.climora.update.UpdateChecker.Update;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class UpdateCheckerTest {
    private static final String MC = "1.21.1";

    private static List<Candidate> modrinth(String... versions) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < versions.length; i++) {
            String[] parts = versions[i].split("\\|");
            json.append(i > 0 ? "," : "").append("{\"id\":\"id").append(i).append("\",\"version_number\":\"")
                    .append(parts[0]).append("\",\"version_type\":\"").append(parts[1]).append("\"}");
        }
        return UpdateChecker.modrinthFiles(json.append("]").toString(), "climora");
    }

    @Test
    void newerReleaseIsReported() {
        Update update = UpdateChecker.choose(modrinth("0.2|release", "0.3|release"), "0.2", MC);
        assertNotNull(update);
        assertEquals("0.3", update.latest());
        assertEquals(Type.RELEASE, update.type());
        assertEquals("https://modrinth.com/mod/climora/version/id1", update.url());
    }

    @Test
    void sameOrOlderIsSilent() {
        assertNull(UpdateChecker.choose(modrinth("0.2|release", "0.1|release"), "0.2", MC));
        assertNull(UpdateChecker.choose(modrinth("0.2.0|release"), "0.2", MC));
        assertNull(UpdateChecker.choose(modrinth(), "0.2", MC));
        assertNull(UpdateChecker.choose(UpdateChecker.modrinthFiles("not json", "climora"), "0.2", MC));
    }

    @Test
    void betasAndAlphasAreOfferedWithTheirType() {
        Update beta = UpdateChecker.choose(modrinth("0.3-beta.1|beta"), "0.2", MC);
        assertNotNull(beta);
        assertEquals(Type.BETA, beta.type());
        assertEquals("0.3-beta.1", beta.latest());
        assertEquals(Type.ALPHA, UpdateChecker.choose(modrinth("0.3|alpha"), "0.2", MC).type());
    }

    @Test
    void numbersCompareNumericallyNotAsText() {
        assertNotNull(UpdateChecker.choose(modrinth("0.10|release"), "0.9", MC));
    }

    @Test
    void minecraftPrefixAndBuildSuffixAreIgnored() {
        assertNotNull(UpdateChecker.choose(modrinth("1.21.1-0.3|release"), "0.2", MC));
        assertNull(UpdateChecker.choose(modrinth("1.21.1-0.2|release"), "0.2", MC));
        assertNotNull(UpdateChecker.choose(modrinth("0.3+1.21.1|release"), "0.2", MC));
    }

    @Test
    void releaseCandidateUpdatesToFinalRelease() {
        assertNotNull(UpdateChecker.choose(modrinth("1.0.0|release"), "1.0.0-rc.2", MC));
        assertNull(UpdateChecker.choose(modrinth("1.0.0|release"), "1.0.0", MC));
        assertNull(UpdateChecker.choose(modrinth("1.0.0-rc.2|beta"), "1.0.0-rc.2", MC));
    }
}
