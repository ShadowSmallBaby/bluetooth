package net.ankio.bluetooth.ui.compose

import android.content.SharedPreferences
import androidx.compose.runtime.*
import net.ankio.bluetooth.utils.sp

/** Observe the same preferences used by the hook bridge and foreground services. */
@Composable
fun <T> preference(key: String, read: () -> T): T {
    var value by remember(key) { mutableStateOf(read()) }
    val currentRead by rememberUpdatedState(read)
    DisposableEffect(key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == key || changed == null) value = currentRead()
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        value = currentRead()
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}
