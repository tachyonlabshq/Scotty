# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }
-keepnames class kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# Keep Gson serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class org.localsend.localsend_app.model.** { *; }

# Keep Nearby Connections API
-keep class com.google.android.gms.nearby.** { *; }
-dontwarn com.google.android.gms.nearby.**

# Keep DataStore
-keep class androidx.datastore.** { *; }
-keep class androidx.datastore.preferences.** { *; }

# Keep NFC service classes (by class name so HCE meta-data references work)
-keep class org.localsend.localsend_app.service.NfcHceService { *; }
-keep class org.localsend.localsend_app.service.NfcReaderManager { *; }
-keep class org.localsend.localsend_app.service.NearbyTransferService { *; }
-keep class org.localsend.localsend_app.service.NfcBeamMessage { *; }
-keep class org.localsend.localsend_app.service.NfcMessageHub { *; }
-keep class org.localsend.localsend_app.service.ReceivedFile { *; }

# Keep ViewModel classes
-keep class org.localsend.localsend_app.ui.MainViewModel { *; }
-keep class org.localsend.localsend_app.ui.NfcBeamStatus { *; }
-keep class org.localsend.localsend_app.ui.NfcBeamStatus$* { *; }
