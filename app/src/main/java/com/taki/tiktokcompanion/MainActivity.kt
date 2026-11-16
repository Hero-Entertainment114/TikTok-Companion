package com.taki.tiktokcompanion

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.taki.tiktokcompanion.data.DraftRecord
import com.taki.tiktokcompanion.data.MessageRecord
import com.taki.tiktokcompanion.data.VaultDb
import com.taki.tiktokcompanion.notify.TikTokNotificationListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var database: VaultDb
    private lateinit var root: LinearLayout
    private lateinit var pageContent: LinearLayout
    private var activeTab: Tab = Tab.VAULT

    private enum class Tab(val label: String) {
        VAULT("Kho lưu trữ"), DRAFTS("Bản nháp"), NOTES("Ghi chú"), SETTINGS("Cài đặt")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = VaultDb(applicationContext)
        window.statusBarColor = COLOR_BG
        window.navigationBarColor = COLOR_BG
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) render()
    }

    private fun render() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BG)
            setPadding(dp(18), dp(10), dp(18), dp(8))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(12))
        }
        header.addView(text("TIKTOK COMPANION", 12, COLOR_ACCENT, true))
        header.addView(text("Không gian tin nhắn riêng", 25, COLOR_TEXT, true).apply {
            setPadding(0, dp(4), 0, dp(3))
        })
        header.addView(text("Lưu những gì Android nhận được. Giữ bản gốc, chỉnh sửa bản xem riêng.", 13, COLOR_MUTED))
        root.addView(header)

        val navScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
        }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Tab.values().forEach { tab ->
            val selected = tab == activeTab
            val button = Button(this).apply {
                text = tab.label
                textSize = 12f
                isAllCaps = false
                setTextColor(if (selected) COLOR_WHITE else COLOR_MUTED)
                background = rounded(if (selected) COLOR_ACCENT else COLOR_CARD, 16)
                minHeight = dp(42)
                minimumHeight = dp(42)
                setPadding(dp(12), 0, dp(12), 0)
                setOnClickListener {
                    activeTab = tab
                    render()
                }
            }
            val params = LinearLayout.LayoutParams(dp(116), dp(44)).apply {
                marginEnd = dp(7)
            }
            nav.addView(button, params)
        }
        navScroll.addView(nav)
        root.addView(navScroll)

        val scroll = ScrollView(this).apply {
            fillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        pageContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(18))
        }
        scroll.addView(pageContent)
        root.addView(scroll)
        root.addView(text("DỮ LIỆU CHỈ LƯU TRÊN THIẾT BỊ NÀY", 10, COLOR_MUTED, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, dp(2))
        })
        setContentView(root)

        try {
            when (activeTab) {
                Tab.VAULT -> renderVault()
                Tab.DRAFTS -> renderDrafts()
                Tab.NOTES -> renderNotes()
                Tab.SETTINGS -> renderSettings()
            }
        } catch (error: Exception) {
            pageContent.addView(card().apply {
                addView(text("Không thể mở kho dữ liệu", 17, COLOR_TEXT, true))
                addView(text("${error.javaClass.simpleName}: ${error.message ?: "Lỗi dữ liệu hoặc khóa mã hóa"}", 13, COLOR_MUTED))
                addView(actionButton("Thử tải lại") { render() })
            })
        }
    }

    private fun renderVault() {
        val enabled = isNotificationAccessEnabled()
        pageContent.addView(card().apply {
            addView(text(if (enabled) "Đang sẵn sàng nhận thông báo" else "Cần bật quyền đọc thông báo", 16, COLOR_TEXT, true))
            addView(text(
                if (enabled) "Ứng dụng sẽ lưu phần văn bản TikTok hiển thị trong thông báo mới."
                else "Android chưa cấp Notification Access. Bật quyền để bắt đầu lưu thông báo TikTok.",
                13, COLOR_MUTED
            ))
            addView(actionButton(if (enabled) "Mở cài đặt quyền thông báo" else "Bật quyền đọc thông báo") {
                openNotificationSettings()
            })
        })

        pageContent.addView(sectionTitle("Nội dung TikTok đã ghi nhận", "Đọc tại đây không mở TikTok và không tự gửi trạng thái đã xem."))
        val messages = database.getMessages()
        if (messages.isEmpty()) {
            pageContent.addView(emptyState(
                "Chưa có nội dung được lưu",
                "Bật quyền ở trên, sau đó chờ thông báo TikTok mới có nội dung hiển thị. Ứng dụng lưu văn bản nhìn thấy được, không thể xác định chắc chắn mọi thông báo đều là tin nhắn trực tiếp. Thông báo cũ không được khôi phục ngược lại."
            ))
            return
        }
        messages.forEach { message ->
            pageContent.addView(messageCard(message))
        }
    }

    private fun renderDrafts() {
        pageContent.addView(sectionTitle("Bản nháp riêng", "Soạn trước, sau đó sao chép hoặc chia sẻ để tự dán vào TikTok."))
        pageContent.addView(actionButton("＋  Tạo bản nháp mới") { showDraftDialog(null) })
        val drafts = database.getDrafts()
        if (drafts.isEmpty()) {
            pageContent.addView(emptyState("Chưa có bản nháp", "Các bản nháp được mã hóa và chỉ lưu trong ứng dụng này."))
        } else {
            drafts.forEach { draft -> pageContent.addView(draftCard(draft)) }
        }
    }

    private fun renderNotes() {
        pageContent.addView(sectionTitle("Ghi chú & bản xem đã chỉnh", "Mọi thay đổi ở đây chỉ tác động đến bản lưu trong Companion."))
        val notes = database.getNotes()
        if (notes.isEmpty()) {
            pageContent.addView(emptyState("Chưa có ghi chú", "Mở một mục trong Kho lưu trữ để thêm ghi chú hoặc chỉnh sửa phần hiển thị."))
        } else {
            notes.forEach { message ->
                pageContent.addView(card().apply {
                    addView(text(message.title.ifBlank { "Thông báo TikTok" }, 15, COLOR_TEXT, true))
                    addView(text(message.note.orEmpty(), 14, COLOR_MUTED))
                    addView(actionButton("Mở bản lưu") { showMessageDialog(message) })
                })
            }
        }
    }

    private fun renderSettings() {
        pageContent.addView(sectionTitle("Quyền riêng tư & hoạt động", "Bạn có quyền kiểm soát dữ liệu đã lưu."))
        pageContent.addView(card().apply {
            addView(text("Quyền truy cập thông báo", 16, COLOR_TEXT, true))
            addView(text(
                if (isNotificationAccessEnabled()) "Đã bật trên Android." else "Chưa bật. Listener sẽ không nhận được thông báo khi quyền này tắt.",
                13, COLOR_MUTED
            ))
            addView(actionButton("Mở trang Notification Access") { openNotificationSettings() })
        })
        pageContent.addView(card().apply {
            addView(text("Cách dữ liệu được xử lý", 16, COLOR_TEXT, true))
            addView(bullet("Chỉ xử lý thông báo từ package TikTok được hỗ trợ."))
            addView(bullet("Mã hóa nội dung bằng AES-GCM; khóa nằm trong Android Keystore."))
            addView(bullet("Không khai báo quyền Internet, không tải dữ liệu lên máy chủ."))
            addView(bullet("Không đọc cơ sở dữ liệu riêng, bộ nhớ riêng hoặc tin nhắn trên máy chủ TikTok."))
            addView(bullet("Không thể lưu nội dung nếu TikTok/Android ẩn nội dung thông báo hoặc không phát thông báo."))
        })
        pageContent.addView(card().apply {
            addView(text("Giới hạn của tính năng", 16, COLOR_TEXT, true))
            addView(text(
                "Kho lưu trữ chỉ có thể giữ văn bản mà Android thực sự nhận được. Nếu tin nhắn bị thu hồi trước khi thông báo tới máy, nội dung thông báo bị ẩn, hoặc TikTok không đưa nội dung vào thông báo, ứng dụng không thể khôi phục tin nhắn đó. Việc đọc trong kho này không mở cuộc trò chuyện, nhưng không thể bảo đảm trạng thái đã xem ở các thao tác khác trong TikTok.",
                13, COLOR_MUTED
            ))
        })
        pageContent.addView(card().apply {
            addView(text("Xóa dữ liệu", 16, COLOR_TEXT, true))
            addView(text("Xóa toàn bộ thông báo đã lưu. Bản nháp riêng không bị ảnh hưởng.", 13, COLOR_MUTED))
            addView(actionButton("Xóa kho thông báo", danger = true) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Xóa kho thông báo?")
                    .setMessage("Thao tác này không thể hoàn tác.")
                    .setNegativeButton("Hủy", null)
                    .setPositiveButton("Xóa") { _, _ ->
                        database.clearMessages()
                        toast("Đã xóa kho thông báo")
                        render()
                    }
                    .show()
            })
        })
        pageContent.addView(text("TikTok Companion v0.1.0 · Android 8.0 trở lên", 12, COLOR_MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(12))
        })
    }

    private fun messageCard(message: MessageRecord): View = card().apply {
        addView(text(message.title.ifBlank { "Thông báo TikTok" }, 15, COLOR_TEXT, true))
        addView(text(formatDate(message.timestamp), 11, COLOR_MUTED))
        addView(text(message.displayText, 14, COLOR_TEXT).apply { maxLines = 4 })
        if (!message.editedDisplay.isNullOrBlank()) {
            addView(text("Có bản hiển thị đã chỉnh sửa", 11, COLOR_ACCENT, true))
        }
        if (!message.note.isNullOrBlank()) {
            addView(text("Ghi chú: ${message.note}", 12, COLOR_MUTED).apply { maxLines = 2 })
        }
        addView(actionButton("Mở / chỉnh bản hiển thị") { showMessageDialog(message) })
    }

    private fun draftCard(draft: DraftRecord): View = card().apply {
        addView(text(draft.title.ifBlank { "Bản nháp không tiêu đề" }, 15, COLOR_TEXT, true))
        addView(text("Sửa gần nhất: ${formatDate(draft.updatedAt)}", 11, COLOR_MUTED))
        addView(text(draft.body, 14, COLOR_TEXT).apply { maxLines = 3 })
        val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(actionButton("Sửa") { showDraftDialog(draft) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(5) })
        row.addView(actionButton("Sao chép") { copyText(draft.body, "Đã sao chép bản nháp") }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(5) })
        addView(row)
        val share = actionButton("Chia sẻ…") { shareText(draft.body) }
        addView(share)
    }

    private fun showMessageDialog(message: MessageRecord) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        form.addView(text("Nội dung gốc (chỉ đọc)", 12, COLOR_MUTED, true))
        val original = EditText(this).apply {
            setText(message.body)
            isEnabled = false
            textSize = 14f
            minLines = 2
            maxLines = 5
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = rounded(COLOR_BG, 12)
        }
        form.addView(original, matchWrap())
        form.addView(text("Bản hiển thị riêng", 12, COLOR_MUTED, true).apply { setPadding(0, dp(12), 0, dp(3)) })
        val edited = EditText(this).apply {
            setText(message.displayText)
            hint = "Nhập nội dung muốn hiển thị trong Companion"
            textSize = 14f
            minLines = 3
            gravity = Gravity.TOP
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = rounded(COLOR_BG, 12)
        }
        form.addView(edited, matchWrap())
        form.addView(text("Ghi chú riêng", 12, COLOR_MUTED, true).apply { setPadding(0, dp(12), 0, dp(3)) })
        val note = EditText(this).apply {
            setText(message.note.orEmpty())
            hint = "Ví dụ: cần trả lời sau"
            textSize = 14f
            minLines = 2
            gravity = Gravity.TOP
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = rounded(COLOR_BG, 12)
        }
        form.addView(note, matchWrap())

        val dialog = AlertDialog.Builder(this)
            .setTitle(message.title.ifBlank { "Thông báo TikTok" })
            .setView(form)
            .setNegativeButton("Đóng", null)
            .setPositiveButton("Lưu bản riêng", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                database.updateMessagePresentation(
                    message.id,
                    edited.text?.toString().orEmpty(),
                    note.text?.toString().orEmpty()
                )
                toast("Đã lưu bản hiển thị riêng")
                dialog.dismiss()
                render()
            }
        }
        dialog.show()
    }

    private fun showDraftDialog(existing: DraftRecord?) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        val title = EditText(this).apply {
            hint = "Tên / người nhận (chỉ để bạn phân loại)"
            setText(existing?.title.orEmpty())
            textSize = 14f
            singleLine = true
        }
        val body = EditText(this).apply {
            hint = "Soạn nội dung tin nhắn…"
            setText(existing?.body.orEmpty())
            textSize = 15f
            minLines = 5
            maxLines = 12
            gravity = Gravity.TOP
        }
        form.addView(text("Tên bản nháp", 12, COLOR_MUTED, true))
        form.addView(title, matchWrap())
        form.addView(text("Nội dung", 12, COLOR_MUTED, true).apply { setPadding(0, dp(10), 0, dp(3)) })
        form.addView(body, matchWrap())

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Tạo bản nháp" else "Chỉnh sửa bản nháp")
            .setView(form)
            .setNegativeButton("Đóng", null)
            .setPositiveButton("Lưu", null)
            .setNeutralButton(if (existing == null) "Sao chép" else "Xóa", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val draftBody = body.text?.toString().orEmpty()
                if (draftBody.isBlank()) {
                    body.error = "Hãy nhập nội dung trước khi lưu"
                } else {
                    database.saveDraft(existing?.id, title.text?.toString().orEmpty(), draftBody)
                    toast("Đã lưu bản nháp")
                    dialog.dismiss()
                    render()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (existing == null) {
                    val draftBody = body.text?.toString().orEmpty()
                    if (draftBody.isNotBlank()) copyText(draftBody, "Đã sao chép")
                    else toast("Chưa có nội dung để sao chép")
                } else {
                    AlertDialog.Builder(this)
                        .setMessage("Xóa bản nháp này?")
                        .setNegativeButton("Hủy", null)
                        .setPositiveButton("Xóa") { _, _ ->
                            database.deleteDraft(existing.id)
                            dialog.dismiss()
                            render()
                        }.show()
                }
            }
        }
        dialog.show()
    }

    private fun isNotificationAccessEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        val component = ComponentName(this, TikTokNotificationListener::class.java).flattenToString()
        return enabled.split(":").any { it.equals(component, ignoreCase = true) }
    }

    private fun openNotificationSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
            toast("Không mở được cài đặt Notification Access trên thiết bị này")
        }
    }

    private fun copyText(value: String, message: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("TikTok Companion", value))
        toast(message)
    }

    private fun shareText(value: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, value)
        }
        startActivity(Intent.createChooser(intent, "Chia sẻ bản nháp"))
    }

    private fun sectionTitle(title: String, subtitle: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(2), dp(12), dp(2), dp(10))
        addView(text(title, 19, COLOR_TEXT, true))
        addView(text(subtitle, 12, COLOR_MUTED).apply { setPadding(0, dp(3), 0, 0) })
    }

    private fun emptyState(title: String, description: String): View = card().apply {
        addView(text(title, 16, COLOR_TEXT, true))
        addView(text(description, 13, COLOR_MUTED))
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(14), dp(15), dp(14))
        background = rounded(COLOR_CARD, 18)
        elevation = dp(1).toFloat()
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.bottomMargin = dp(11)
        layoutParams = params
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(2), 0, dp(5))
        setLineSpacing(dp(2).toFloat(), 1f)
    }

    private fun bullet(value: String): TextView = text("•  $value", 13, COLOR_MUTED).apply {
        setPadding(0, dp(4), 0, dp(5))
    }

    private fun actionButton(label: String, danger: Boolean = false, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setTextColor(if (danger) COLOR_DANGER else COLOR_ACCENT)
        background = rounded(if (danger) COLOR_DANGER_BG else COLOR_ACCENT_BG, 13)
        minHeight = dp(42)
        minimumHeight = dp(42)
        setPadding(dp(10), 0, dp(10), 0)
        setOnClickListener { onClick() }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(43))
        params.topMargin = dp(8)
        layoutParams = params
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun matchWrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun formatDate(millis: Long): String = SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault()).format(Date(millis))

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_SHORT).show()

    companion object {
        private const val COLOR_BG = 0xFFF7F7FC.toInt()
        private const val COLOR_CARD = 0xFFFFFFFF.toInt()
        private const val COLOR_TEXT = 0xFF24243A.toInt()
        private const val COLOR_MUTED = 0xFF77778B.toInt()
        private const val COLOR_ACCENT = 0xFF6558D9.toInt()
        private const val COLOR_ACCENT_BG = 0xFFEDEBFF.toInt()
        private const val COLOR_WHITE = 0xFFFFFFFF.toInt()
        private const val COLOR_DANGER = 0xFFB3261E.toInt()
        private const val COLOR_DANGER_BG = 0xFFFFEDEA.toInt()
    }
}
