# Cloudborne

Cloudborne is an independent Android network client built with Kotlin, Jetpack Compose, SaltUI, and in-process sing-box libbox.

## Build

Requirements:

- JDK 21
- Android SDK platform 36
- Android build-tools 36.1.0
- Mill 1.1.x

Build the JVM tests:

```bash
~/bin/mill app.test
```

Build the Android APK:

```bash
~/bin/mill app.androidApk
```

Build the instrumented test APK:

```bash
~/bin/mill app.androidTest.androidTestApk
```

The debug APK is written to:

```text
out/app/androidApk.dest/app.apk
```

## Project structure

- `app/src/main`: Android application and runtime integration
- `app/src/test`: JVM parser and configuration tests
- `app/src/androidTest`: Room and Compose semantics tests
- `vendor/sing-box/libbox.aar`: pinned arm64 libbox binding
- `build.mill`: Mill Android build definition

## License

See the project license files and third-party notices under `vendor/`.
