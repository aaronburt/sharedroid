# Contributing to ShareDroid

Thanks for helping. Bug reports, fixes and new upload destinations are all welcome.

## Before you start

- For anything bigger than a small fix, open an issue first so we can agree on the approach.
- Security problems go through [SECURITY.md](SECURITY.md), not public issues.
- By contributing you agree that your work is licensed under [GPL-3.0](LICENSE), the same as the rest of the project.

## Building

You need JDK 17 or newer and the Android SDK (Android Studio installs both).

```sh
./gradlew assembleDebug        # build the debug APK
./gradlew test                 # JVM unit tests
./gradlew lint                 # Android lint
```

The `s3` module is plain Kotlin, so its tests run without an emulator or device.

## Making a change

1. Fork the repo and create a branch from `main`.
2. Keep each pull request to one change. Add or update tests for behaviour you change.
3. Run `./gradlew test lint` before pushing.
4. Fill in the pull request template. CI must pass before review.

## Code style

- Kotlin, following the [official Kotlin style](https://kotlinlang.org/docs/coding-conventions.html). `.editorconfig` covers formatting.
- UI is Jetpack Compose. No new XML layouts.
- No proprietary or tracking dependencies (Firebase, Play Services, analytics SDKs). ShareDroid stays F-Droid compatible.
- Never commit credentials. Tests that need S3 use MinIO, not a real account.

## Adding a destination

Each destination implements the `Uploader` interface in its own module and adds its own settings screen. See the S3 module as the reference.
