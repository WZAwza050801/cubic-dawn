# 木材与工具窗口验收（Linux）

需要 JDK 21、项目的 LWJGL 依赖，以及系统已有的 Xorg dummy 驱动、Mesa OpenGL、Python 3、libX11/libXtst、xwininfo 和 ImageMagick import。此脚本使用独立显示 `:98`，不用共享桌面或登录会话。

从仓库根目录执行。确保 :98 空闲；夹具会覆盖 **build/tools-smoke.txt**，不要把它当作自己的存档。

1. 编译并准备确定的世界和背包：

   ```sh
   ./gradlew classes
   java --class-path build/classes/java/main tools/smoke/ToolsFixture.java
   ```

2. 在另一个终端启动独立测试显示（保持运行）：

   ```sh
   /usr/lib/xorg/Xorg :98 -config "$PWD/tools/smoke/xorg-dummy.conf" -noreset -nolisten tcp -novtswitch -sharevts -logfile "$PWD/build/xorg-smoke.log"
   ```

3. 启动游戏并等待日志打印 `Aim 0 1 1`：

   ```sh
   DISPLAY=:98 ./gradlew run -PworldFile=build/tools-smoke.txt
   ```

4. 从第三个终端运行键鼠验收：

   ```sh
   python3 tools/smoke/wood_tools.py
   ```

   会实际按键、操作背包并正常退出游戏，截图写到 build/ui-smoke。检查背包取消采集且返回后须松开重按、采集原木、木板/木棍/工作台配方、无工作台拒绝木镐配方、放置工作台、合成木镐、点击/交换快捷栏、使用木镐持续采集，以及物品未丢失。脚本失败会保留窗口以便查看，断言错误会返回非零退出码。

5. 重启检查恢复（应看到 81 个方块，选中 wood pickaxe）：

   ```sh
   DISPLAY=:98 ./gradlew run -PworldFile=build/tools-smoke.txt -Pframes=8
   ```

   最终背包计数应为原木 6、泥土 4、木板 3、木棍 2、木镐 1、工作台 1、石 1。完成后用启动终端的 Ctrl+C 停止独立 Xorg。

云端验证额外使用了环境现有网络代理及系统 CA 信任库；这些设置未写入仓库。Windows/macOS 仍使用普通启动命令人工验收，本脚本仅支持上述 Linux 环境。

## 熔炉与箱子验收

沿用上述 Linux 依赖和独立 `:98` 显示。从仓库根目录运行，夹具仅覆盖 build/production-smoke.txt。

1. `./gradlew classes`，然后 `java --class-path build/classes/java/main tools/smoke/ProductionFixture.java`。
2. 启动 `DISPLAY=:98 ./gradlew run -PworldFile=build/production-smoke.txt`，等待打印 `Hotbar: sand`。
3. 另一终端执行 `python3 tools/smoke/production_first.py`。脚本会打开熔炉、加入两个沙和一个木板，确认菜单暂停，运行部分加工后保存并正常退出。
4. 再启动同一游戏命令，等待 `Hotbar:`；执行 `python3 tools/smoke/production_second.py`。会继续烧完两个玻璃，取出后横移到箱子，存入耐久 31 的木镐，确认非空箱子不能拆除，再取回工具、存入两个玻璃并退出。
5. `DISPLAY=:98 ./gradlew run -PworldFile=build/production-smoke.txt -Pframes=8` 验证恢复。应为 83 方块，箱内玻璃 2，背包木镐 1（耐久 31）、木板 1、原木 1，熔炉为空且余燃归零。

截图写入 build/ui-smoke/production-*.png。运行结束后停止独立 Xorg。菜单暂停是预期规则，不要一直开着熔炉菜单等待烧制。两阶段脚本依次使用同一份世界，第二阶段不要重新运行夹具。
