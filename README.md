Android FTP Server (Jetpack Compose + Apache FtpServer)
This is a demo. Originally designed for transfer jpg/raw images from sony camera that supports ftp features.

An Android-based local FTP server app built with Jetpack Compose and Apache FtpServer,
allowing devices such as digital cameras or PCs to connect via Wi-Fi or hotspot for file transfer.
--------------------------------------------------------------------------------------------------------------
Features

1. Run FTP server directly on your Android phone.
    Supports connection through Wi-Fi or mobile hotspot.
    Compatible with cameras (e.g. Sony) or any FTP client.

2. Jetpack Compose UI.
    Modern Material 3 interface with live connection info.
    User-configurable port, username, password.
    Toggle allow anonymous access.

3. Persistent Foreground Notification.
    Always visible (even when the app is in background).
    Shows IP, user info, and server status.
    Allows starting/stopping FTP directly from the notification.

4. File Event Callback.
    Notifies when a file starts and finishes uploading in main page.
    Designed for large RAW image uploads from camera.
    This is a demo. Designed for future extension.

6. Fixed root directory.
    Default: /storage/emulated/0/Pictures/ftptest.
    Can be changed later to user-selected paths via SAF.

7. User Access Control.
    Custom username/password.
    Optional anonymous login.
   
8. Min SDK	24.
   Target SDK	34 (Android 14).

--------------------------------------------------------------------------------------------------------------
Usage

1. Clone this repo.
git clone https://github.com/<yourname>/android-ftp-server.git

2. Open in Android Studio.

3. Build & Run.
Connect an Android device (API 24+).
Click “Run App” ▶️

4. Set parameters in UI.
Port (default: 2121).
Username / Password.
Allow anonymous access (optional).

5. Start FTP.
Press “启动 FTP” (start FTP) button or use notification toggle.

6. Connect from another device.
Example:
ftp://192.168.x.x:2121
Username: user
Password: 1234

Or use “anonymous” if allowed

<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="29" />



