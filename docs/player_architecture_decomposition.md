# Player Architecture Refactoring

This document outlines the refactoring of the video player subsystem (`xyz.mpv.rex.ui.player`).

## Why Refactor?

Over time, `PlayerActivity` and `PlayerViewModel` had grown into large files handling many unrelated responsibilities—including audio focus, Picture-in-Picture, intent parsing, playlist loading, gestures, and screenshots.

To make the player codebase cleaner, easier to navigate, and simpler to test, these responsibilities were split into smaller, dedicated classes.

## Architecture & Design

The refactoring uses a **delegation approach**:
- `PlayerActivity` and `PlayerViewModel` keep their existing public methods and properties so existing UI components, sheets, and controls continue to work without changes.
- Specialized delegate classes and managers take ownership of specific features.
- All existing player behavior, lifecycle ordering, and user settings were kept identical—only the file structure was modularized.

## New Components

### Activity Delegates (`ui/player/delegates/`)
These classes handle Android-specific lifecycle, window, and hardware features:

- **`PlayerKeyEventHandler`**: Hardware buttons (D-pad, media keys, volume).
- **`PlayerOrientationController`**: Screen orientation (Sensor, Video, and Smart orientation modes).
- **`PlayerSystemUiController`**: Fullscreen immersive mode and system bar visibility.
- **`PlayerAudioController`**: Audio focus and unplugged headphone (`BECOMING_NOISY`) handling.
- **`PlayerMediaSessionController`**: Android `MediaSession` controls and lockscreen notifications.
- **`PlayerBackgroundPlaybackController`**: Background service binding and audio-only playback.
- **`PlayerPlaybackStateController`**: Saving and restoring playback position in the database.
- **`PlayerPlaylistLoader`**: Folder scanning, M3U file parsing, and stream switching.
- **`PlayerPipController`**: Picture-in-Picture mode transitions and overlay hiding.
- **`PlayerIntentHandler`**: Launch intents, file/stream URIs, subtitle extras, and activity results.

### ViewModel Managers (`ui/player/managers/`)
These classes manage player state and background tasks:

- **`PlaybackManager`**: Seeking, playback speed, and frame stepping.
- **`PlaylistManager`**: Playlist items, queue navigation, and repeat modes.
- **`TrackManager`**: Audio and subtitle track selection and external audio files.
- **`SubtitleManager`**: Online and local subtitle fetching.
- **`AmbientModeManager`**: Ambient lighting effects and custom shaders.
- **`CustomButtonManager`**: User-configured player buttons.
- **`PlayerSnapshotManager`**: Taking screenshots and saving them to device storage.
- **`PlayerGestureManager`**: Double-tap to seek, swipe gestures, and tap actions.

### Event Handling (`ui/player/observers/`)
- **`MpvEventDispatcher`**: Centralizes MPV property and event callbacks to keep player listeners clean.

## Result

- `PlayerActivity.kt` went from ~3,900 lines down to ~2,600 lines.
- `PlayerViewModel.kt` went from ~2,200 lines down to ~1,990 lines.
- Around 1,500 lines of code were moved into small, focused files that are much easier to understand and maintain.
