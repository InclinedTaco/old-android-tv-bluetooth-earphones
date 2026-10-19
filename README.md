# Old Android TV Bluetooth Helper for Headphones

A small, remote-friendly Android TV app for discovering, pairing and connecting Bluetooth headphones on older TVs whose normal accessory screen is unhelpful.

The app is called **Bluetooth Audio** on the TV. It uses the TV's existing Bluetooth audio stack. It does not root the TV, modify firmware, install a driver or add hardware capabilities that are missing.

## Features

- Show paired devices and their current audio connection state.
- Scan without restricting discovery to a particular headphone brand.
- Select a device with the TV remote and start Android's normal pairing process.
- Request an A2DP audio connection after the selected device is paired.
- Reconnect already-paired headphones on demand.
- Play a short, low-level test beep after an audio device is connected.
- Launch from the Android TV app list and navigate with a directional pad.

Discovery can also show computers, phones and other Bluetooth accessories. Choose your headphones; appearing in the list does not mean a device can receive TV audio.

## Compatibility

This is an experimental helper for older firmware that already exposes an A2DP **source** service: the part that sends TV audio to headphones. It cannot guarantee support for every TV or headphone. A missing, disabled or vendor-blocked audio stack cannot be fixed by this app.

| TV | Android | Headphones | Observed result |
| --- | --- | --- | --- |
| Sony KD-43X7500E | Android 9 / API 28 | CMF Buds 2a | Discovery, system pairing, A2DP connection and Android audio routing succeeded. Connection remained active after stopping the helper. |

The table records software-observed results. Acoustic playback quality, latency, microphone support, reconnection after reboot and other TV/headphone combinations have not been comprehensively tested.

- Minimum installation version: Android 8.0 / API 26.
- Only Android 9 on the listed TV has been verified.
- The build targets API 28 for older firmware.
- A2DP connection uses a non-public Android API through reflection. Newer Android versions and some manufacturers restrict it.
- Standard Bluetooth A2DP headphones are the intended devices. This is not an LE Audio, hearing-aid, microphone or transmitter emulator.
- Sony does not officially list A2DP for the tested model. This workaround is not an official compatibility claim.

## Install

Download [the development APK](dist/old-android-tv-bluetooth-helper.apk). Its SHA-256 is recorded in [SHA256SUMS](dist/SHA256SUMS).

This is a **debug-signed development build**, not a Play Store release. No signing key is included.

Sideload it with a TV file manager, or use Android SDK Platform Tools:

~~~sh
adb connect TV_IP:5555
adb install -r dist/old-android-tv-bluetooth-helper.apk
~~~

For ADB, enable developer options and the appropriate USB/network debugging option, then accept the TV's authorization prompt. Some TVs use another connection workflow; port 5555 is an example.

Open **Bluetooth Audio** from the TV's app list. If the launcher hides sideloaded apps:

~~~sh
adb shell am start -n local.tv.bluetoothhelper/.MainActivity
~~~

Application ID: **local.tv.bluetoothhelper**.

## Pair headphones

1. Turn Bluetooth on in TV Settings.
2. Put the headphones in pairing mode. Disconnect a phone or other device if it reconnects automatically.
3. Open **Bluetooth Audio** and allow its requested location permission.
4. Select **Scan for headphones**.
5. Choose your headphones.
6. Accept **Pair** in the TV's system confirmation. Some headphones have a short confirmation timeout.
7. Wait for **Connected for audio**.
8. Use **Play a quiet test tone**, then try audio in a TV app.

On Android 8/9, Bluetooth discovery requires location permission. The app requests coarse location only for discovery; it does not use GPS or fetch your physical location.

Select an already-paired device to reconnect. You do not need to leave the helper open for an established A2DP connection: Android manages it. Automatic reconnection depends on the TV and headphones and is not guaranteed.

The beep uses the media stream at a low generator level. Actual loudness still depends on TV/headphone volume.

## Troubleshooting

### Headphones do not appear

Confirm that they are discoverable, not merely powered on. Keep them near the TV and disconnect any phone that reconnects automatically. Grant this app location permission; some firmware also requires the system location switch. Scan again after the previous scan finishes. Headphones requiring a proprietary dongle or only LE Audio are outside this helper's scope.

### Pairing times out

Retry and accept the TV's confirmation promptly. Follow the headphone manufacturer's pairing instructions. Resetting headphones is not normally necessary and is not done by this app.

### Paired, but audio does not connect

