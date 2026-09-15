# HARM

**Home Assistant Remote Media** turns an older Android tablet into a local media display controlled by Home Assistant.

[Website](https://harm-app.github.io/) · [Download the latest APK](https://github.com/harm-app/harm/releases/latest)

## Features

- Black, neutral screen while idle
- Native Intelbras camera RTSP playback through LibVLC over TCP, without displaying the Home Assistant UI
- Automatic reconnection when a stream stops progressing
- Media playback from HTTP, HTTPS, or RTSP URLs
- NVR login and automatic discovery of available cameras
- Manual preview of selected cameras
- Remote wake, brightness control, playback stop, and screen-off commands
- Automatic startup after the tablet reboots
- Token-protected local HTTP API

Compatible with Android 6.0 or newer. Initially tested on a Galaxy Tab A SM-P555M running Android 7.1.1.

## Installation

1. Download the APK from the [latest GitHub release](https://github.com/harm-app/harm/releases/latest).
2. On the Android device, allow your browser or file manager to install unknown apps.
3. Open the downloaded APK and complete the installation.
4. Open HARM and configure the Home Assistant endpoint and camera source on the device.

Keep HARM's control port available only on your trusted local network. Do not publish camera credentials, the device token, or port `8765` on the internet. See the [security policy](SECURITY.md) for details.

## Local API

The endpoint follows the format `http://TABLET_IP:8765`. The control token is shown only in the app's local settings.

```text
GET /status?token=TOKEN
GET /camera?token=TOKEN&channel=1&timeout=30
GET /media?token=TOKEN&url=ENCODED_URL&timeout=30
GET /stop?token=TOKEN
GET /wake?token=TOKEN
GET /brightness?token=TOKEN&value=180
GET /screen/off?token=TOKEN
```

`timeout=0` keeps the media open until `/stop` or `/screen/off` is received. A positive value stops playback automatically after that number of seconds.

## Home Assistant services

- `rest_command.harm_camera`
- `rest_command.harm_media`
- `rest_command.harm_stop`
- `rest_command.harm_wake`
- `rest_command.harm_brightness`
- `rest_command.harm_screen_off`

Example automation action:

```yaml
action: rest_command.harm_camera
data:
  channel: 1
  timeout: 30
```

## Build

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/HARM-0.4.4-debug.apk`

## License

HARM is released under the [MIT License](LICENSE). LibVLC and other bundled dependencies remain subject to their respective licenses; see [third-party notices](THIRD_PARTY_NOTICES.md).
