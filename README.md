# SmoothSave

Async world saving for Minecraft: Fabric — eliminates auto-save freezes.

## How it works

Intercepts `LevelStorage\.save()` and offloads the disk write
to a background thread. The NBT serialization still happens on the main thread,
but the expensive file I/O (compression + atomic replace) runs asynchronously.

## Result

- No more 1-5 second server freezes during auto-save
- TPS stays at 20.000 during world saves
- Zero external dependencies
