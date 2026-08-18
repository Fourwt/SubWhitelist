# SubWhitelist

一个基于 **libxposed 现代 API 102** 的 LSPosed 模块：让用户自定义的 Android 包名通过
Xiaomi/HyperOS **SubScreenCenter（背屏）** 的音乐白名单判断，同时保持其它所有原始行为不变。

- 目标应用：`com.xiaomi.subscreencenter`
- 框架要求：LSPosed ≥ 2.1.1（libxposed API 102）
- 依赖：`io.github.libxposed:api:102.0.0`（compileOnly，不打包）+ `io.github.libxposed:service:102.0.0`（implementation，仅模块 UI 用）

## 一、Hook 点（已通过 APK 逆向确认）

目标 APK 只有一个 `classes.dex`，白名单判断集中在混淆类 `A2.a`：

| Hook | 方法 | 签名 | 作用 |
|---|---|---|---|
| 主 | `A2.a.c` | `boolean c(String)` | 背屏智能助手音乐卡片"是否音乐包"分类器（链路①所有关卡都先调它） |
| 辅 | `A2.a.b` | `HashSet<String> b()` | 喂给 MAML `MusicController.sMusicPackages` 的白名单数据源（覆盖链路② `ActiveAudioSessionManager` 过滤） |

两者都遵循：**仅对用户自定义包名返回 true / 追加包名，其余严格返回 originalResult**。

## 二、目录结构

```
app/src/main/
├── AndroidManifest.xml                      # Application + MainActivity（XposedProvider 由 service AAR 自动合并）
├── java/com/subwhitelist/
│   ├── ModuleMain.java                      # 模块入口（继承 XposedModule），Hook 逻辑
│   ├── App.java                             # 注册 LSPosed 服务监听，拿到可写远程配置
│   └── MainActivity.java                    # 白名单管理 UI
├── res/...                                  # 布局 / 字符串
└── resources/META-INF/xposed/
    ├── java_init.list                       # 入口：com.subwhitelist.ModuleMain
    ├── scope.list                           # 作用域：com.xiaomi.subscreencenter
    └── module.prop                          # minApiVersion=101 / targetApiVersion=102 / staticScope=true
```

## 三、构建

### Android Studio（推荐）
直接 `File → Open` 打开本目录，等待 Gradle 同步后 `Build → Build APK(s)`。
首次同步会自动安装 `platforms;android-37`（AGP 9.2.1 要求 compileSdk 37）。

### 命令行
```bash
# Windows（本机已配 JAVA_HOME = Android Studio 自带 JBR）
./gradlew assembleDebug
```
产物：`app/build/outputs/apk/debug/app-debug.apk`

> 依赖版本：AGP `9.2.1`、Gradle `9.5.1`、compileSdk `37`、minSdk `26`、targetSdk `34`。

## 四、安装与启用

1. 在 LSPosed 管理器里安装生成的 APK。
2. 启用本模块，作用域勾选 **`com.xiaomi.subscreencenter`**（`scope.list` 已声明，`staticScope=true` 锁定）。
3. 打开模块 App（Material UI），添加自定义包名（如 `com.example.music`）。
4. 点右上角 **刷新图标**（重启背屏）——需在 KernelSU 中授予本模块 Root 权限；或手动 Force Stop `SubScreenCenter` / 重启手机，使 Hook 生效。

> UI 说明：右上角刷新图标 = 重启背屏；右上角三点溢出菜单 = Debug 日志开关（默认隐藏）；列表项点击 = 复制包名，右侧删除图标 = 移除该包（带「撤销」）。

## 五、验证

```bash
adb logcat -s SubScreenWhitelist
```

预期日志：

```
Hook initialized
Target method found: A2.a.c(String)
Target method found: A2.a.b()
package=com.example.music original=false custom=true final=true
```

- 其它包名不会刷日志（只在 Debug 开启时打印命中自定义白名单的包）。
- 若看不到 `Target method found`，说明作用域未生效或目标进程未重启。

## 六、配置存储

- UI 进程通过 `XposedServiceHelper` + `XposedService.getRemotePreferences("whitelist")` **写入** LSPosed 远程配置。
- Hook 进程通过 `getRemotePreferences("whitelist")` **只读**。
- 键：`packages`（StringSet，白名单包名）、`debug`（boolean，日志开关）。

## 七、注意事项

- 修改白名单后需**重启目标进程**才会重新加载配置；可直接点 App 内「重启背屏」按钮（等价于 `su -c "am force-stop com.xiaomi.subscreencenter"`）。
- 模块不修改 `subscreencenter.apk`、不修改 `subPackages.db`、不 Hook 全局 PackageManager，仅在目标进程内做返回值包装。
- 若目标方法被 ART 内联，已调用 `deoptimize()` 兜底；Hook 全程 `ExceptionMode.PROTECTIVE`，异常不会拖垮目标进程。
