# ADB 5555

An Android app that does two things a computer usually has to do, from the phone itself:

- **Turn on ADB over port 5555** (like `adb tcpip 5555`), so you can connect with
  `adb connect <phone-ip>:5555`. One tap in the app or on its Quick Settings tile, after each reboot.
- **Grant SpeedEQ its per-app permission** — one tap on *Grant SpeedEQ access*. SpeedEQ uses it to
  find which app is playing so its EQ follows just your music. The app only ever runs that single
  fixed command for SpeedEQ; it never runs a command handed to it by another app.

It works by pairing with Android's Wireless debugging once, the same way a computer pairs.

## Install

Download the `.apk` from the [latest release](https://github.com/JeremyDoege89/Adb5555/releases/latest)
and open it. Android will ask you to allow installs from your browser; that's expected for an app
from outside the Play Store. Android 11 or newer.

## One-time setup

1. **Developer options:** Settings › About phone › tap *Build number* seven times.
2. In Developer options, turn on **Wireless debugging** (needs Wi-Fi).
3. In ADB 5555, tap **Pair**. In Wireless debugging, tap *Pair device with pairing code*, leave that
   dialog open, pull down the notification shade and type the 6-digit code into ADB 5555's
   notification.

Then use **Enable 5555** or **Grant SpeedEQ access**. The SpeedEQ grant survives reboots, so you only
need it once and can uninstall ADB 5555 afterwards if you like.
