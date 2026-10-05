# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

## [1.0.0] - 2026-10-04

First release of Simply Caterpillar as a **server-side Paper 26.2 plugin** (Java 25). Forge, Fabric and client mods
are no longer needed: players join with a vanilla client, and Bedrock players through Geyser work too.

### Parts

- **Drill head** with fuel and a power button: digs a 3x3 tunnel, one block every 3 seconds, and moves forward; the
  segments follow one by one. Fuel use grows with the length of the caterpillar.
- **Basic drill segment**, **storage** (9 consumption + 9 gathered slots), **collector** (picks up drops),
  **incinerator** (destroys chosen item types), **drill seat** (ride along), **transporter** (fills chest minecarts
  with full stacks and sends them down the rails), **reinforcement builder** (seals the tunnel walls, ceiling and
  floor against water, lava and falling blocks) and **decoration placer** (rails, supports and torches from up to 16
  repeating patterns).
- All crafted with the original mod's recipes; `/caterpillar give|reload|list` for admins.

### Server features

- Each caterpillar belongs to the player who placed it; protection plugins (GriefPrevention, WorldGuard...) are
  checked as that player for every block broken or placed.
- Caterpillars pause while their owner is offline or their chunks are unloaded, survive restarts, and cannot be
  blown up or pushed by pistons.
- Collected items that a part needs go to its supply first; the owner is told when a part runs out of something.
- Configurable fuels, speeds, blocks, limits and messages (MiniMessage language file).

### Optional resource pack

- The original 3D models and item icons, offered to players when they join. Players who decline it, and Bedrock
  players, see plain blocks instead; nothing else changes for them.

### Changes from the Forge mod

- Parts are one block wide (the original storage, reinforcement and decoration parts were wider; with the resource
  pack their side pieces are still shown).
- Breaking the drill head takes the whole caterpillar apart and returns the parts and stored items.
- The original Forge 1.20.1 sources are archived under `legacy/forge-1.20.1`.

## [1.20.1-8.0.2] - 2024-01-04

Removed the `Pattern Book` item, while keep crashing on servers.

### Fixed

- Fixed item collector and reinforcement builder blocks placement.
- Fixed empty bucket not returned after lava bucket consumed.
- Fixed drill head particles position.

### Updated

- Rotated drill_bits 45deg, so they don't look like a cross.

## [1.20.1-8.0.1] - 2023-08-24

### Fixed

- Fixed a bug in pattern book gui with text.

## [1.20.1-8.0.0] - 2023-08-17

First release of the forge 1.20.1 version

## [1.19.4-7.4.0] - 2023-08-17

### Updated

- Changed texture of the incinerator.
- Changed texture of the reinforcement.
- Changed texture of the drill head.

## [1.19.4-7.3.1] - 2023-04-13

### Fixed

- Fixed a bug where the item collector would not use the drill storage inventory.
- Fixed a bug where decoration pattern would not be updated when the pattern book was used.

## [1.19.4-7.3.0] - 2023-04-12

### Added

- Update GUI inventory if changed while opened.
- Added pattern book item, you can now transfer patterns between decoration blocks.

## [1.19.4-7.2.2] - 2023-03-15

First release of the forge 1.19.4 version

### Fixed

- Drill head blade top fixed missing particle texture.