# Anchor — ProGuard / R8 rules for release builds

# Keep Kotlin serialization metadata
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all serializable data classes
-keep,includedescriptorclasses class com.anchor.** implements kotlinx.serialization.KSerializer { *; }
-keep class com.anchor.data.repository.AnchorExport { *; }
-keep class com.anchor.domain.model.** { *; }
-keep class com.anchor.domain.repository.HistoryLog { *; }

# Koin
-keepnames class org.koin.** { *; }
-keep class org.koin.** { *; }

# Kotlin coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# AndroidX Compose — do not strip layout info
-keep class androidx.compose.** { *; }

# Accessibility service
-keep class com.anchor.android.AnchorAccessibilityService { *; }
