# Electricity Card APK

Android WebView wrapper for the supplied `index.html`.

Upload the contents of this project to the root of a GitHub repository.
GitHub Actions builds `app/build/outputs/apk/debug/app-debug.apk`.

Important: the supplied HTML references Tailwind and Google Fonts from external CDNs, so the HTML itself is bundled offline but those two external resources are not. The app can be made fully offline later by bundling the CSS/font locally.
