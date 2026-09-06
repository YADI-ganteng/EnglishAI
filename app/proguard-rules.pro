# ML Kit
-keep class com.google.mlkit.** { *; }

# Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }

# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable

# Keep custom exceptions
-keep class com.yad.englishai.** { *; }