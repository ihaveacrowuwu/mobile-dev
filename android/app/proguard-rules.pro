# ─────────────────────────────────────────────────────────────────────────────
# R8 / ProGuard rules for the release build.
#
# Most modern libraries ship their own consumer rules, so this file only covers
# what R8 cannot infer. Verify with:  ./gradlew assembleRelease && installRelease
# ─────────────────────────────────────────────────────────────────────────────

# ── Keep line numbers for readable crash reports, hide original file names ──
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Annotations required at runtime by Retrofit and kotlinx.serialization ───
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# ── kotlinx.serialization ───────────────────────────────────────────────────
# Serializers are generated as nested classes; keep them and their companions.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Our DTOs are only ever instantiated reflectively by the serializer.
-keep,includedescriptorclasses class com.nauhaan.skycast.data.remote.dto.** { *; }

# ── Retrofit ────────────────────────────────────────────────────────────────
# Retrofit's own consumer rules cover most of this; these guard the generic
# return types on suspend functions.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ── OkHttp ──────────────────────────────────────────────────────────────────
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Room ────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# ── Compose ─────────────────────────────────────────────────────────────────
# Compose ships full consumer rules; this only silences a known R8 warning.
-dontwarn androidx.compose.**

# ── Enums used in persisted/serialized state ────────────────────────────────
-keepclassmembers enum com.nauhaan.skycast.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
