package net.ankio.bluetooth.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import net.ankio.bluetooth.R

enum class AppTab(@StringRes val title: Int, @DrawableRes val icon: Int) {
    Webdav(R.string.nav_webdav, R.drawable.ic_cloud),
    Home(R.string.nav_home, R.drawable.ic_bluetooth),
    Settings(R.string.nav_settings, R.drawable.ic_setting)
}
