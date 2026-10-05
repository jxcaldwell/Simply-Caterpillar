# Simply Caterpillar (Paper plugin)

A caterpillar drill that digs a 3x3 tunnel for you, as a **server-side Paper plugin**. No Forge, no Fabric and
no client mods: players join with a vanilla client.

This is a port of the Forge mod [Simply Caterpillar](https://github.com/The-Fireplace-Minecraft-Mods/Simply-Caterpillar)
(credits: The_Fireplace and Daniel-Mendes, and Daniel Appleby for the original Caterpillar mod; MIT licensed).
The original Forge 1.20.1 sources are kept for reference in [`legacy/forge-1.20.1`](legacy/forge-1.20.1) and the
original models and textures in [`resourcepack`](resourcepack) for a later resource-pack milestone.

**Target:** Paper 26.2, Java 25.

## Status: milestone 5 (all milestones done)

| Part | Status |
|---|---|
| Drill head, basic drill segment, fuel, power button, drilling, movement | done |
| Storage, item collector, incinerator, drill seat | done |
| Transporter | done |
| Reinforcement builder, decoration placer | done |
| Optional resource pack with the original models | done (this milestone) |

## How to play

1. Craft a **Basic Drill Segment** (`c c / crc / cpc`: cobblestone, redstone, any planks) and a **Drill Head**
   (`iii / ·d· / ·f·`: iron ingots, a drill segment, a furnace). Admins can use `/caterpillar give <part>`.
2. Place the **Drill Head** on the ground: it builds a 3x3 cutting face with the base block behind it, facing the way
   you are looking.
3. Place **Basic Drill Segments** in a line directly behind the head to lengthen the caterpillar.
4. Right-click any part of the caterpillar to open the head's GUI. Put fuel in the second slot of the top row and
   click the power button.

### Attachments

Craft each one from a Basic Drill Segment and place it behind the head like any segment.

| Part | Recipe | What it does |
|---|---|---|
| Storage Segment | chest, segment, chest (in a row) | Right-click it for 9 consumption + 9 gathered slots. Collectors fill the gathered slots of the head first, then each storage segment. |
| Collector Segment | segment above a hopper | Picks up dropped items within 3 blocks of itself every half second while the caterpillar runs, and right after the drill breaks blocks. Put it directly behind the head. |
| Incinerator Segment | furnace, segment, lava bucket (top to bottom) | Right-click it to choose which item types it destroys in the gathered slots. Starts with gravel, sand, red sand, cobblestone and dirt. Click an item type to remove it; click with an item on the cursor to add it. |
| Transporter Segment | chain, segment, chain over a hopper (`c d c` / ` h `) | Hangs a chest minecart block under itself, moves full stacks of gathered items into it and sends it off as a real chest minecart when every slot is a full stack. See below. |
| Reinforcement Builder | pistons on all four sides of a segment (` p ` / `pdp` / ` p `) | Lines the ring of blocks just outside the tunnel (ceiling and floor 5 wide, walls 3 high) from two blocks ahead to two behind, every time it moves. Right-click to pick the block for each position (click a slot with a block on the cursor, or shift-click a block in your inventory) and, per side, what may be replaced: water, lava, falling blocks, air/plants, or everything. Defaults (original mod): cobblestone; seal water and lava everywhere, support falling ceilings, fill floor gaps. |
| Decoration Placer | dispenser, segment, dispenser | Places one pattern in the tunnel slice it has just left, moving to the next pattern each block. Each pattern is the 8 blocks around the middle of the slice. The cycle length is adjustable from 1 to 16 patterns (default 10, as in the original), so whatever is in one pattern is placed every N blocks. Default is the original "mineshaft": a rail line, a plank-and-fence frame every 10 blocks, wall torches and a powered rail with its redstone torch. Torches go on the wall when they can't stand. |
| Drill Seat | cauldron above a segment | Right-click to sit and ride along; anyone may use it. Sneak or jump to get off. |

**Transporter details.** Put chest minecarts in the drill head's (or a storage segment's) *consumption* slots; the
transporter takes one when it has none. It needs free space below itself (the bottom row of the tunnel); a rail
there is remembered and put back when the cart moves on. The cart block can be opened like the transporter itself
to see its cargo. When the cargo is full the block is replaced by a chest minecart; if it was released onto a rail it
rolls backwards along the track. Breaking the cart block or the transporter returns the minecart and its cargo.

**Consumption slots.** Reinforcement and decoration blocks, and the transporter's chest minecarts, are taken from the
consumption slots (middle row) of the drill head first and then of each storage segment. If the block is not there,
that position is skipped and the owner gets a chat message ("out of Rail for the Decoration Placer"), at most
once a minute per item (`supply-warning-seconds`). When the collector picks up an item that a part uses (a block in a
reinforcement or decoration pattern, or a chest minecart for a transporter), it goes to the consumption slots first,
until there are 64 of it there (`collector.supply-amount`); the rest goes to the gathered slots. Every placed block is checked against protection plugins as the owner; a block the
reinforcement builder replaces is broken first, so its drops can be collected.

While powered the drill burns fuel (one unit per tick per part: the longer the caterpillar, the hungrier it is),
breaks the 3x3 area in front of it every 3 seconds, steps forward, and the segments follow one by one.
Breaking a part of the caterpillar takes the whole thing apart and returns the parts and stored items.

## Resource pack (optional)

Players are offered a resource pack with the original mod's models when they join. With it:

* caterpillars in the world show the original 3D models (drill head with its spinning-bit "drilling" look, storage
  chests on the sides, collector hopper, transporter cart, reinforcement pistons...), and
* part items show their original icons.

Players who decline it, and Bedrock players joining through Geyser, keep seeing the plain blocks; nothing breaks for
them. Players with the pack see the plain blocks as invisible barriers and break caterpillar parts by **sneaking and
left-clicking** them.

Every build on GitHub publishes the pack (`SimplyCaterpillar-pack.zip`) as a pre-release next to the jar, and the jar
knows the link and checksum of its own pack, so nothing needs configuring. Settings are under `resource-pack:` in
`config.yml` (turn it off, make it required, or host the pack yourself). The pack is built from
[`resourcepack/`](resourcepack); `tools/generate_pack.py` regenerates its 26.2 item definitions. The part icons
replace the icons of the default placeholder blocks only for part items; if you change `blocks:` in the config, the
part icons fall back to the plain block icons (the world models are unaffected).

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
