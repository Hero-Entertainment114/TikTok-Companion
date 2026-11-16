# Build APK bằng GitHub Actions (không cần Android Studio)

Workflow đã được thêm tại `.github/workflows/build-apk.yml`. GitHub sẽ build ứng dụng trên runner Linux; máy tính cá nhân không cần Android Studio, JDK, Gradle hay Android SDK.

## Cách làm

1. Đăng nhập GitHub và tạo một repository mới. Repository public là lựa chọn thuận tiện cho dự án mã nguồn mở.
2. Giải nén gói source. **Mở thư mục `TikTokCompanion` bên trong và tải toàn bộ nội dung của thư mục đó lên root repository**. Sau khi tải lên, cấu trúc ở root phải có dạng:

   ```text
   .github/workflows/build-apk.yml
   app/
   build.gradle.kts
   settings.gradle.kts
   gradle.properties
   README.md
   ```

   Không để `.github` nằm sâu trong một thư mục `TikTokCompanion/` con, nếu không GitHub sẽ không phát hiện workflow ở đúng vị trí.
3. Commit các tệp lên nhánh `main`.
4. Mở tab **Actions** của repository. Workflow **Build TikTok Companion APK** sẽ tự chạy sau lần push đầu tiên.
5. Khi job có dấu tích xanh, mở lần chạy đó, kéo xuống **Artifacts**, chọn `TikTokCompanion-debug-apk` để tải ZIP chứa APK.
6. Giải nén ZIP artifact, lấy `app-debug.apk`, chuyển sang điện thoại Android và cài đặt. Có thể cần bật quyền cho phép cài ứng dụng từ nguồn này trong Android.

## Build thủ công

Trong tab **Actions**, chọn workflow **Build TikTok Companion APK** → **Run workflow** → chọn nhánh `main` → **Run workflow**.

## Cấu hình build

- JDK: 17
- Gradle: 8.9
- Android compile SDK: 35
- Android Build Tools: 35.0.0
- Gradle task: `:app:assembleDebug`
- APK: `app/build/outputs/apk/debug/app-debug.apk`

Workflow chỉ tạo debug APK để cài thử. Đây chưa phải bản release đã ký bằng khóa phát hành riêng. Không chia sẻ keystore hoặc mật khẩu ký ứng dụng trong mã nguồn công khai.

## Lưu ý về quyền riêng tư

GitHub Actions cần tải mã nguồn lên GitHub để build. Source hiện không cần API key hay secret để build. Ứng dụng không khai báo quyền Internet trong manifest; việc build trên GitHub không có nghĩa nội dung thông báo trên điện thoại được gửi lên GitHub.

GitHub Actions chạy trên máy chủ cloud và cần kết nối Internet. Lượt build có thể mất vài phút tùy lúc tải Gradle và Android SDK. Quota/giới hạn phút chạy phụ thuộc loại repository và gói GitHub hiện hành.
