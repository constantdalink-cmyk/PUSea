package org.example.project

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

/** Android 剪贴板实现：复制到系统剪贴板并 Toast 提示已复制。 */
class AndroidTextCopier(private val context: Context) : TextCopier {
    override fun copy(text: String): Boolean {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        cm.setPrimaryClip(ClipData.newPlainText("CodeRiView", text))
        Toast.makeText(context, "copied: $text", Toast.LENGTH_SHORT).show()
        return true
    }
}
