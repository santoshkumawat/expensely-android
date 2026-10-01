# Expensely — Android

Native Android shell for Expensely, an expense-tracking app. `v2.0` wraps the live web app in a `WebView`, giving it an installable, app-like presence on Android while the web app remains the single source of the UI.

- **Backend:** [expensely-backend](https://github.com/santoshkumawat/expensely-backend)
- **Web (what this app displays):** [expensely-frontend](https://github.com/santoshkumawat/expensely-frontend)

## Status

✅ Released — `v2.0`. A single `MainActivity` hosts a full-screen `WebView` pointed at `https://expensely-app.netlify.app`, with JS and DOM storage enabled, wide-viewport rendering, and the device back button mapped to the WebView's own navigation history (falling back to closing the app). The WebView identifies itself with `ExpenselyApp/2.0` appended to its user agent.

The release APK (`expensely-v2.0.apk`) is built locally under `app/release/` and is git-ignored — build it from source with Android Studio or `./gradlew assembleRelease`.

## Stack

- Java, `android.webkit.WebView`
- AndroidX: AppCompat, Material Components, ConstraintLayout, Activity
- Gradle (Kotlin DSL), `compileSdk` 36, `minSdk` 24, `targetSdk` 36

## Getting started

1. Open the project root in Android Studio and let Gradle sync.
2. Run the `app` module on an emulator or device (API 24+) — it needs network access to reach the live site.

No local config needed: the target URL is hardcoded in `MainActivity`, and all auth/data logic runs the same as it does in a browser, since it's the same web app.

## How it works

```
MainActivity → WebView → https://expensely-app.netlify.app → backend API
```

There's no native networking, storage, or business logic in this repo — the WebView delegates entirely to the deployed Next.js frontend, which talks to the [backend](https://github.com/santoshkumawat/expensely-backend) exactly as it does in a desktop browser (JWT stored in the WebView's local storage, same REST calls).

## Possible next steps

If this moves toward a more native experience later:

1. Swap the hardcoded URL for a build-config value, so debug builds can point at a local backend.
2. Add native push notifications, since a WebView can't receive them the way a native client can.
3. Consider a native rewrite of high-traffic screens (Expenses, Dashboard) if WebView performance or offline support becomes a problem.

## App ID

`com.expensely.app`
