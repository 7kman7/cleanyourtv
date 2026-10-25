# Shrink and optimise: keep app class and method names and line numbers so crash reports
# and stack traces remain readable, while allowing AndroidX libraries to be minified.
-keepattributes SourceFile,LineNumberTable
-keepnames class com.gathin.tvhome.** { *; }
-keepclassmembernames class com.gathin.tvhome.** { *; }
