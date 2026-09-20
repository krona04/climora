# 🌦️ Climora

**Weather That Comes From Somewhere.**

<center><img src="https://i.imgur.com/5Rs8xo8.png" alt="Climora Banner" width="800px"></center>

> ⚠️ **EARLY DEVELOPMENT WARNING**
>
> This mod is currently in **Early Access (v0.1)** — the first public release. Features are subject to change and bugs may occur.
>
> * **Loaders:** NeoForge & Fabric
> * **Minecraft Version:** 1.21.1
> * **Java:** 21
> * **Scope:** Works in any dimension with an open sky — the Overworld and modded dimensions like it. The Nether and the End have no weather.

> ### Required
> * **[Architectury API](https://modrinth.com/mod/architectury-api)**
> * **[Fabric API](https://modrinth.com/mod/fabric-api)** *(Fabric only)*

---

## 📖 About

**Climora** replaces Minecraft's global weather cycle with a climate simulation. Vanilla flips a coin for an entire dimension: it rains everywhere or nowhere, the biome decides whether that rain is snow, and nothing leads up to either.

Here, highs and lows drift across the world, wind blows out of the highs and into the lows carrying moisture with it, rising moist air turns into cloud, thick enough cloud rains, and warm humid air forced upwards turns into a thunderstorm. Every step follows from the one before it. Nothing switches itself on.

The result is that weather is **local**. It can rain on one side of a lake and not the other, you can watch a shower arrive from upwind, and a desert at night is nothing like a jungle at the same hour.

---

## ✨ Features

### 🌡️ Climate & Temperature
- **Weather Cells** — The world is divided into cells of 128×128 blocks, built from the biomes and terrain underneath them and simulated on the server without loading chunks.
- **Real Temperature** — Air temperature in °C, from the biome, the elevation (about 3 °C colder per 100 blocks) and the time of day.
- **Every Biome Has a Climate** — A desert swings from **+38 °C** at noon to **+14 °C** at night because dry air loses heat fast. A jungle stays warm and humid around the clock with a swing of only **±5 °C**. The sea barely moves at all.
- **Honest Mountains** — Mountain biomes are cold in their own right, so peaks are not cooled twice.
- **No Seams** — Values are blended between cell centres. Temperature, wind and rain have no steps at cell borders.

### 🌀 Pressure & Wind
- **Weather Systems** — Highs and lows a couple of kilometres across drift across the world, seeded from the world seed, taking about one in-game day to pass overhead.
- **Wind** — Computed from the pressure gradient plus the flow steering those systems. Gusts pick up in a storm.
- **Transport** — Wind carries moisture and cloud, so weather arrives from upwind instead of appearing on top of you.
- **Slant** — Rain leans and snow drifts with the wind.

### ☁️ Clouds & Rain
- **Local Rain** — Rain falls only where the simulation puts it, and it has edges.
- **Dynamic Clouds** — Clouds are drawn from the simulated cover instead of the vanilla texture: the same block shapes, but only where there really are clouds. Rain clouds sit lower and thicker, storm clouds grow into towers.
- **Shared Sky** — Clouds drift with the weather systems and look identical for every player on a server.
- **Overcast Means Overcast** — Where it rains, the sky above is shut. Rain never falls out of a blue gap.
- **Rain or Snow** — Decided by the air temperature at that spot, not by the biome, so it snows above the freezing line in mountains that would rain lower down.

### ⛈️ Thunderstorms
- Storms build on their own where warm, very humid air is forced upwards — by a low, or by afternoon heating.
- **Local Lightning** strikes only inside storm cells and only where rain is falling. Lightning rods and target selection work as in vanilla.

### ❄️ Snow & Ice
- Snow **builds up in layers** while it falls.
- Still water **freezes at the edges** below −1 °C.
- Snow and ice **melt anywhere warmer than +1 °C — including in the dark**. Vanilla needs bright light, which is why shaded snow used to last forever.

### 🧭 Weather Vane
- A block whose arrow turns to face the wind and sways in the gusts.
- Put a **comparator** next to it to read the wind speed on the **Beaufort scale** as a redstone signal of 0–15.

### 🔗 Vanilla Integration
- Local weather drives the vanilla systems: mobs get wet, fires go out, cauldrons fill, snow settles.
- Vanilla `/weather clear|rain|thunder` turns on local weather within about 4 km of the player. Can be switched off.

### 🐛 Debug
- An **F3 overlay** with the air temperature where you stand, the state of your cell and the simulation load. Works in singleplayer and on servers running the mod.

---

## 🔧 Commands Reference

All commands require **permission level 2**.

### Reading the Weather

| Command | Description |
|---|---|
| `/climora temperature [pos]` | Air temperature at a position |
| `/climora cell [pos]` | A cell's state: mean temperature, daily swing, pressure, humidity, cloud, rain |
| `/climora stats` | Cell counts and simulation load |

### Controlling the Weather

| Command | Description |
|---|---|
| `/climora weather clear\|cloudy\|rain\|storm [radius] [seconds]` | Force weather over a radius, measured in cells |
| `/climora weather auto` | Hand control back to the simulation |
| `/weather clear\|rain\|thunder` | Vanilla command, redirected to local weather within ~4 km |

### Maintenance

| Command | Description |
|---|---|
| `/climora resample [radius]` | Rebuild cells from the terrain — needed after `/fillbiome` |
| `/climora stress <radius>` | Load test: simulate a much larger area |
| `/climora reload` | Reload `config/climora.json` |

---

## 📦 Blocks

| Block | Recipe | Description |
|---|---|---|
| Weather Vane | 4 copper ingots + 1 iron ingot | Points into the wind; a comparator reads its strength on the Beaufort scale (0–15) |

```
C C C      C = copper ingot
  I        I = iron ingot
  C
```

---

## 🖥️ Multiplayer

Install the mod on the server and require it of clients.

- A player **without** Climora on a server **with** it will never see rain — the mod turns off the vanilla global cycle, and that player has nothing to replace it with.
- A client **with** Climora on a server **without** it falls back to ordinary vanilla weather.

---

## 🌍 Localization

| Language | Code |
|---|---|
| English | `en_us` |
| Russian | `ru_ru` |

Translations for more languages are welcome.

---

## ⚖️ License

Licensed under the **MIT License**.

- **Modpacks:** Free to include in any modpack.
- **Source Code:** Free to view, modify and distribute, provided the copyright notice and license text are kept.

---

## 🐛 Bug Reports & Suggestions

Found a bug, or is the weather doing something odd? Please open an issue — there are templates for **bug reports**, **performance reports**, **config questions** and **general feedback** in `.github/ISSUE_TEMPLATE`.
