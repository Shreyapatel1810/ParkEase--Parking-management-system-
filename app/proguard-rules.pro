# ParkEase ProGuard rules (only used if minifyEnabled is turned on for release builds)

# Keep model classes so Firebase can map database fields to Java fields
-keep class com.parkease.app.models.** { *; }

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }
