# Release shrinker rules for the OnePage companion app.
#
# Per-library consumer rules already cover OkHttp, Compose, CameraX and ZXing.
# Keep the app's own entry points explicit so a minified build can never drop a
# class the framework instantiates by name from the manifest.
-keep class com.onepage.companion.OnePageApp { *; }
-keep class com.onepage.companion.MainActivity { *; }

# org.json is provided by the platform; silencing the R8 "missing class" noise
# for the JVM test-scope copy keeps the release build warning-free.
-dontwarn org.json.**
