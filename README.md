# Dynamic Skateboards

Arcade-style skateboarding for NeoForge Minecraft 1.21.1. Peer of `dynamic-vehicles`
and `dynamic-atmosphere` in the `brooswit-minecraft` org; not a module of either.

First playable vertical slice spec: see epic MINECRAFT-92 and its linked
Confluence handoff doc (internal). This repo starts with story (a): repo
scaffold, a skateboard item, and hold-to-skate state.

## Building

```
./gradlew build
```

Sable (compile-only, PolyForm Shield 1.0.0) is downloaded and sha512-verified
by the `downloadSable` Gradle task; the modpack supplies it at runtime.

## Releases

`version` in `gradle.properties` is assigned by the release workflow from
`changelog.d/` fragments — never hand-edit it. See `changelog.d/README.md`.
