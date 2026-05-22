# SOS Tech — ProGuard / R8 rules
# Keep Hilt-generated classes
-keep class dagger.hilt.** { *; }
-keep class **_HiltModules** { *; }
-keep class **_ComponentTreeDeps** { *; }

# Keep Room generated code
-keep class * extends androidx.room.RoomDatabase { *; }

# Keep SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# Keep Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }

# Keep WorkManager workers
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.hilt.work.HiltWorker

# Keep Timber (strip debug logs in release via build config, not R8)
-keep class timber.log.Timber { *; }

# Kotlin — keep metadata for reflection
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
