-keep class com.riz.app.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
