package com.taki.tiktokcompanion

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Switch
import android.widget.Toast
import com.taki.tiktokcompanion.banner.BannerData
import com.taki.tiktokcompanion.banner.BannerPreviewView
import com.taki.tiktokcompanion.banner.BannerRenderer
import com.taki.tiktokcompanion.data.DraftRecord
import com.taki.tiktokcompanion.data.MessageRecord
import com.taki.tiktokcompanion.data.StreakSettings
import com.taki.tiktokcompanion.data.VaultDb
import com.taki.tiktokcompanion.notify.TikTokNotificationListener
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var database: VaultDb
    private lateinit var root: LinearLayout
    private lateinit var pageContent: LinearLayout
    private var activeTab: Tab = Tab.VAULT
    private var refreshOnResume = false

    private enum class Tab(val label: String) {
        VAULT("Kho lưu trữ"), DRAFTS("Bản nháp"), BANNER("Banner"), STREAKS("Chuỗi"), NOTES("Ghi chú"), SETTINGS("Cài đặt")
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
        if (::root.isInitialized && refreshOnResume) {
            refreshOnResume = false
            render()
        }
    }

    @Deprecated("Deprecated in Android API; retained for minSdk 26 compatibility")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_POST_NOTIFICATIONS) render()
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
            setFillViewport(true)
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
                Tab.BANNER -> renderBannerStudio()
                Tab.STREAKS -> renderStreaks()
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

        val search = EditText(this).apply {
            hint = "Tìm theo tên hoặc nội dung…"
            textSize = 14f
            setSingleLine(true)
            setPadding(dp(13), dp(10), dp(13), dp(10))
            background = rounded(COLOR_CARD, 14)
        }
        pageContent.addView(search, matchWrap().apply { bottomMargin = dp(10) })
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pageContent.addView(results, matchWrap())

        fun showMatches(query: String) {
            results.removeAllViews()
            val filtered = messages.filter {
                query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true) ||
                    it.displayText.contains(query, true) || it.note.orEmpty().contains(query, true)
            }
            if (filtered.isEmpty()) {
                results.addView(emptyState("Không tìm thấy", "Thử một từ khóa khác."))
            } else filtered.forEach { results.addView(messageCard(it)) }
        }
        showMatches("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = showMatches(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
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

    private fun renderBannerStudio() {
        pageContent.addView(sectionTitle(
            "Banner Studio",
            "Tạo ảnh banner PNG 1440 × 560 để chia sẻ hoặc đăng thủ công. Người khác thấy ảnh sau khi bạn gửi/đăng lên TikTok."
        ))

        val title = EditText(this).apply {
            hint = "Tiêu đề banner"
            setText("YOUR SPACE")
            textSize = 15f
            setSingleLine(true)
        }
        val subtitle = EditText(this).apply {
            hint = "Dòng mô tả"
            setText("A little space that feels like you.")
            textSize = 14f
            setSingleLine(true)
        }
        val footer = EditText(this).apply {
            hint = "Tên / @username (không bắt buộc)"
            setText("@yourname")
            textSize = 14f
            setSingleLine(true)
        }
        var theme = 0
        val preview = BannerPreviewView(this, BannerData("YOUR SPACE", "A little space that feels like you.", "@yourname", theme)).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(168))
        }
        pageContent.addView(card().apply {
            addView(text("Xem trước", 14, COLOR_TEXT, true))
            addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(168)))
            addView(text("Chọn tông màu", 12, COLOR_MUTED, true).apply { setPadding(0, dp(9), 0, dp(3)) })
            val paletteRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            listOf("Tím", "Xanh", "Hoàng hôn", "Đêm").forEachIndexed { index, label ->
                val paletteButton = Button(this@MainActivity).apply {
                    text = label
                    textSize = 10f
                    isAllCaps = false
                    setTextColor(COLOR_ACCENT)
                    background = rounded(COLOR_ACCENT_BG, 12)
                    minHeight = dp(38)
                    minimumHeight = dp(38)
                    setPadding(dp(5), 0, dp(5), 0)
                    setOnClickListener {
                        theme = index
                        updateBannerPreview(preview, title, subtitle, footer, theme)
                    }
                }
                paletteRow.addView(paletteButton, LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                    if (index > 0) marginStart = dp(4)
                })
            }
            addView(paletteRow, matchWrap())
        })

        pageContent.addView(card().apply {
            addView(text("Nội dung banner", 15, COLOR_TEXT, true))
            addView(text("Tiêu đề", 12, COLOR_MUTED, true))
            addView(title, matchWrap())
            addView(text("Mô tả", 12, COLOR_MUTED, true).apply { setPadding(0, dp(9), 0, dp(2)) })
            addView(subtitle, matchWrap())
            addView(text("Dòng chân banner", 12, COLOR_MUTED, true).apply { setPadding(0, dp(9), 0, dp(2)) })
            addView(footer, matchWrap())
            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) =
                    updateBannerPreview(preview, title, subtitle, footer, theme)
                override fun afterTextChanged(s: Editable?) = Unit
            }
            title.addTextChangedListener(watcher)
            subtitle.addTextChangedListener(watcher)
            footer.addTextChangedListener(watcher)
            addView(actionButton("Xuất PNG và chia sẻ…") {
                shareBanner(BannerData(
                    title.text?.toString().orEmpty(),
                    subtitle.text?.toString().orEmpty(),
                    footer.text?.toString().orEmpty(),
                    theme
                ))
            })
            addView(actionButton("Mở TikTok") { openTikTok() })
        })

        pageContent.addView(text(
            "Banner của Companion là ảnh độc lập, không thay đổi trực tiếp hồ sơ TikTok. Bạn cần tự đăng/gửi ảnh để người khác nhìn thấy.",
            12, COLOR_MUTED
        ))
    }

    private fun updateBannerPreview(
        preview: BannerPreviewView,
        title: EditText,
        subtitle: EditText,
        footer: EditText,
        theme: Int
    ) {
        preview.update(BannerData(
            title.text?.toString().orEmpty(),
            subtitle.text?.toString().orEmpty(),
            footer.text?.toString().orEmpty(),
            theme
        ))
    }

    private fun shareBanner(data: BannerData) {
        try {
            val folder = File(cacheDir, "banners").apply { mkdirs() }
            val filename = "banner-${System.currentTimeMillis()}-${(1000..9999).random()}.png"
            val file = File(folder, filename)
            val bitmap = BannerRenderer.createBitmap(data)
            FileOutputStream(file).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IllegalStateException("Không thể ghi ảnh PNG")
                }
            }
            bitmap.recycle()
            val uri = Uri.parse("content://$packageName.bannerprovider/banner/$filename")
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Chia sẻ banner"))
        } catch (error: Exception) {
            toast("Không thể xuất banner: ${error.message ?: "lỗi không xác định"}")
        }
    }

    private fun renderStreaks() {
        val settings = database.getStreakSettings()
        pageContent.addView(sectionTitle(
            "Trả lời chuỗi nhanh",
            "Tạo nút trả lời nhanh khi thông báo TikTok khớp từ khóa. Người dùng vẫn phải nhấn nút và xác nhận gửi."
        ))
        val enabled = Switch(this).apply {
            text = "Bật gợi ý trả lời chuỗi"
            textSize = 14f
            setTextColor(COLOR_TEXT)
            isChecked = settings.enabled
            setPadding(0, dp(3), 0, dp(8))
        }
        val trigger = EditText(this).apply {
            hint = "Từ khóa nhận diện (ví dụ 🔥)"
            setText(settings.trigger)
            textSize = 14f
            setSingleLine(true)
        }
        val reply = EditText(this).apply {
            hint = "Câu trả lời gợi ý"
            setText(settings.replyTemplate)
            textSize = 14f
            minLines = 2
            maxLines = 4
        }
        pageContent.addView(card().apply {
            addView(enabled, matchWrap())
            addView(text("Chỉ tạo gợi ý khi nội dung thông báo có", 12, COLOR_MUTED, true))
            addView(trigger, matchWrap())
            addView(text("Câu trả lời mẫu (có thể sửa trước khi gửi)", 12, COLOR_MUTED, true).apply { setPadding(0, dp(10), 0, dp(3)) })
            addView(reply, matchWrap())
            addView(actionButton("Lưu cài đặt trả lời nhanh") {
                val triggerValue = trigger.text?.toString().orEmpty().trim()
                val replyValue = reply.text?.toString().orEmpty().trim()
                if (enabled.isChecked && triggerValue.isBlank()) {
                    trigger.error = "Cần có từ khóa kích hoạt"
                } else if (enabled.isChecked && replyValue.isBlank()) {
                    reply.error = "Nhập câu trả lời mẫu"
                } else {
                    database.saveStreakSettings(StreakSettings(enabled.isChecked, triggerValue, replyValue))
                    toast(if (enabled.isChecked) "Đã bật gợi ý trả lời chuỗi" else "Đã tắt gợi ý trả lời chuỗi")
                    render()
                }
            })
        })

        val notificationPermissionGranted = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        pageContent.addView(card().apply {
            addView(text("Điều kiện hoạt động", 15, COLOR_TEXT, true))
            addView(bullet("Đã bật quyền Notification Access cho TikTok Companion."))
            addView(bullet("TikTok phải gửi thông báo có nội dung khớp từ khóa."))
            addView(bullet("Thông báo TikTok phải có sẵn hành động trả lời nhanh (RemoteInput). Nếu không có, Companion không thể gửi tin nhắn thay TikTok."))
            addView(bullet("Bạn cần nhấn ‘Trả lời chuỗi’ và xác nhận gửi; không có chế độ tự gửi âm thầm."))
            addView(text(
                if (notificationPermissionGranted) "Quyền thông báo của Companion: đã sẵn sàng."
                else "Android 13 trở lên cần cấp quyền thông báo để hiện nút trả lời nhanh.",
                12, if (notificationPermissionGranted) COLOR_ACCENT else COLOR_DANGER, true
            ))
            if (!notificationPermissionGranted) {
                addView(actionButton("Cấp quyền thông báo") {
                    if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
                })
            }
            addView(actionButton("Mở cài đặt quyền đọc thông báo") { openNotificationSettings() })
        })
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
            addView(bullet("Kho lưu trữ và cấu hình được mã hóa, lưu cục bộ; ứng dụng không khai báo quyền Internet."))
            addView(bullet("Banner được xuất thành ảnh và chia sẻ bằng bảng chia sẻ Android; người khác chỉ thấy sau khi bạn đăng/gửi."))
            addView(bullet("Không đọc cơ sở dữ liệu riêng, không chặn/sửa gói tin hoặc giả mạo dữ liệu máy chủ TikTok."))
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
        pageContent.addView(text("TikTok Companion v0.2.0 · Android 8.0 trở lên", 12, COLOR_MUTED).apply {
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
        val quickActions = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
        quickActions.addView(actionButton("Sao chép") { copyText(message.displayText, "Đã sao chép nội dung") },
            LinearLayout.LayoutParams(0, dp(43), 1f).apply { marginEnd = dp(5) })
        quickActions.addView(actionButton("Mở TikTok") { openTikTok() },
            LinearLayout.LayoutParams(0, dp(43), 1f).apply { marginStart = dp(5) })
        addView(quickActions)
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
        addView(actionButton("Sao chép và mở TikTok") {
            copyText(draft.body, "Đã sao chép. Mở TikTok để dán và gửi.")
            openTikTok()
        })
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
            setSingleLine(true)
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
            refreshOnResume = true
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
            refreshOnResume = false
            toast("Không mở được cài đặt Notification Access trên thiết bị này")
        }
    }

    private fun openTikTok() {
        val packageCandidates = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
        val launchIntent = packageCandidates.firstNotNullOfOrNull { packageName ->
            packageManager.getLaunchIntentForPackage(packageName)
        }
        if (launchIntent == null) {
            toast("Chưa tìm thấy ứng dụng TikTok trên thiết bị")
            return
        }
        runCatching { startActivity(launchIntent) }
            .onFailure { toast("Không thể mở TikTok: ${it.message ?: "lỗi không xác định"}") }
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
        private const val REQUEST_POST_NOTIFICATIONS = 903
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
