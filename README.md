# TikTok Companion v0.2.0

Ứng dụng Android native (Kotlin) hỗ trợ quản lý nội dung TikTok mà Android thực sự đưa vào thông báo, soạn nháp, tạo ảnh banner để chia sẻ và trả lời nhanh có xác nhận của người dùng.

## Tính năng mới trong v0.2.0

- **Banner Studio:** tạo banner PNG 1440 × 560, nhập tiêu đề/mô tả/tên, chọn 4 tông màu, xem trước và mở bảng chia sẻ Android. Có thể chọn TikTok nếu phiên bản ứng dụng trên máy hỗ trợ nhận ảnh được chia sẻ. Người khác chỉ nhìn thấy banner sau khi bạn chủ động đăng/gửi.
- **Gợi ý trả lời chuỗi:** cấu hình từ khóa (mặc định 🔥) và câu trả lời mẫu. Khi thông báo TikTok khớp từ khóa và thông báo gốc có hành động trả lời nhanh `RemoteInput`, Companion tạo một thông báo có nút **Trả lời chuỗi**. Người dùng phải bấm nút, có thể sửa nội dung và xác nhận gửi.
- **Tìm kiếm kho thông báo:** lọc theo tên, văn bản, bản hiển thị đã chỉnh hoặc ghi chú.
- **Mở TikTok nhanh:** thêm nút mở ứng dụng từ thẻ thông báo, bản nháp và Banner Studio.
- **Nâng cấp bản nháp:** tiếp tục sửa, sao chép, chia sẻ hoặc mở TikTok để tự dán/gửi.
- **Nâng cấp cơ sở dữ liệu:** schema v2 thêm cấu hình trả lời nhanh mã hóa; giữ nguyên thông báo, bản nháp và ghi chú đã lưu từ v0.1.0.

## Tính năng sẵn có được giữ lại

- **Notification Vault:** nhận thông báo từ hai package TikTok được cấu hình (`com.zhiliaoapp.musically`, `com.ss.android.ugc.trill`).
- **Private Reading:** đọc lại nội dung đã lưu trong Companion.
- **Private Drafts:** tạo/sửa/xóa bản nháp; sao chép/chia sẻ văn bản.
- **Local Presentation Editor:** lưu một bản hiển thị đã chỉnh và ghi chú riêng; văn bản gốc không bị thay đổi.
- **Encryption at rest:** nội dung thông báo, bản nháp và cấu hình trả lời nhanh được mã hóa AES-GCM; khóa nằm trong Android Keystore.
- **No network permission:** manifest không khai báo `INTERNET`; dữ liệu của ứng dụng không được gửi đến máy chủ dự án.

## Giới hạn quan trọng

1. **Banner không sửa trực tiếp hồ sơ TikTok.** Banner Studio tạo một ảnh độc lập để đăng/gửi; người khác sẽ thấy sau khi bạn chia sẻ ảnh đó. Tài liệu trợ giúp hồ sơ TikTok hiện mô tả luồng đổi ảnh/video hồ sơ, không phải một API đặt banner của ứng dụng bên thứ ba.
2. **Không tự gửi tin nhắn âm thầm.** Tính năng Chuỗi không tự động gửi tin nhắn. Nó chỉ tái sử dụng hành động trả lời nhanh mà chính thông báo TikTok cung cấp. Nếu TikTok không đưa `RemoteInput` vào thông báo, nút không xuất hiện. Người dùng vẫn phải nhấn và xác nhận gửi. Mỗi người gửi chỉ nhận tối đa một gợi ý trong 30 phút để tránh thông báo lặp.
3. **Không điều khiển giao diện riêng của TikTok.** Các nút mở TikTok chỉ khởi chạy ứng dụng; sao chép/chia sẻ không tự chọn người nhận hoặc gửi tin nhắn.
4. **Không sửa dữ liệu máy chủ hoặc gói tin.** Companion không chặn lưu lượng, giả mạo phản hồi, thay đổi lượt thích/người theo dõi/trạng thái chuỗi, hoặc thay đổi dữ liệu hiển thị trên máy chủ TikTok. Bản hiển thị đã chỉnh trong kho vẫn là bản riêng trên thiết bị.
5. Android chỉ chuyển cho ứng dụng nội dung mà TikTok thực sự đưa vào thông báo. Nếu xem trước bị ẩn, thông báo không có văn bản, TikTok không phát thông báo, hoặc nội dung bị thu hồi trước khi nhận, ứng dụng không thể phục hồi nội dung đó.
6. Notification Listener có thể thấy những loại thông báo TikTok ngoài DM. V0.2.0 không tuyên bố phân loại DM/chuỗi chính xác tuyệt đối; điều kiện từ khóa chỉ là bộ lọc đơn giản trên phần văn bản nhìn thấy.

