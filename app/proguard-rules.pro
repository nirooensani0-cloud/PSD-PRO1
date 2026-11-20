# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-dontwarn kotlinx.serialization.**
-dontwarn org.bouncycastle.**
-keep class net.zetetic.database.sqlcipher.** { *; }
-keep class org.osmdroid.** { *; }
