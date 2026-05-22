# Kotlin
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlin.Metadata { *; }

# Keep service classes
-keep class com.smarttech.auto.** { *; }