## Quyền cần bật trên điện thoại

1. Cài ứng dụng và mở TikTok Companion.
2. Vào **Kho lưu trữ** → **Bật quyền đọc thông báo**.
3. Trong trang Android **Notification access / Quyền truy cập thông báo**, bật TikTok Companion và xác nhận.
4. Để dùng gợi ý trả lời chuỗi trên Android 13 trở lên, mở tab **Chuỗi** và cấp quyền thông báo cho Companion.
5. Đảm bảo thông báo TikTok đang bật và nội dung xem trước thông báo được phép hiển thị.

Quyền Notification Access rất nhạy cảm: Android cho phép listener đọc nội dung thông báo từ những package mà listener chấp nhận. Chỉ cài APK từ nguồn bạn tin cậy.

## Build APK

### GitHub Actions

Project có workflow `.github/workflows/build-apk.yml`. Đưa toàn bộ nội dung thư mục project lên root của GitHub repository, commit lên nhánh `main`, sau đó mở **Actions**. Workflow sẽ tạo debug APK và lưu artifact `TikTokCompanion-debug-apk`.

### Build trên Windows

Cần JDK 17, Android SDK Platform 35, Build Tools 35.0.0 và kết nối Internet cho lần tải Gradle/plugin đầu tiên. Chạy `build-debug.ps1` hoặc `build-debug.bat`; APK dự kiến ở `app/build/outputs/apk/debug/app-debug.apk`.

### Cấu hình build

- Android Gradle Plugin: 8.7.3
- Kotlin plugin: 2.0.21
- Gradle: 8.9
- JDK: 17
- compileSdk / targetSdk: 35
- minSdk: 26 (Android 8.0)
- versionCode: 3
- versionName: 0.2.1

Bản build mặc định là **debug APK**, không phải bản release được ký bằng khóa phát hành riêng.

## Kiểm thử thủ công

- Cài v0.2.1 đè lên bản 0.2.0 hoặc v0.1.0 (giữ cùng application ID trên bản release/debug tương ứng); xác nhận dữ liệu kho và bản nháp cũ còn nguyên.
- Bật Notification Access, gửi thông báo TikTok mới và xác nhận nội dung xuất hiện trong Vault.
- Gõ từ khóa tìm kiếm; xác nhận kết quả lọc theo người gửi/nội dung/ghi chú.
- Tạo banner với từng bảng màu; xuất PNG; chọn một ứng dụng nhận ảnh từ bảng chia sẻ và xác nhận ảnh mở được.
- Bật gợi ý chuỗi với từ khóa mà một thông báo thử nghiệm chứa. Kiểm tra nút chỉ xuất hiện nếu thông báo TikTok có sẵn hành động trả lời nhanh `RemoteInput`; bấm thử và xác nhận Android/TikTok mở giao diện nhập nội dung trước khi gửi.
- Tắt gợi ý chuỗi; xác nhận không còn thông báo Companion được tạo cho những thông báo khớp.
- Tạo/sửa/sao chép/chia sẻ/xóa bản nháp.
- Chỉnh bản hiển thị và ghi chú; xác nhận văn bản gốc không đổi.
- Xác nhận app hoạt động khi không có kết nối Internet.

## Cấu trúc chính

```text
app/src/main/
  AndroidManifest.xml
  java/com/taki/tiktokcompanion/
    MainActivity.kt
    banner/
      BannerRenderer.kt
      BannerFileProvider.kt
    data/
      CryptoBox.kt
      Records.kt
      VaultDb.kt
    notify/
      TikTokNotificationListener.kt
  res/values/
```


### v0.2.1 build-fix

This maintenance release fixes Kotlin compilation errors in the view setup and quick-reply notification action builder. The GitHub Actions workflow remains the same; run **Build TikTok Companion APK** again after replacing the repository contents with this archive.
