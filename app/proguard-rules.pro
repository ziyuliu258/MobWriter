# Release 与调试包分开：这里只服务 R8。序列化走编译期生成的 serializer，不靠反射找字段。
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn kotlin.**
-dontwarn kotlinx.serialization.**
-dontwarn androidx.compose.**
-dontwarn org.scilab.forge.jlatexmath.**

-keepclassmembers class kotlinx.serialization.json.** { *; }

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

# 令牌存在 EncryptedSharedPreferences 里。这个库很小，别让 R8 把它裁坏。
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**
-dontwarn com.google.errorprone.annotations.**

-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
