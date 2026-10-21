# SpotAmp

SpotAmp is an Android music player for browsing and listening to a personal music library through the SpotCore companion server. It saves music details on your phone so you can browse your library when SpotCore is unavailable. Download tracks to listen offline; saved library details are not audio files.

## Getting started

1. Start SpotCore on your computer and open its **Add Device** pairing screen.
2. Install and open SpotAmp on your Android phone.
3. Choose **Scan pairing QR code** on Home.
4. Allow camera access when prompted so SpotAmp can read the QR code. QR decoding takes place on your phone.
5. Review the SpotCore server address and device name, then confirm pairing.
6. SpotAmp connects to the SpotCore library and starts saving its music details on this phone. You can continue in the background and browse or play music as the catalog becomes available.

## Troubleshooting

- **Camera access:** Choose **Continue to camera** when prompted. If you denied access and Android no longer shows the prompt, choose **Open app settings**, allow Camera for SpotAmp, then return to pairing. You can also cancel and pair later.
- **QR code won’t scan or is no longer valid:** Keep SpotCore’s **Add Device** code inside the camera frame. If the code is invalid or expired, create a fresh one in SpotCore and scan it again.
- **SpotCore is unavailable:** Confirm the server is running and reachable from the phone. In SpotAmp, open **Settings → Connection** and choose **Check connection**. When connecting from outside the home network, use SpotCore’s HTTPS address; an HTTP address works only on the home network.
- **Listening offline:** Saved library details are not audio. Download tracks for offline listening. Saved details and completed downloads remain on the phone while SpotCore is unavailable.

## What’s in the app

- Browse and search the active music library.
- Play tracks, albums, artists, and playlists with queue, shuffle, repeat, and seek controls.
- Browse saved library details and manage downloaded music for offline listening.
- Review library metadata suggestions and configure optional discovery, appearance, audio, and listening-history preferences.

Casting is not currently supported. Pairing, playback, background behavior, Bluetooth, and lock-screen controls should be validated on a physical device before release.

## Development

Open this directory in Android Studio and allow Gradle sync to finish. Local build secrets belong in `.env`; see `.env.example` for the expected shape. Never commit `.env`, signing credentials, or production tokens.

For an installable QA variant, run:

```powershell
.\scripts\qa.ps1 qa
```

For available QA tiers and their evidence, see [QA.md](docs/QA.md). For release signing and artifact checks, see [BUILD_SIGNING.md](docs/BUILD_SIGNING.md). Production release signing is enabled only when explicit signing credentials are provided; otherwise the release artifact is unsigned for local validation.

The app currently retains a legacy Android application ID. Do not change the application ID or versioning as part of ordinary development without an explicit release/update migration decision.
