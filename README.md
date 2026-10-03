# Simply Caterpillar (Paper plugin)

A caterpillar drill that digs a 3x3 tunnel for you, as a **server-side Paper plugin**. No Forge, no Fabric and
no client mods: players join with a vanilla client.

This is a port of the Forge mod [Simply Caterpillar](https://github.com/The-Fireplace-Minecraft-Mods/Simply-Caterpillar)
(credits: The_Fireplace and Daniel-Mendes, and Daniel Appleby for the original Caterpillar mod; MIT licensed).
The original Forge 1.20.1 sources are kept for reference in [`legacy/forge-1.20.1`](legacy/forge-1.20.1) and the
original models and textures in [`resourcepack`](resourcepack) for a later resource-pack milestone.

**Target:** Paper 26.2, Java 25.

## Status: milestone 1

| Part | Status |
|---|---|
| Drill head, basic drill segment, fuel, power button, drilling, movement | done (this milestone) |
| Storage, item collector, incinerator, drill seat | planned (milestone 2) |
| Transporter | planned (milestone 3) |
| Reinforcement builder, decoration placer | planned (milestone 4) |
| Optional resource pack with the original models | planned (milestone 5) |

## How to play

1. Craft a **Basic Drill Segment** (`c c / crc / cpc`: cobblestone, redstone, any planks) and a **Drill Head**
   (`iii / ·d· / ·f·`: iron ingots, a drill segment, a furnace). Admins can use `/caterpillar give <part>`.
2. Place the **Drill Head** on the ground: it builds a 3x3 cutting face with the base block behind it, facing the way
   you are looking.
3. Place **Basic Drill Segments** in a line directly behind the head to lengthen the caterpillar.
4. Right-click any part of the caterpillar to open the head's GUI. Put fuel in the second slot of the top row and
   click the power button.

While powered the drill burns fuel (one unit per tick per part: the longer the caterpillar, the hungrier it is),
breaks the 3x3 area in front of it every 3 seconds, steps forward, and the segments follow one by one.
Breaking a part of the caterpillar takes the whole thing apart and returns the parts and stored items.

## Server notes

* Every block the drill breaks is checked against protection plugins as the caterpillar's owner (the player who
  placed the head), by firing a normal `BlockBreakEvent`; placement fires `BlockPlaceEvent`. A denial stops the drill.
* A caterpillar only works while its owner is online and its chunks are loaded; otherwise it pauses.
* Caterpillar blocks are immune to explosions and pistons (configurable).
* Data is stored in `plugins/SimplyCaterpillar/caterpillars.yml`.

### Commands and permissions

| | |
|---|---|
| `/caterpillar give <part> [player] [amount]`, `reload`, `list` | `simplycaterpillar.admin` (default: op) |
| `simplycaterpillar.place`, `.use`, `.craft` | default: everyone |
| `simplycaterpillar.admin` also lets you open and break other players' caterpillars | |

## Building

Requires JDK 25. `./gradlew build` produces `build/libs/SimplyCaterpillar-<version>.jar`; copy it into the server's
`plugins` folder. The machine logic (`dev.the_fireplace.caterpillar.core`) has no Bukkit dependency and is covered
by unit tests that run it against an in-memory world.

## License

MIT, see [LICENSE](LICENSE).
