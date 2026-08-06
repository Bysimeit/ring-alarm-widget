<div align="center">

# Ring Alarm Widget

**Arm and disarm your Ring alarm from the Android home screen. No server, no account, nothing of yours leaves the phone.**

*The one button the official app never shipped.*

[![License](https://img.shields.io/badge/license-AGPL--3.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-3ddc84.svg)]()
[![Built with](https://img.shields.io/badge/built%20with-Kotlin-7f52ff.svg)]()
[![Release](https://img.shields.io/badge/release-v1.0.1-3ddc84.svg)](https://github.com/Bysimeit/ring-alarm-widget/releases/latest)

</div>

---

> **Not affiliated with Ring LLC or Amazon.** This is an independent project. It
> uses the same private API the official apps use, which is not documented and
> not endorsed by anyone. The name says what the app does, nothing more. If you
> want an officially supported product, this is not it.

## Why this exists

The official Ring app ships a widget for Live View. It ships a widget for Ring
Unlock. It does not ship one for the thing you actually do twice a day: arming
and disarming the alarm.

So the routine is: unlock the phone, find the app, wait for it to load, reach the
dashboard, tap the mode. Every morning, every evening. People have been asking
for the widget on Ring's own community forum since 2023, and it has never
arrived.

The infrastructure clearly exists on their side. The feature just was never
considered worth building.

## Install

Download the APK from [the latest release](https://github.com/Bysimeit/ring-alarm-widget/releases/latest),
then add the widget from your home screen: long press, **Widgets**, look for
**Ring Alarm Widget**. Two sizes are offered, three cells by two for the controls
and one cell for the status tile.

For updates, [Obtainium](https://github.com/ImranR98/Obtainium) can follow the
GitHub releases directly. Every release ships with a SHA-256 checksum so you can
verify what you downloaded.

It will not be on the Play Store, and the reason is in
[Honest limitations](#honest-limitations).

## What the widget does

Three buttons: Disarmed, Home, Away. Tap one, the mode changes. That is the whole
feature, and it is the one the official app never shipped.

It comes in two shapes.

**Three cells by two** carries the current state, the three modes and a refresh
button. Shrink it to a single row and the header steps aside, leaving three
buttons that still say which mode is active by their colour.

**One cell** is a status tile. The colour tells you the mode across the room, and
a tap opens the app. It deliberately cannot change anything: a one-tap disarm on
a square the size of an icon is too easy to hit by accident, and arming by
accident is no better once the exit delay runs out.

Arming in Away starts that exit delay, and both widgets show a live countdown.
Android animates it in the launcher, so the phone is not woken sixty times a
minute to redraw a number.

The colours do the work so you do not have to read: grey disarmed, amber home,
violet away. Red is reserved for errors and never names a mode, because on an
alarm panel red means "triggered".

App and widget each have their own light, dark or system setting. They are
separate on purpose: the app owns the whole screen, the widget sits on your
wallpaper, and the right answer is not always the same for both.

## How it is meant to work

Two constraints shape the whole design.

### 1. There is no server, and there never will be

Everything happens on the phone. No backend of mine sits between you and Ring,
because a backend would be a place where your alarm credentials could pool up.
There is nothing to breach, nothing to subpoena, and nothing for me to shut down
if I lose interest.

This is verifiable rather than promised: read the network permissions, read the
code, watch the traffic.

### 2. Your password is never stored

You type it once, at sign-in. It goes to Ring's OAuth endpoint and is discarded
from memory afterwards. What gets kept is a refresh token, encrypted on the
device, which grants exactly the ability to change alarm modes and nothing else.

If that token leaks, you revoke the session from your Ring account. Your password
was never in the picture.

## Honest limitations

This is an independent project that talks to an undocumented API. The risks are
real and worth stating before you trust it with an alarm system.

- **The API is private and can break without notice.** Amazon publishes no public
  API for Ring, and the "Works with Ring" programme is closed. Everything here
  rests on the protocol the official apps use. A single change on their side can
  break this app overnight, and there is no mitigation for that. Similar
  community projects have survived for years, but survival is not a guarantee.
- **It will never be on the Play Store.** A third-party app using a private API
  and naming another company's product does not pass review. Distribution is
  GitHub Releases, with Obtainium or F-Droid for updates.
- **One tap disarms the alarm.** Changing the mode does not require the PIN, so
  anyone holding your phone can disarm from the home screen. Optional biometric
  confirmation before disarming now exists, off by default, and the 1×1 tile
  cannot change the mode at all.
- **The widget can show a stale mode.** It reads the panel every fifteen minutes
  in the background, and every minute while the app is on screen. Change the mode
  at the physical keypad and the widget will not notice straight away. Holding a
  live connection is not an option today: Ring closes its websocket after about a
  minute, measured and reproducible.
- **Automated access may sit awkwardly with Ring's terms of service.** Personal
  use, no monetisation, no data collection, but you should know it rather than
  find out later.
- **Not audited by anyone.** If the security of your home depends on getting this
  exactly right, use the official app.

## Trusting an app that asks for your alarm credentials

You should not, on the strength of a README. So here is what to check instead.

The password path is short enough to audit by hand: it goes from the sign-in
screen to `oauth.ring.com`, and is never written anywhere. There is no analytics
SDK, no crash reporter, no third-party telemetry. The only host the app ever
contacts belongs to Ring.

Releases are built by [a public GitHub Actions workflow](.github/workflows/android-release.yml)
from the commit it names, not on a laptop. Each one ships with its SHA-256, so
you can check that the APK you downloaded is the one that workflow produced.

Found a problem? Open an issue for anything ordinary. For an actual
vulnerability, please report it privately first.

## License

[AGPL-3.0](LICENSE)

Ring is a trademark of Ring LLC, an Amazon company. This project is not
affiliated with, endorsed by, or supported by either. The trademark is used only
to describe what the software interoperates with.
