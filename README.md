# Aditya Music — Android 16 only

A clean, offline-first music player project targeting **Android 16 / API 36 only**.

This is an independent implementation using Android 16 platform APIs and AndroidX Media3. It is not a drop-in rebuild of Google's legacy AOSP `packages/apps/Music` module. That upstream module is marked as no longer actively supported and its source is provided as a reference; its present source tree is not a complete modern end-user music app.

## Included features

- Local audio library scan through Android `MediaStore` with the Android 16 `READ_MEDIA_AUDIO` permission.
- Songs, albums, artists, folders, favorites, recently played, and user-created playlists with saved track order and per-playlist track removal.
- Search across track title, artist, album, and folder path.
- Album artwork and track metadata where the device's media database provides them.
- Playback queue with track selection, removal, and reordering; play all; shuffle; repeat off / all / one; previous / next; seeking.
- Background playback through `MediaSessionService` and Media3 ExoPlayer.
- System media notification with tap-to-open player, lock-screen controls, headset/Bluetooth media-key integration, audio focus handling, local-playback wake lock while actively playing, and pause-on-headphone-disconnect behavior.
- Playback speed presets from 0.75× through 2.0×.
- Sleep timer that pauses playback from the service, so it remains active while the player screen is closed.
- System equalizer / sound-effects panel when the Android device provides a compatible panel.
- Android audio picker integration (`ACTION_GET_CONTENT`), share audio URI, and inspect track details.
- No account, cloud, streaming, ads, or internet permission.

## Android / build configuration

| Setting | Value |
|---|---|
| Minimum SDK | 36 (Android 16) |
| Target SDK | 36 (Android 16) |
| Compile SDK | 36 |
| Java | 17 |
| Kotlin | 2.4.21 |
| Android Gradle Plugin | 8.13.2 |
| Gradle | 8.13 |
| AndroidX Media3 | 1.11.1 |

Older Android versions are intentionally unsupported. This project does not attempt Android 10 compatibility.

## Build with GitHub Actions

1. Create a new repository named `Music` and use `main` as its default branch.
2. Add the provided `extract-project.yml` file to `.github/workflows/extract-project.yml` in the empty repository.
3. Upload `AdityaMusic-Android16.zip` to the repository root.
4. Open **Actions → Extract Android 16 project → Run workflow**. The workflow extracts the project and commits the source files. The extraction workflow explicitly starts `build.yml` after pushing the source.
5. Open **Actions → Build Aditya Music (Android 16)**. Download the `AdityaMusic-Android16-APK` artifact after the run succeeds.

For the extraction workflow to push its extraction commit, set **Settings → Actions → General → Workflow permissions → Read and write permissions** in the repository.

## Build locally

Open the project in a recent Android Studio release with Android 16 SDK Platform 36 installed, then run:

```bash
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

The debug APK is generated at:

`app/build/outputs/apk/debug/app-debug.apk`

## Notes and honest limitations

- Audio format support depends on the Android 16 device's platform decoders and Media3 extractors.
- The equalizer screen is provided by the device/OEM or installed sound-effects app. If none is installed, the app reports that rather than pretending to apply an EQ.
- Media access is limited to audio exposed by Android `MediaStore`; files hidden from the media database may need to be indexed by the system before they appear.
- This delivery has source-level and configuration checks, but it has **not** been installed and exercised on a physical Android 16 device in this environment. The GitHub Actions build and device playback test are still required before calling the APK release-verified.
