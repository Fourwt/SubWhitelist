# SubWhitelist

给小米背屏的音乐白名单加自定义包名的 LSPosed 模块。

背屏默认只认几个内置音乐 App，别的播放器放歌不会在背屏弹音乐卡片。这个模块 Hook 了背屏判断「是不是音乐应用」的逻辑，让你指定的包名也能通过，其它行为不变。

## 用法

1. 安装 APK，在 LSPosed 里启用，作用域选 `com.xiaomi.subscreencenter`
2. 打开 App，添加包名（比如 `com.example.music`）
3. 点右上角刷新图标重启背屏（需要 Root）

## 构建

```bash
./gradlew assembleDebug
```

## 许可

MIT
