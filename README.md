# Renewly

**An offline Android app for tracking your subscriptions.** Netflix renews on the 15th, ₹649, on your HDFC card ending 1234: Renewly keeps that list for you and reminds you before each charge.

Your data never leaves your phone. The app has **no internet permission**, no accounts, no ads and no tracking.

## Download

Get the latest `.apk` from **[Releases](../../releases/latest)**, open it on your phone and allow installing from your browser or file manager when Android asks.

Requires Android 8.0 or newer.

## Features

- **Track every subscription**: name, nickname, fee, currency (₹, €, $, £), billing cycle and first payment date
- **Next renewal, worked out for you**: weekly, monthly, every 3 or 6 months, or yearly. Month ends are handled correctly (31 Jan → 28 Feb → 31 Mar)
- **Payment card**: a card nickname and its last 4 digits. Full card numbers are never stored
- **Reminders**: a notification on the day, or 1, 3 or 7 days before each renewal, checked every morning on the device
- **Monthly total** across all active subscriptions, per currency
- **Pause** a subscription without deleting it
- **Backup**: export to a JSON file and import it on a new phone, with no cloud involved
- Light and dark themes, with Material You colours on Android 12+

## Privacy

| | |
|---|---|
| Internet | None. The build fails if the app ever requests the internet permission |
| Storage | A local database on your phone only |
| Cloud backup | Disabled, so your data isn't copied to Google Drive |
| Card data | Only a nickname and the last 4 digits |

## Built with

Kotlin · Jetpack Compose · Material 3 · Room · WorkManager

Every push is tested and built by GitHub Actions. Tagged versions (`v1.0.0`, …) are published automatically to Releases.

## Build it yourself

Requires JDK 17 and the Android SDK.

```bash
./gradlew testDebugUnitTest assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.

## Project structure

```
app/src/main/java/com/rachitratnammudaliar/renewly/
  domain/     renewal date maths and money formatting (unit tested)
  data/       the Room database
  reminders/  daily check and notifications
  backup/     JSON export and import
  ui/         Compose screens: list, add/edit, settings
```

## Licence

MIT © 2026 Rachit Ratnam Mudaliar
