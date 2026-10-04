# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Changed

- Rewritten as a server-side Paper 26.2 plugin (Java 25). Forge, Fabric and client mods are no longer needed.
  The Forge 1.20.1 sources are archived under `legacy/forge-1.20.1`; models and textures moved to `resourcepack/`.

### Added

- Decoration placer: adjustable cycle length (1-16 patterns). Fixed: blocks could not be picked up to set reinforcement, decoration or incinerator slots; shift-click now adds a block.
- Milestone 4: reinforcement builder and decoration placer, using blocks from the consumption slots.
- Milestone 3: transporter segment that carries and releases chest minecarts full of gathered items.
- Milestone 2: storage, collector, incinerator and drill-seat segments (see the README for how they behave), with
  the original mod's recipes. Jar names now include the commit id.
- Milestone 1: drill head, basic drill segment, fuel and power GUI, 3x3 drilling and movement, protection-plugin
  checks, persistence, `/caterpillar give|reload|list`.

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