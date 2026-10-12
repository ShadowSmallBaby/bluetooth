package net.ankio.bluetooth.data

import net.ankio.bluetooth.config.PreferenceKeys
import net.ankio.bluetooth.utils.SpUtils

/** One repository serializes read/modify/write operations from both screens. */
object AppRepositories {
    val webdav = net.ankio.bluetooth.webdav.WebdavRepository()
    val history = HistoryRepository(object : HistoryStore {
        override fun read() = SpUtils.getString(PreferenceKeys.HISTORY)
        override fun write(json: String) = SpUtils.putString(PreferenceKeys.HISTORY, json)
    })
}
