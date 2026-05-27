# ==============================
# Stacca! — ProGuard Rules v1.6
# ==============================

# Mantieni annotazioni (richiesto da molte librerie)
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# --- App: Receivers e Activities dichiarate nel Manifest ---
# Android li istanzia per nome stringa, non possono essere rinominati
-keep class com.stacca.app.receivers.** { *; }
-keep class com.stacca.app.ui.** { *; }
-keep class com.stacca.app.StaccaApplication { *; }

# --- Supabase / Ktor ---
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class kotlinx.serialization.** { *; }
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# --- Google Play Billing ---
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# --- Google Credential Manager / Identity ---
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.** { *; }
-dontwarn com.google.android.libraries.identity.**

# --- OkHttp (usato da Ktor) ---
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# --- Coroutines ---
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# --- Evita warning su classi Java interne ---
-dontwarn java.lang.invoke.**
-dontwarn **$$Lambda$*
