# Add project specific ProGuard rules here.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Gson 2.11+ bundles consumer rules; these stay as a harmless safety net. Preserve generic
# signatures for TypeToken callers under R8 full mode; history uses a concrete array type.
-keep,allowobfuscation,allowoptimization class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowoptimization class * extends com.google.gson.reflect.TypeToken

# Framework entry points are loaded by name from xposed_init / java_init.list.
-keep class net.ankio.bluetooth.hook.ModernEntry { *; }
-keep class net.ankio.bluetooth.hook.LegacyBluetoothEntry { *; }
-keep class net.ankio.bluetooth.hook.LegacyStatusEntry { *; }
-keep class net.ankio.bluetooth.utils.ModuleStatus { *; }
-keep class io.github.libxposed.service.** { *; }
-dontwarn de.robv.android.xposed.**
-dontwarn io.github.libxposed.api.**

# Gson models retain their persisted/network field names across app upgrades.
-keep class net.ankio.bluetooth.bluetooth.BleDevice { *; }
-keep class net.ankio.bluetooth.bluetooth.BluetoothData { *; }
# Company table parsed from assets/company_ids.json; hook snapshot shared app→hooked process.
-keep class net.ankio.bluetooth.data.BluetoothCompany { *; }
-keep class net.ankio.bluetooth.config.HookSettings { *; }
-keep class net.ankio.bluetooth.hook.model.** { *; }
-keep class net.ankio.bluetooth.update.UpdateChecker$* { *; }
# Sardine uses Simple XML annotations and reflective constructors for DAV responses.
-keep class com.thegrizzlylabs.sardineandroid.model.** { *; }
-keep class org.simpleframework.xml.** { *; }
-dontwarn javax.xml.stream.**
# Optional desktop TLS providers probed by OkHttp; Android uses its own platform provider.
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
