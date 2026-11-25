# AX EMOTE SERVER — Android Native APK

Package: `com.axemote.app`  
Website: https://ax-emote-ultra.onrender.com/

## Included
- Kotlin Android app with immersive WebView (JavaScript, DOM storage and cookies enabled).
- Single WebView instance moved between the Activity and the floating overlay window to preserve the live page and in-memory state as far as Android permits.
- User-granted `SYSTEM_ALERT_WINDOW` permission, draggable official-logo floating icon, close-only overlay page, ON/OFF control in the main app.
- Foreground service and `onTaskRemoved()` cleanup; `stopWithTask=true`.
- GitHub Actions workflow for debug and release APK builds.

## Build
Open this folder in Android Studio (JDK 17, Android SDK 35), then run `assembleDebug`.

## GitHub Actions signing
Add repository secrets:
- `AX_KEYSTORE_BASE64` — base64-encoded release keystore
- `AX_STORE_PASSWORD`
- `AX_KEY_ALIAS`
- `AX_KEY_PASSWORD`

The workflow decodes the keystore and builds the release APK. Without signing secrets, it builds a debug APK only.

## Important device testing
Android overlay and foreground-service restrictions vary by manufacturer and Android version. Test on the target device, especially Android 13–15. The Android 14+ `specialUse` foreground-service declaration may require Play Console review if publishing on Google Play.

The existing Render website/backend is not included or modified.