Select the paired device again. Disconnect it from other devices before retrying. Firmware may block A2DP even while allowing normal Bluetooth pairing.

### Connected, but no sound

Check TV media volume, headphone volume and the app playing audio. Try the beep and another TV app. Successful pairing does not guarantee that every manufacturer's HDMI, tuner or protected playback path routes audio the same way.

### No A2DP source service

The app cannot provide one. A compatible external Bluetooth audio transmitter connected to a TV audio output may be an alternative.

### APK update has an incompatible signature

Android requires matching signing certificates for updates. A debug build from another computer may use a different key. Uninstall the old helper before installing that build. Do not erase the TV's Bluetooth system data.

## Permissions and privacy

| Permission | Purpose |
| --- | --- |
| BLUETOOTH | Read paired devices and use profiles on older Android. |
| BLUETOOTH_ADMIN | Discover devices and request pairing/connection. |
| ACCESS_COARSE_LOCATION | Receive discovery results on Android 8/9. |
| BLUETOOTH_CONNECT / BLUETOOTH_SCAN | Permission gates on Android 12+ if installed there; audio compatibility remains unverified. |

There is no Internet permission, account, advertising SDK, analytics, background service or boot receiver. The app does not change global Bluetooth settings or bypass Android's pairing confirmation.

Device names and connection events are written to local Android logs for diagnostics. The app does not upload them. Avoid publishing an entire logcat dump: other applications' information may be included.

## Build

Requirements:

- JDK 17 recommended; JDK 21 was also used successfully.
- Android SDK Platform 34 and Build Tools 34.0.0.
- Accepted Android SDK licenses.
- ANDROID_HOME pointing to the SDK, or an untracked local.properties with sdk.dir.

The Gradle 8.9 wrapper is included with its distribution checksum. The project uses Android Gradle Plugin 8.5.2, native Java/Android views and no third-party runtime dependencies.

macOS / Linux:

~~~sh
chmod +x gradlew
./gradlew assembleDebug lintDebug
~~~

Windows:

~~~powershell
.\gradlew.bat assembleDebug lintDebug
~~~

Output:

~~~text
app/build/outputs/apk/debug/app-debug.apk
~~~

Install:

~~~sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
~~~

Keep signing keys, local.properties, build output and machine-specific settings out of source control. For maintained releases, create and retain your own private release signing key.

## How it works

1. BluetoothAdapter.startDiscovery and ACTION_FOUND provide discovery results directly.
2. Selecting a device calls BluetoothDevice.createBond. Android handles pairing UI and authentication.
3. The helper obtains an A2DP profile proxy with getProfileProxy.
4. When the selected device becomes bonded, it calls the firmware's BluetoothA2dp.connect method through reflection.
5. Connection broadcasts update the screen. Android owns the audio connection.

The non-public connect method is the compatibility-sensitive part. Failure is shown in the UI. The app does not change hidden-API enforcement, obtain root or alter system packages.

Official references:

- [Android Bluetooth discovery](https://developer.android.com/develop/connectivity/bluetooth/find-bluetooth-devices)
- [BluetoothDevice and createBond](https://developer.android.com/reference/android/bluetooth/BluetoothDevice)
- [Non-SDK interface restrictions](https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces)
- [Sony KD-43X7500E specifications](https://www.sony.co.in/electronics/support/televisions-projectors-lcd-tvs-android-/kd-43x7500e/specifications)

## Validation and contributions

The included build passes assembleDebug and lintDebug. The Play Store target-SDK lint rule is explicitly excluded because this is a sideloaded helper targeting older firmware; other lint errors remain enabled. Android 12+ permissions are checked before Bluetooth operations, but newer firmware's hidden-API restrictions may still prevent audio connection.

For a compatibility report, include the TV model, Android/firmware version, headphone model and whether each of these works:

1. Discovery and system pairing.
2. A2DP connection and audible playback.
3. Audio after leaving the helper.
4. Reconnection after headphones are turned off and on.

Omit MAC addresses, private network addresses and unrelated device names from public reports. One working combination does not establish compatibility with all devices.

## Remove

Uninstall **Bluetooth Audio** in the TV's app settings, or:

~~~sh
adb uninstall local.tv.bluetoothhelper
~~~

The helper does not intentionally erase system-managed pairings when removed. Forget a device through the TV's normal Bluetooth settings.

## License

MIT; see [LICENSE](LICENSE). Not affiliated with Sony, Nothing/CMF, Google or Android.

