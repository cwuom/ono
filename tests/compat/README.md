# QQ 兼容逻辑回归检查

在仓库根目录执行：

```powershell
python tests/compat/test_target_manager.py
python tests/compat/test_sticker_entry.py
python tests/compat/test_markdown_guard.py
```

需要 Python 3、JDK 17 或更新版本，以及 Kotlin 命令行编译器。Java 优先使用 `JAVA_HOME` 或 PATH，Kotlin 优先使用 PATH；Windows 也支持 Android Studio 默认安装目录。

测试编译实际的 TargetManager、DexKitExecutor、MarkdownGuard 和表情入口方法，使用轻量宿主桩覆盖缓存迁移、重载恢复、扫描失败、嵌套图标、点击事件与 Markdown 新旧路径。生成文件保存在已忽略的 `generated/`。

这些检查不执行真实 DexKit 查询，不操作 Android 设备，不能替代目标 QQ APK 的静态核对、完整应用构建和宿主运行验证。
