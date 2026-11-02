# Duedial

**An offline subscription tracker for Android and iPhone that reminds you before you're charged.**

Netflix renews on the 15th, ₹649, on your HDFC card ending 4821. Duedial keeps that list, works out every next payment for you, and shows what you spend each month. Your data never leaves your device: no internet, no account, no ads and no tracking.

<p>
  <img src="docs/screenshots/home.png" width="230" alt="Home screen with monthly total and upcoming payments">
  <img src="docs/screenshots/detail.png" width="230" alt="Subscription details with next and last payment">
  <img src="docs/screenshots/edit.png" width="230" alt="Adding a subscription">
  <img src="docs/screenshots/home-dark.png" width="230" alt="Home screen in dark mode">
</p>

## Install

Download from **[Releases](../../releases/latest)**. Duedial isn't in any app store yet, so your phone asks you to confirm the first time.

| Device | File | Requires |
|---|---|---|
| Android | `Duedial-x.y.z-Android.apk` | Android 8.0+ |
| iPhone, iPad | `Duedial-x.y.z-iOS.ipa` | iOS 16+, installed with a sideloading tool |

<details>
<summary><b>Android</b></summary>

1. Download the APK on your phone and open it. If Android asks, allow your browser or Files app to **install unknown apps**.
2. If Play Protect warns about an unknown developer, tap **More details → Install anyway**.
3. **Samsung Galaxy (One UI 6+):** if the install is blocked, turn off **Settings → Security and privacy → Auto Blocker**, install, then turn it back on.

Updates install over the old version and keep your data.
</details>

<details>
<summary><b>iPhone and iPad</b></summary>

Apple only allows apps from the App Store or apps signed with an Apple ID, so the `.ipa` is installed with a free sideloading tool:

1. On a Windows PC or Mac, install **[Sideloadly](https://sideloadly.io)** or **[AltStore](https://altstore.io)**.
2. Connect your iPhone, drag in `Duedial-x.y.z-iOS.ipa`, and sign in with your Apple ID.
3. On the iPhone, open **Settings → General → VPN & Device Management**, tap your Apple ID and choose **Trust**. On iOS 16+, also turn on **Settings → Privacy & Security → Developer Mode**.

With a free Apple ID the app must be refreshed every 7 days (AltStore does this automatically). Your data stays on the phone when you refresh.
</details>

**Moving between phones:** Android and iPhone read the same backup file. Use **Settings → Export** on one phone and **Settings → Import** on the other.

**Coming from Renewly?** Duedial is the same app under a new name. In Renewly, open **Settings → Export**, then in Duedial open **Settings → Import** and choose that file.

## Features

**Every kind of plan**
| Type | For | How it's counted |
|---|---|---|
| **Subscription** | Anything paid on a schedule | Next payment, last payment, paid so far |
| **Free trial** | Trials that turn into paid plans | Free until the trial ends; a reminder tells you the last day to cancel |
| **Free** | Student offers, free tiers, gifts | Never charged; optional "free until" date with a reminder and the normal price you're saving |
| **Lifetime** | One-time purchases | Paid once, never renews, never in the monthly bill |

**Subscriptions**
- Name, nickname, fee and currency (₹, €, $, £)
- Any billing period: weekly, monthly, every 3 or 6 months, yearly, or custom (every 10 days, every 2 years, …)
- **Next payment**, **last payment** and **paid so far**, all worked out from the first payment date
- Month ends handled correctly: 31 Jan → 28 Feb → 31 Mar
- **Cancelled but still active**: set an end date and Duedial stops counting payments after it
- **Shared plans**: split the cost between 2 to 6 people and every total uses your share
- **Discounts**: add the regular price to see how much you save
- Payment method: a card nickname and its last 4 digits. Full card numbers are never stored
- Categories, notes, and pause without deleting

**Icons**
- Known services (Netflix, Spotify, YouTube, Prime, Hotstar, ChatGPT, iCloud, Jio, Airtel and about 40 more) get their brand colour automatically
- Use any image from your gallery as a subscription's icon

**Totals**
| Option | What it adds up |
|---|---|
| **Monthly bill** (default) | Every subscription you count, as a monthly amount. Longer plans are divided: ₹1,499 a year adds ₹125 a month |
| **Due this month** | What's actually charged in the current calendar month, from subscriptions you count |

*Count in monthly bill* is on by default for every paid subscription. Switch it off on a subscription's page to leave it out of both totals; the list then marks it *not counted*. Trials join the bill only once they become paid. Free and lifetime plans are never in it. Different currencies are shown separately, never added together.

**Reminders**
- Before a payment: on the day, or 1, 2, 3 or 7 days before, or off
- Before a free trial ends, with the last day to cancel
- Before a free offer ends
- Android checks every morning; iPhone schedules them ahead, so they arrive even when the app is closed

**Backup**
- Export to a JSON file you keep, and import it on a new phone, Android or iPhone. No cloud involved

## Reminders on Samsung phones

Samsung's battery saving can delay background work. In Duedial, open **Settings → Open app settings → Battery → Unrestricted**, and make sure Duedial isn't listed under **Sleeping apps** (Settings → Battery → Background usage limits).

## Privacy

| | |
|---|---|
| Internet | None. The Android build fails if the app ever asks for internet access |
| Storage | Only on your phone: a private database on Android, a file in the app's private storage on iPhone |
| Cloud backup | Disabled on Android, so your data isn't copied to Google Drive |
| Card data | Only a nickname and the last 4 digits |

See [PRIVACY.md](PRIVACY.md).

## How it's built

| Platform | Built with |
|---|---|
| Android | Kotlin, Jetpack Compose, Material 3, Room, WorkManager |
| iPhone, iPad | Swift and SwiftUI, a port of the same logic with the same unit tests |

| | |
|---|---|
| Tests | Unit tests cover payment dates, month ends, trials, free and lifetime plans, totals, money parsing, brand matching and backups. They run for Android and on an iPhone simulator for every change |
| Releases | Raising the number in `version.txt` builds every version on GitHub Actions and publishes them together |
| Screenshots | Taken by a robot: GitHub Actions boots an Android emulator, opens the app with sample data and saves these images |

```
app/        Android app
ios/        iPhone and iPad app (XcodeGen project)
```

### Build it yourself

```bash
./gradlew :app:assembleDebug            # Android (JDK 17 and Android SDK)
cd ios && xcodegen generate && open Duedial.xcodeproj   # iOS (macOS with Xcode 15+)
```

## Licence

MIT © 2026 Rachit Ratnam Mudaliar
