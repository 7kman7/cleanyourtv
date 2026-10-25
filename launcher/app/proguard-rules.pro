# Shrink and optimise: keep app class and method names and line numbers so crash reports
# and stack traces remain readable, while allowing AndroidX libraries to be minified/obfuscated.
-keepattributes SourceFile,LineNumberTable
-keep class com.gathin.tvhome.** { *; }
-keepclassmembers class com.gathin.tvhome.** { *; }
