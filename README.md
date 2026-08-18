# SubWhitelist

给小米背屏音乐白名单加自定义包名的 LSPosed 模块，让任意指定的 App 能像内置音乐 App 一样被背屏识别。

## 原理

背屏判断音乐包的地方是混淆类 `A2.a` 的两个静态方法：

- `boolean c(String)` —— 判断某个包是不是音乐应用，背屏音乐卡片的所有判断都先过它
- `HashSet b()` —— 生成喂给 MAML 音乐控件的白名单

模块只做两件事：你在 App 里加的包名，`c()` 对它返回 true，`b()` 把它塞进结果。其余包该怎么判还怎么判。

## 用法

1. 安装 APK，在 LSPosed 里启用，作用域选 `com.xiaomi.subscreencenter`
2. 打开 App，添加包名（比如 `com.example.music`）
3. 点右上角刷新图标重启背屏（需要 Root）
