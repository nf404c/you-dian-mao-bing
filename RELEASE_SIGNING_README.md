# 油猫饼正式签名与发布

正式签名资产用于 `com.fuellog.app` 的所有后续升级包。请永久保留并备份 `release-signing/youmaobing-release.jks`；丢失该文件或其密码后，已安装的正式版本将无法原地升级。

- Keystore：`release-signing/youmaobing-release.jks`
- Alias：`youmaobing_release`
- 私密本机配置：`.release-signing.properties`（已被 `.gitignore` 排除，包含密码；请保存到密码管理器或加密备份）
- 发布构建：`JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew assembleRelease`
- 分发文件：`release/油猫饼-v<versionName>-release.apk`

首次全新安装必须保持空白：不预置车辆、加油记录或数据库。升级现有安装时不得清空已有数据；请仅在模拟器、备用设备或其他隔离环境验证首次安装流程。

不要把 keystore、`.release-signing.properties` 或任何明文密码提交到 Git、发送到公开渠道或放入 APK。每次更新都必须继续使用同一个 keystore 和 alias，并提高 `versionCode`。
