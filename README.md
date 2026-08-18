# SubWhitelist

给小米背屏（SubScreenCenter）的音乐白名单加自定义包名的 LSPosed 模块。

背屏默认只认几个内置音乐 App（小米音乐、QQ 音乐、网易云、酷狗那些），其它播放器放歌不会在背屏弹音乐卡片。这个模块 Hook 一下它判断「是不是音乐应用」的地方，让你指定的包名也能通过判断，其它逻辑一律不动。

## 原理

反编译 `com.xiaomi.subscreencenter` 看到，背屏判断音乐包的地方是混淆类 `A2.a` 的两个静态方法：

- `boolean c(String)` —— 判断某个包是不是音乐应用，背屏音乐卡片的所有判断都先过它
- `HashSet b()` —— 生成喂给 MAML 音乐控件的白名单

模块只做两件事：你在 App 里加的包名，`c()` 对它返回 true，`b()` 把它塞进结果。其余包该怎么判还怎么判。

## 编译

Android Studio 直接打开 Build 就行，首次同步会自动装 AGP 9.2.1 和 android-37 平台。或者命令行：

```bash
./gradlew assembleDebug
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`。

## 使用

1. 装 APK，LSPosed 里启用，作用域勾 `com.xiaomi.subscreencenter`（已锁定）。
2. 打开 App，填包名（比如 `com.example.music`），添加。
3. 点右上角刷新图标重启背屏（走 `su -c am force-stop`，KernelSU 里给下 root 权限）。

右上角三个点是 Debug 日志开关。列表点一下复制包名，右边垃圾桶删掉（带撤销）。

## 验证

```bash
adb logcat -s SubScreenWhitelist
```

看到 `Target method found: A2.a.c(String)` 就是 Hook 上了。命中自定义包时会打：

```
package=com.example.music original=false custom=true final=true
```

## 其它

- 改完白名单要重启背屏才生效。
- 不碰 APK、不碰 `subPackages.db`、不 Hook 全局 PackageManager，只在背屏进程里改返回值。
- 配置存在 LSPosed 远程 SharedPreferences 里，App 写、Hook 进程读。
