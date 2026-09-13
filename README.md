# Ginera TV Remote for Android

A native Android companion for LG webOS and Android-based televisions.

## Phase 1

- LG webOS pairing over local Wi-Fi
- Power off and Wake-on-LAN
- Volume, mute, channel controls and number pad
- Direction pad, pointer touchpad and keyboard text entry
- HDMI, Netflix and YouTube shortcuts
- TV address, MAC address and LG pairing key stored locally on the phone
- Haier network-readiness panel

No private household network identifiers are stored in this public repository.

## Download the APK

GitHub Actions builds an installable debug APK after every push:

1. Open the repository's **Actions** tab.
2. Select the latest successful **Build Android APK** run.
3. Download the **Ginera-TV-Remote-debug** artifact.
4. Extract it and install `app-debug.apk` on the Android phone.

Android may ask you to allow installation from the browser or file manager used to open the APK.

## First LG connection

1. Put the phone and LG television on the same Wi-Fi.
2. Enter the LG IP address and MAC address in the app.
3. Tap **Pair / Connect LG**.
4. Accept the pairing prompt displayed by the television.

The pairing key is retained only in the app's private on-device storage.
