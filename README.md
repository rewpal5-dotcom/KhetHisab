# KhetHisab — Android APK

**KhetHisab** ("Tractor ka hisab, chat ki tarah aasaan") ka native Android app.

Site: https://friendly-hisab-tracker.lovable.app/

**Direct APK download:** https://github.com/rewpal5-dotcom/KhetHisab/releases/latest/download/app-debug.apk

## Kya hai isme

- **Progress bar** — page load hote waqt upar patli bar
- **Back button** — app ke andar page history mein peeche
- **Offline screen** — internet na ho to saaf message + Retry button
- **Zoom lock** — pinch zoom aur left-right panning band, page device width par fix
- **Edge-to-edge safe** — Android 15 ke status/nav bar ke neeche content nahi jaata
- **Lovable badge hidden** — neeche ka "Edit with Lovable" wala promotion link hata diya gaya hai
- **Google login** — WebView ke liye normal Chrome user-agent set hai, taaki Google sign-in block na ho
- **Downloads** — file download ho to Downloads folder mein
- Sirf INTERNET + ACCESS_NETWORK_STATE permission, koi ad SDK nahi

## Offline ke baare mein (zaroori)

Ye app ek **cloud app** hai — login, hisab aur data SabSupabase/cloud par rehta hai, isliye **poora offline nahi chal sakta**. App kholne par internet chahiye hoga; internet na ho to app "Internet nahi mila" screen dikhata hai.

Agar aap chahte hain ki bina internet bhi kaam kare, to uske liye app ka apna local database (offline-first) chahiye — matlab site ka code le kar usme offline storage add karna padega. Wo alag kaam hai, bata dein to karte hain.

## Build

GitHub Actions (`.github/workflows/build-apk.yml`) — har build release mein APK attach karti hai.

```bash
./gradlew assembleDebug
```

## Tech

Java · WebView · minSdk 24 · targetSdk 35 · compileSdk 36 · AGP 8.9.3 · Gradle 8.11.1 · koi third-party SDK nahi

## License

MIT
