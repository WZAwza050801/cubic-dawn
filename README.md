# Cubic Dawn / 方块黎明

仓库：[github.com/WZAwza050801/cubic-dawn](https://github.com/WZAwza050801/cubic-dawn)

做一个三维沙盒，世界的底座由方块与像素构成。

从最小可运行程序开始，由真实需求、Bug 和性能问题推动架构演化。

启动：`.\gradlew.bat run`

无窗口验收：`.\gradlew.bat verifyMath`

当前可以在已有岛上行走、跳跃、选中方块、删除和放置，并保存玩家与方块。进度见 `docs/implementation-progress.md`。

需要安装 **JDK 21**（不是仅 JRE）。Windows 使用 `./gradlew.bat run`，Linux/macOS 使用 `./gradlew run`。原生库自动选择平台；macOS 自动使用首线程启动。

完整无窗口检查：`./gradlew check`（Windows：`./gradlew.bat check`）。
使用临时存档启动：`./gradlew run -PworldFile=build/smoke-world.txt -Pframes=8`。

移动现在使用 60 Hz 固定模拟步，暂停/失焦会清空时间积累，长卡顿最多追赶 3 步。背包、合成等仍在后续计划中。


按 **8** 选择沙子：放置后悬空会逐格下落，挖掉支撑会继续落下。暂停时沙子停止；F5 保存或正常退出后，下次启动会恢复待执行任务。新版存档 v5 兼容读取 v3/v4，保存时自动升级。
