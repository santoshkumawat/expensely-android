# Expensely — Android

The Android app for **Expensely**, a personal expense and income tracker. It is a thin native shell: one activity with a full-screen `WebView` that loads the deployed Expensely web app, so every feature of the web app is available on the phone as an installable app.

| Part | Repo | What it is |
|---|---|---|
| Frontend | [expensely-frontend](https://github.com/santoshkumawat/expensely-frontend) | Next.js web app, deployed on Netlify — [expensely-app.netlify.app](https://expensely-app.netlify.app) |
| Backend | [expensely-backend](https://github.com/santoshkumawat/expensely-backend) | Spring Boot REST API + MongoDB, deployed on Render |
| **Android (this repo)** | [expensely-android](https://github.com/santoshkumawat/expensely-android) | WebView shell that loads the web app |

```
Android app (WebView)  ──▶  Next.js web app (Netlify)  ──▶  REST API /api (Render)  ──▶  MongoDB
```

There is no networking, storage or business logic in this repo. Sign-in, data and every screen come from the web app, exactly as in a desktop browser (the login token lives in the WebView's local storage).

---

## Contents

1. [Expensely features](#expensely-features) — what the product does, and which repo builds each piece
2. [Set up and run](#set-up-and-run)
3. [How the Android app connects to the other projects](#how-the-android-app-connects-to-the-other-projects)
4. [What the Android app itself does](#what-the-android-app-itself-does)
5. [Known limits and next steps](#known-limits-and-next-steps)
6. [Project structure](#project-structure)

---

## Expensely features

On Android you get all of these through the web app.

| Area | Feature | What you get | Android (this repo) | Frontend |
|---|---|---|---|---|
| **Accounts** | Sign up / sign in | Email-OTP registration, JWT login, forgot / reset password | shows the web app | `login`, `register`, `AuthContext` |
| | Profile | Edit name and username, change email and password | shows the web app | `profile` page |
| | Demo account | A read-only tour | shows the web app | "Try demo" |
| **Money** | Expenses & income | Add / edit / delete; category decides expense or income | shows the web app | `ExpenseModal`, `ExpenseList` |
| | Recurring records | One gen date, due date, amount → 12 months | shows the web app | `ExpenseModal` |
| | Payment status & paid date | UPCOMING → PENDING → PAID, "Paid on …" | shows the web app | `ExpenseList` |
| | All-time search | Whole words or word starts, across every year | shows the web app | Expenses search |
| **Overview** | Home & Quick Stats | Balance, Your Money cards with sparklines, Quick Stats, Due Soon | shows the web app | `Dashboard` |
| | Hide amounts | An eye button hides every rupee amount | shows the web app | `AmountsToggle` |
| | Report | Month / year breakdown, income vs expenses | shows the web app | `AnalyticsView` |
| | Excel export | Monthly / yearly `.xlsx` | saved to Downloads through the file bridge | export button |
| **Reminders** | Payment reminder emails | Due-date emails; mark the payment paid in the app | shows the web app | profile toggle |
| **Personal** | Themes | 26 themes × light / dark, saved per account | shows the web app | `ThemeContext` |
| **Privacy** | Encryption at rest | Description and amount encrypted before storage | — | — |
| **Admin** | Admin panel & site analytics | Stats, categories, users, anonymous analytics | shows the web app | `admin` page |
| **Mobile** | **The Android app** | Installable app, full-screen, back button, skips the landing page | **this repo** | `isAndroidApp()` |

---

## Set up and run

### Prerequisites

- **Android Studio** (current stable) with an Android SDK — `compileSdk` 36
- JDK 11 or newer for the Gradle build (Android Studio bundles one)
- A device or emulator on **API 24+** with internet access

### Run

1. Open this folder in Android Studio and let Gradle sync (it creates `local.properties` with your SDK path; that file is git-ignored).
2. Run the `app` module on a device or emulator.

From the command line:

```bash
./gradlew assembleDebug     # debug APK → app/build/outputs/apk/debug/
./gradlew assembleRelease   # release build (see signing below)
```

The app opens **https://expensely-app.netlify.app**, so it works as soon as it launches — no backend, MongoDB or environment variables are needed on your machine.

### Release builds

A release APK must be signed. Use Android Studio → **Build → Generate Signed App Bundle / APK** with your release keystore. Keep the keystore and its passwords **outside the repository** and never commit them. The built APK (`app/release/expensely-v2.2.apk`) is git-ignored.

Current release: **v2.2** (`versionCode` 4, `versionName` 2.2, application id `com.expensely.app`).

### Testing against local servers (optional)

To try a local frontend and backend inside the emulator:

1. Run the backend ([backend README](https://github.com/santoshkumawat/expensely-backend#readme)) and the frontend ([frontend README](https://github.com/santoshkumawat/expensely-frontend#readme)) on your computer. In the emulator your computer is `10.0.2.2`, not `localhost`.
2. Frontend: set `NEXT_PUBLIC_API_URL=http://10.0.2.2:8080/api` in `.env.local` and restart `npm run dev`.
3. Backend: add `http://10.0.2.2:3000` to the CORS allow-list in `config/SecurityConfig.java`.
4. Android: change `START_URL` in `MainActivity` to `http://10.0.2.2:3000` (the app only opens pages from that address) and allow plain HTTP for debug builds (`android:usesCleartextTraffic="true"` on `<application>`). Don't ship either change.

---

## How the Android app connects to the other projects

- **Frontend.** `MainActivity` loads `START_URL` (`https://expensely-app.netlify.app`) and appends `ExpenselyApp/2.2` to the WebView's user agent. The frontend's `src/lib/platform.js` (`isAndroidApp()`) reads that to skip the landing page and go straight to login inside the app.
- **Backend.** No direct connection. The web app inside the WebView calls the REST API the same way it does in a browser.
- **Updates.** Because the UI is the deployed web app, every frontend and backend release reaches Android users immediately, without a new APK. A new APK is only needed when this shell itself changes.

---

## What the Android app itself does

Everything the shell adds is in `MainActivity`:

- Full-screen `WebView` with JavaScript and DOM storage enabled (needed for the login token) and wide-viewport rendering.
- The device **back button** goes back through the WebView's own history, and closes the app when there is nowhere left to go.
- The `ExpenselyApp/2.2` user-agent marker described above.
- **Only the Expensely site loads inside the app.** Links to any other address open in the phone's browser, so no outside page can run inside the WebView or reach the file bridge below.
- A **file bridge** (`window.ExpenselyAndroid.saveFile(name, mimeType, base64)`): the web app's Excel export hands the file here, because a WebView ignores download links. On Android 10+ it is saved to the Downloads folder (no permission needed); on Android 9 and older it opens the share sheet through a `FileProvider`.
- An **offline page**: when the site can't load, a bundled `offline.html` (in `assets/`) replaces the system "Web page not available" error.
- **In-app updates** (`UpdateChecker`): on launch the app reads `https://expensely-app.netlify.app/app-version.json` from the frontend. If its `versionCode` is higher than the installed one, an **Update** dialog downloads the APK inside the app and opens the system installer. The APK URL must be `https://github.com/santoshkumawat/expensely-android/releases/download/...`; anything else is ignored. The first time, Android asks you to allow installs from Expensely. To ship a release, publish the APK on GitHub, then raise `versionCode`, `versionName`, `apkUrl` and `notes` in the frontend's `public/app-version.json`.
- App icon (adaptive, with a monochrome variant), `Material3` day / night theme without an action bar, and the `INTERNET` and `REQUEST_INSTALL_PACKAGES` permissions.

---

## Known limits and next steps

- **Notifications.** Payment reminders are sent by email; a WebView cannot receive native push notifications.
- **Hardcoded URL.** Move the address into a build-config value so debug builds can point at a local frontend without editing code.
- **Native screens.** If WebView performance or offline support ever matters, the busiest screens (Home, Expenses) are the candidates for native rewrites.

---

## Project structure

```
app/src/main/
  java/com/expensely/app/MainActivity.java   the whole app: WebView setup, back handling
  res/layout/activity_main.xml               a single WebView
  res/mipmap-*/ res/values*/ res/xml/         icons, themes (day / night), backup rules
  AndroidManifest.xml                        INTERNET permission, launcher activity
app/build.gradle.kts                         version, SDK levels, dependencies
gradle/libs.versions.toml                    dependency versions
```

Stack: Java, `android.webkit.WebView`, AndroidX AppCompat / Activity and Material Components, Gradle (Kotlin DSL), `minSdk` 24, `targetSdk` 36.
