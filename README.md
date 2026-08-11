# Rofiant (Android)

Native Android client for Rofiant, built with Jetpack Compose.

## Development

Requires JDK 17.

```sh
./gradlew assembleDebug   # build app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug    # build + install to a connected device/emulator
```

## Builds & releases

Every push to `master` runs [`.github/workflows/build.yml`](.github/workflows/build.yml),
which builds a debug APK and publishes it as a GitHub Release (tagged
`build-<run number>`). All builds are signed with the checked-in
`debug.keystore` — not a secret, kept consistent on purpose so each new
build installs as an update over the last rather than requiring an
uninstall (Android refuses to install a differently-signed "update" over
an existing app).

### Installing via Obtainium

[Obtainium](https://github.com/ImranR98/Obtainium) tracks this repo's
GitHub Releases and prompts you to update whenever a new one is published.

1. Install Obtainium.
2. Tap **Add App**.
3. Paste `https://github.com/RofiantAI/RofiantMobile` as the source URL.
4. Confirm — Obtainium finds the `app-debug.apk` asset on the latest
   release automatically.
5. Install. Future pushes to `master` show up as an available update.

## License

All rights reserved — see [LICENSE](LICENSE).
