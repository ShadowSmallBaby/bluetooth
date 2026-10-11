package net.ankio.bluetooth
import net.ankio.bluetooth.config.PreferenceKeys

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.text.TextUtils
import com.flurry.android.FlurryAgent
import com.quickersilver.themeengine.ThemeEngine
import net.ankio.bluetooth.utils.LocaleDelegate
import net.ankio.bluetooth.utils.SpUtils
import java.util.*


open class App : Application() {

    companion object {
        fun getLocale(tag: String): Locale? {
            return if (TextUtils.isEmpty(tag) || "SYSTEM" == tag) {
                LocaleDelegate.systemLocale
            } else Locale.forLanguageTag(tag)
        }

        fun getLocale(): Locale? {
            val tag: String = SpUtils.getString(PreferenceKeys.LANGUAGE, "SYSTEM")
            return getLocale(tag)
        }

        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
    }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        // Flavor-specific: modern binds the LibXposed service, legacy is a no-op.
        ActivationSupport.install(this)
        ThemeEngine.applyToActivities(this)
        //设置语言
        LocaleDelegate.defaultLocale = getLocale()

        //匿名统计
        if (SpUtils.getBoolean(PreferenceKeys.ANALYZE, true)) {
            FlurryAgent.Builder()
                .withLogEnabled(true)
                .build(this, "JS6H7TJJDMQRDDMWG55C")
        }

    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
    }

}
