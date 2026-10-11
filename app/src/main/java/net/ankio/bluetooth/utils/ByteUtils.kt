package net.ankio.bluetooth.utils

import android.util.Log
import java.util.Locale

/**
 *
 * @description ByteUtils
 * @author llw
 * @date 2021/9/13 14:28
 */
object ByteUtils {

    private const val HEX_CHARS = "0123456789ABCDEF"

    /**
     * Convert hex string to byte[]
     *
     * @param hexString the hex string
     * @return byte[]
     */
    fun hexStringToBytes(hexString: String): ByteArray {
        val hexString = hexString.uppercase(Locale.getDefault())
        val length = hexString.length / 2
        val hexChars = hexString.toCharArray()
        val byteArrayResult = ByteArray(length)
        for (i in 0 until length) {
            val pos = i * 2
            byteArrayResult[i] = (charToByte(hexChars[pos]).toInt()
                .shl(4) or charToByte(hexChars[pos + 1]).toInt()).toByte()
        }
        Log.d("TAG", "hexStringToBytes: " + byteArrayResult.contentToString())
        return byteArrayResult
    }

    /**
     * 将 BLE 广播原始字节按 AD 结构（TLV）截断尾部 0x00 填充，返回大写 HEX 字符串。
     * 用于 UI 显示与配置存储的统一归一化：只保留有效 AD 段，不含填充。
     */
    fun advDataToHexString(record: ByteArray?): String {
        if (record == null || record.isEmpty()) return ""
        var index = 0
        var validEnd = 0
        val size = record.size
        while (index < size) {
            val length = record[index].toInt() and 0xFF
            if (length == 0) break
            if (index + 1 + length > size) break
            index += 1 + length
            validEnd = index
        }
        val sb = StringBuilder(validEnd * 2)
        for (i in 0 until validEnd) {
            val v = record[i].toInt() and 0xFF
            sb.append(HEX_CHARS[v shr 4]).append(HEX_CHARS[v and 0x0F])
        }
        return sb.toString()
    }

    /**
     * Convert char to byte
     *
     * @param c char
     * @return byte
     */
    private fun charToByte(c: Char): Byte = HEX_CHARS.indexOf(c).toByte()

}