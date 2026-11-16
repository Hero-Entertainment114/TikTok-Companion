# Build TikTok Companion v0.2.0 bằng GitHub Actions

Workflow nằm tại `.github/workflows/build-apk.yml`. GitHub sẽ build ứng dụng trên runner Linux; máy tính cá nhân không cần cài Android Studio, JDK, Gradle hay Android SDK.

## Các bước

1. Tạo repository GitHub.
2. Giải nén gói source và tải **toàn bộ nội dung bên trong** thư mục project lên root repository. Không đặt `.github` sâu bên trong một thư mục con.
3. Commit các tệp lên nhánh `main`.
4. Mở tab **Actions** → workflow **Build TikTok Companion APK**.
5. Khi job hoàn tất với dấu tích xanh, mở lần chạy và tải artifact `TikTokCompanion-debug-apk`.
6. Giải nén artifact, lấy `app-debug.apk`, chuyển sang Android và cài đặt.

## Build thủ công trên Windows

Cần JDK 17 và Android SDK Platform 35 / Build Tools 35.0.0. Chạy `build-debug.ps1` hoặc `build-debug.bat` tại thư mục project. Lần build đầu có thể cần kết nối Internet để tải Gradle và plugin.

## Build config

- JDK 17; Gradle 8.9; Android Gradle Plugin 8.7.3; Kotlin 2.0.21.
- compileSdk 35; targetSdk 35; minSdk 26.
- versionCode 2; versionName 0.2.0.
- Gradle task: `:app:assembleDebug`.
- APK path: `app/build/outputs/apk/debug/app-debug.apk`.

Workflow chỉ tạo debug APK. Không commit keystore hoặc mật khẩu ký release lên repository.
