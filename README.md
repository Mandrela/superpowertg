## QuietTelegram for Android

This repo is a fork of official [Telegram for Android](https://github.com/DrKLO/Telegram)
repository. It's intention is to make notifications less annoying with minimal changes to the
codebase.

It works just like that: the message sound (and vibration) is played only when there's no
notifications from this particular chat in notification area. If you swipe out the notification,
the sound will be played again the next time a notification arrives.

### Compilation Guide

You will require Android Studio 3.4, Android NDK rev. 20 and Android SDK 8.1

1. Download the source code from https://github.com/SY573M404/QuietTelegram
2. Copy your `release.keystore` into `TMessagesProj/config`
3. Fill out `RELEASE_KEY_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_STORE_PASSWORD` in
   `local.properties` to access your release.keystore
4. Go to https://console.firebase.google.com/, create two android apps with application IDs
   `org.telegram.messenger` and org.telegram.messenger.beta, turn on firebase messaging and download 
   google-services.json, which should be copied to the same folder as `TMessagesProj` (*that's where
   unclear part of the README begins. It clearly says to put the file into the repository root, but
   in the upstream repository it is located **inside** of `TMessagesProj` dir*).
5. Open the project in the Studio (note that it should be opened, NOT imported).
6. Fill out values in `TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java` – there’s
   a link for each of the variables showing where and which data to obtain.
7. Run `gradle :TMessagesProj_App:assembleAfatRelease` to actually build the APK for the Google
   Play version. That should work the same for other versions too, but I haven't really tested it.

Chlen
