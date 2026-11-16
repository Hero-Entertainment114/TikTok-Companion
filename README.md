# TikTok Companion v0.1.0

Ứng dụng Android native (Kotlin) để lưu văn bản hiển thị trong thông báo TikTok, đọc lại trong kho cục bộ, tạo bản nháp, và ghi chú/chỉnh sửa **bản hiển thị riêng**.

## Có gì trong bản này

- **Notification Vault:** nhận thông báo mới từ hai package TikTok được cấu hình trong `TikTokNotificationListener` (`com.zhiliaoapp.musically`, `com.ss.android.ugc.trill`).
- **Private Reading:** xem lại nội dung đã lưu trong ứng dụng Companion mà không mở cuộc trò chuyện TikTok.
- **Private Drafts:** tạo/sửa/xóa bản nháp; sao chép hoặc chia sẻ văn bản để người dùng tự dán vào TikTok.
- **Local Presentation Editor:** lưu một bản hiển thị đã chỉnh và ghi chú riêng, đồng thời giữ nguyên văn bản gốc.
- **Encryption at rest:** các trường nội dung được mã hóa AES-GCM; khóa AES được giữ trong Android Keystore.
- **No network permission:** manifest không khai báo `INTERNET`; dữ liệu không được gửi đến máy chủ của dự án.

## Giới hạn cố ý

1. Android chỉ chuyển cho ứng dụng nội dung mà TikTok thực sự đưa vào thông báo. Nếu xem trước thông báo bị tắt/ẩn, thông báo không có văn bản, TikTok không phát thông báo, hoặc tin nhắn bị thu hồi trước khi nội dung được nhận, ứng dụng không thể phục hồi nội dung đó.
2. Notification Listener có thể thấy các loại thông báo TikTok khác ngoài DM. V0.1.0 lưu văn bản thông báo hiển thị; không tuyên bố phân loại DM chính xác tuyệt đối.
3. Đọc lại trong Companion không mở TikTok, nên thao tác này không tự phát sinh trạng thái đã xem bên trong TikTok. Ứng dụng không điều khiển trạng thái đọc của TikTok và không bảo đảm các thao tác khác trong TikTok sẽ không gửi trạng thái đó.
4. Chỉnh sửa là bản hiển thị riêng trong Companion. Không có thay đổi nào được gửi đến máy chủ TikTok hoặc thiết bị người khác.
5. Bản nháp được lưu ngoại tuyến. Sao chép/chia sẻ không tự gửi tin nhắn.

## Quyền cần bật trên điện thoại

1. Cài ứng dụng.
2. Mở TikTok Companion → **Kho lưu trữ** → **Bật quyền đọc thông báo**.
3. Trong trang Android **Notification access / Quyền truy cập thông báo**, bật TikTok Companion và xác nhận.
4. Đảm bảo thông báo TikTok đang bật và nội dung xem trước thông báo được phép hiển thị. Gửi thử một tin nhắn mới để kiểm tra.

Quyền Notification Access là quyền nhạy cảm: Android sẽ cho phép ứng dụng đọc nội dung thông báo của các package mà listener chấp nhận. Mã nguồn hiện chỉ chấp nhận các package TikTok được liệt kê. Không bật quyền này cho APK không rõ nguồn gốc.

## Build

### Cách dễ nhất: GitHub Actions (không cần Android Studio trên PC)

Project có sẵn workflow tại `.github/workflows/build-apk.yml`. Đưa **toàn bộ nội dung** thư mục project lên root của một GitHub repository, commit lên nhánh `main`, rồi mở tab Actions. Workflow sẽ tự build APK trên máy chủ GitHub và tải kết quả dưới mục Artifacts. Hướng dẫn từng bước nằm trong [BUILD-GITHUB.md](BUILD-GITHUB.md).

### Build trực tiếp trên PC bằng command line

Không cần Android Studio, nhưng cần JDK 17, Android SDK Command-line Tools, Android SDK Platform 35, Build Tools 35.0.0 và Gradle 8.9. Sau khi cài và cấu hình `ANDROID_HOME`/`ANDROID_SDK_ROOT`, chạy:

```powershell
gradle :app:assembleDebug
```

### Yêu cầu nếu dùng Android Studio

- Android Studio phiên bản có hỗ trợ Android Gradle Plugin 8.7.3.
- JDK 17 trở lên.
- Android SDK Platform 35 và Build Tools tương ứng.
- Gradle 8.9 (Android Studio có thể tải/cài khi đồng bộ project).

### Build bằng Android Studio

1. Mở thư mục `TikTokCompanion` bằng Android Studio.
2. Chọn **Sync Project with Gradle Files**.
3. Hoặc chạy `build-debug.bat` trên Windows. Script sẽ tìm Android SDK, tải Gradle 8.9 nếu chưa có, tạo Gradle Wrapper chuẩn và build APK debug.
4. APK debug dự kiến ở `app/build/outputs/apk/debug/app-debug.apk`.

Có thể build thủ công trong terminal khi đã cài Gradle và Android SDK:

```bash
gradle :app:assembleDebug
```

Project dùng Android framework APIs, không cần thư viện UI bên thứ ba. Môi trường tạo project không có Android SDK/Gradle và không truy cập được máy chủ tải dependency, vì vậy APK chưa được biên dịch hoặc chạy trên thiết bị tại đây. Script build cần kết nối Internet ở lần tải Gradle/plugin đầu tiên. Cần build và smoke-test trên Android Studio/thiết bị thật trước khi phát hành.

## Cấu trúc

```text
app/src/main/
  AndroidManifest.xml
  java/com/taki/tiktokcompanion/
    MainActivity.kt
    data/
      CryptoBox.kt
      Records.kt
      VaultDb.kt
    notify/
      TikTokNotificationListener.kt
  res/values/
    strings.xml
    styles.xml
```

## Kiểm thử thủ công tối thiểu

- Cài bản debug, mở quyền Notification Access, xác nhận trạng thái chuyển thành đã bật.
- Gửi một thông báo TikTok có nội dung xem trước; xác nhận nó xuất hiện trong Kho lưu trữ.
- Tắt xem trước nội dung thông báo; xác nhận ứng dụng không thể lưu phần văn bản bị ẩn.
- Mở một mục, chỉnh bản hiển thị và thêm ghi chú; đóng/mở lại để xác nhận dữ liệu còn nguyên và văn bản gốc không đổi.
- Tạo bản nháp, sửa, sao chép, chia sẻ và xóa.
- Tắt Notification Access; xác nhận màn hình báo chưa bật quyền.
- Xóa kho thông báo; xác nhận bản nháp không bị xóa.
- Xác nhận ứng dụng hoạt động khi không có kết nối Internet.
