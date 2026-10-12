package net.ankio.bluetooth.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.ankio.bluetooth.App
import net.ankio.bluetooth.ui.compose.MainScreen
import net.ankio.bluetooth.utils.ContextWrapper
import net.ankio.bluetooth.utils.LocaleDelegate

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ContextWrapper.wrap(newBase, App.getLocale()))
        LocaleDelegate.changedList[javaClass] = true
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MainScreen(this) }
    }
    fun changeLanguage() {
        LocaleDelegate.defaultLocale = App.getLocale()
        LocaleDelegate.changedList.clear()
        LocaleDelegate.changedList[javaClass] = true
        recreate()
    }
}
