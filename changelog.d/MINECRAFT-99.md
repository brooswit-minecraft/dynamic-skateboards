bump: minor

### Added
- Repo scaffold for Dynamic Skateboards: NeoForge 1.21.1 project, MIT license, CI with build-and-test and the shared release gate, Sable pinned compile-only for a later story.
- A skateboard item, craftable from oak planks and iron ingots, also available via the creative tab/give.
- Server-authoritative hold-to-skate state: holding the skateboard in the main hand skates; off-hand or switching away does not. The server derives this each tick from the held item and pushes it to tracking clients, which apply a custom lowered stance (not vanilla sneak/crouch).
