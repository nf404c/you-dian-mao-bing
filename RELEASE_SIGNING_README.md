# 正式签名与发布说明

`com.fuellog.app` 的升级包必须始终使用同一套正式签名证书；证书丢失或更换后，已安装的正式版本将无法原地升级。

- Release signing credentials are intentionally not included in this repository.
- 私有签名证书和包含密码的本地配置均不得提交、发送到公开渠道或放入 APK。
- 本地配置存在时可执行 `./gradlew assembleRelease`；公开源码可直接执行 `./gradlew assembleDebug`。
- 正式 APK 应作为 GitHub Release 附件分发，不长期提交到 Git 仓库。

首次全新安装必须保持空白：不预置车辆、补能记录或数据库。升级现有安装时不得清空已有数据；请仅在模拟器、备用设备或其他隔离环境验证首次安装流程。
