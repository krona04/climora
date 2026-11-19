# Climora

Weather that comes from somewhere.

<center><img src="https://i.imgur.com/5Rs8xo8.png" alt="Climora Banner" width="800px"></center>

> **Early development.** This is an early access build (v0.2). Features can change and bugs can happen.
>
> * Loaders: NeoForge and Fabric
> * Minecraft version: 1.21.1
> * Java: 21
> * Scope: any dimension with an open sky, so the Overworld and modded dimensions like it. The Nether and the End have no weather.

> ### Required
> * **[Architectury API](https://modrinth.com/mod/architectury-api)**
> * **[Fabric API](https://modrinth.com/mod/fabric-api)** *(Fabric only)*

---

## About

Climora replaces Minecraft's global weather cycle with a climate simulation. Vanilla flips a coin for the whole dimension. It rains everywhere or nowhere, the biome decides whether the rain is snow, and nothing leads up to either.

Here, highs and lows drift across the world. Wind blows out of the highs and into the lows, and it carries moisture. Rising moist air turns into cloud. Thick cloud rains. Warm, humid air that is forced upwards turns into a thunderstorm. Each step follows from the one before, and nothing switches on by itself.

So weather is local. It can rain on one side of a lake and stay dry on the other. You can watch a shower arrive from upwind. A desert at night feels nothing like a jungle at the same hour.

---

## Features

### Climate and temperature
- **Weather cells.** The world is split into cells of 128×128 blocks. Each cell is built from the biomes and terrain under it, and the server simulates it without loading chunks.
- **Real temperature.** Air temperature is in °C. It comes from the biome, the elevation (about 3 °C colder per 100 blocks) and the time of day.
- **Every biome has a climate.** A desert swings from +38 °C at noon to +14 °C at night, because dry air loses heat fast. A jungle stays warm and humid around the clock, with a swing of only ±5 °C. The sea barely moves.
- **Honest mountains.** Mountain biomes are already cold, so peaks are not cooled a second time.
- **No seams.** Values are blended between cell centres, so temperature, wind and rain have no steps at cell borders.

### Pressure and wind
- **Weather systems.** Highs and lows a couple of kilometres across drift over the world. They are seeded from the world seed, and each one takes about one in-game day to pass overhead.
- **Wind.** Wind comes from the pressure gradient plus the flow that steers the systems. Gusts pick up in a storm.
- **Transport.** Wind carries moisture and cloud, so weather arrives from upwind and does not appear on top of you.
- **Slant.** Rain leans and snow drifts with the wind.

### Clouds and rain
- **Local rain.** Rain falls only where the simulation puts it, and it has edges.
- **Dynamic clouds.** Clouds are drawn from the simulated cover, not from the vanilla texture. They keep the same block shapes but appear only where there are clouds. Rain clouds sit lower and thicker, and storm clouds grow into towers.
- **Shared sky.** Clouds drift with the weather systems and look the same for every player on a server.
- **Overcast means overcast.** Where it rains, the sky above is closed. Rain never falls out of a blue gap.
- **Rain or snow.** The air temperature at that spot decides, not the biome. In mountains that would get rain lower down, it snows above the freezing line.

### Precipitation
- **Drizzle to downpour.** Rain looks like its intensity. Drizzle is sparse, slow and quiet. A downpour is dense, fast and loud, and it hides the distance.
- **Sleet.** Just above freezing, rain and wet snow fall together.
- **Hail.** Hail is rare. Only the strongest thunderstorms on a hot afternoon drop ice pellets, in short bursts over part of the storm. The pellets shatter on the ground and sound duller on a roof overhead. Hurting players and mobs is a separate option, off by default.
- **Freezing rain.** When warm air blows in aloft over frozen ground, snow melts on the way down and the drops freeze on whatever they hit. The ground gets a glaze of ice that is as slippery as ice, and it melts once the air warms up. It happens by itself where warm air meets a cold region.
- **Distant showers.** A far shower looks like a grey curtain of rain from its cloud to the ground, leaning with the wind. You can watch it come, and you can hear it from the side before it reaches you.
- **Fog.** On still, humid, clear nights, the air near the ground cools below its dew point and fog settles in the lowlands. The morning sun burns it off.
- **Puddles.** Steady rain fills the hollows of natural ground with puddles. They join into one sheet of water, dry out after the rain and freeze over in frost.
- **Grey skies.** A closed cloud deck greys the sky and hides the sun, even without rain.

### Thunderstorms
- Storms build on their own where warm, very humid air is forced upwards, either by a low or by afternoon heating.
- **Local lightning** strikes only inside storm cells and only where rain is falling. Lightning rods and target selection work as in vanilla.

### Snow and ice
- Snow builds up in layers while it falls.
- Still water freezes at the edges below −1 °C.
- Snow and ice melt anywhere warmer than +1 °C, including in the dark. Vanilla needs bright light, which is why shaded snow used to last forever.

### Weather vane
- This block has an arrow that turns to face the wind and sways in the gusts.
- Put a comparator next to it to read the wind speed on the Beaufort scale as a redstone signal of 0 to 15.

### Vanilla integration
Local weather drives the vanilla systems.
- Mobs get wet, cauldrons fill, snow settles and farmland is watered.
- Rain puts out fires and stops them from spreading where it rains.
- Beds work in a thunderstorm when the storm is over the bed.
- Bees go home and stay in the hive while it rains over them. Monsters spawn in the gloom of a storm. Foxes and pandas react to thunder overhead.
- Data-pack loot conditions on the weather check the weather where the loot drops.
- The vanilla `/weather clear|rain|thunder` command turns on local weather within about 4 km of the player. You can switch this off.

### Climate Belts world type
- Climate Belts is a new world type. It lays out biomes by latitude, as on Earth. The equator runs along z = 0, and the poles are 10 000 blocks north and south of it.
- You get rainforest at the equator, then deserts and badlands in the subtropics, then savanna, temperate forests, taiga and tundra. Past the poles there is only snow and ice.
- Belt borders stay ragged, and each belt keeps patches of its neighbours, so the map does not look striped.
- Terrain, caves and structures stay vanilla. The default world type is unchanged.

### Forecast
- `/climora forecast` shows what the next 12 in-game hours bring: pressure and its trend, sky, rain or snow, and thunderstorms. The pressure part is exact, because the weather systems are known in advance. Rain is estimated from the air upwind.
- **Weather speed.** One setting makes weather develop faster or slower, from ×0.25 to ×8. It does not change the length of the day or the wind speeds.

### Debug
- An F3 overlay shows the air temperature where you stand, the state of your cell and the simulation load. It works in singleplayer and on servers that run the mod.

---

## Commands

All commands need permission level 2.

### Reading the weather

| Command | Description |
|---|---|
| `/climora temperature [pos]` | Air temperature at a position |
| `/climora cell [pos]` | A cell's state: temperature, pressure, humidity, cloud, rain, hail, air aloft, wet ground, fog |
| `/climora forecast [pos]` | What the weather does in the next 1, 3, 6 and 12 in-game hours |
| `/climora stats` | Cell counts, simulation load and how often it rained, stormed and hailed |

### Controlling the weather

| Command | Description |
|---|---|
| `/climora weather clear\|cloudy\|fog\|drizzle\|rain\|downpour\|storm\|freezing_rain\|hail [radius] [seconds]` | Force weather over a radius, measured in cells |
| `/climora weather auto` | Hand control back to the simulation |
| `/weather clear\|rain\|thunder` | Vanilla command, redirected to local weather within ~4 km |

### Maintenance

| Command | Description |
|---|---|
| `/climora resample [radius]` | Rebuild cells from the terrain. Use it after `/fillbiome` |
| `/climora stress <radius>` | Load test: simulate a much larger area |
| `/climora reload` | Reload `config/climora.json` |

---

## Blocks

| Block | Recipe | Description |
|---|---|---|
| Weather Vane | 4 copper ingots + 1 iron ingot | Points into the wind. A comparator reads its strength on the Beaufort scale (0 to 15) |
| Glaze Ice | None (made by freezing rain) | A pixel-thin crust of ice. It is slippery, melts above freezing and drops nothing |
| Puddle | None (made by rain) | Rain water in a hollow. It joins its neighbours, dries out and freezes over. Anything placed on it replaces it |

```
C C C      C = copper ingot
  I        I = iron ingot
  C
```

---

## Localization

The mod is translated into twenty languages. Command output, block names, death messages and the world type follow the player's language setting.

| | | | |
|---|---|---|---|
| English `en_us` | Russian `ru_ru` | Ukrainian `uk_ua` | Polish `pl_pl` |
| German `de_de` | French `fr_fr` | Spanish `es_es` | Portuguese (Brazil) `pt_br` |
| Portuguese (Portugal) `pt_pt` | Italian `it_it` | Chinese (Simplified) `zh_cn` | Japanese `ja_jp` |
| Korean `ko_kr` | Chinese (Traditional) `zh_tw` | Turkish `tr_tr` | Dutch `nl_nl` |
| Czech `cs_cz` | Swedish `sv_se` | Vietnamese `vi_vn` | Indonesian `id_id` |

---

## License

Climora uses the MIT License.

- Modpacks: you can include it in any modpack.
- Source code: you can view, modify and distribute it if you keep the copyright notice and license text.

---

## Bug reports and suggestions

Found a bug, or is the weather doing something odd? Please open an issue.
