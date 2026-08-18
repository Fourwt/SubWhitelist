package com.subwhitelist;

import android.content.SharedPreferences;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import io.github.libxposed.api.XposedModule;

/**
 * 模块入口类。现代 libxposed API 102 入口：继承 {@link XposedModule}，
 * 由 META-INF/xposed/java_init.list 声明，框架自动调用 attachFramework。
 *
 * 目标 APK：com.xiaomi.subscreencenter（SubScreenCenter 背屏）
 *
 * 主 Hook：A2.a.c(String) -> boolean  —— 背屏智能助手音乐卡片的"是否音乐包"分类器，
 *          链路①所有关卡（第一关 + A2.g.k 内部 + 焦点回调）都先调它。
 * 辅 Hook：A2.a.b() -> HashSet<String> —— 喂给 MAML MusicController 的白名单数据源，
 *          覆盖链路② ActiveAudioSessionManager 的 List.contains 过滤。
 *
 * 两者都遵循：仅对用户自定义包名返回 true / 追加包名，其余严格返回 originalResult。
 */
public class ModuleMain extends XposedModule {

    private static final String TAG = "SubScreenWhitelist";

    /** 远程配置 group，需与模块 UI 侧（service.getRemotePreferences）保持一致 */
    private static final String PREFS_GROUP = "whitelist";
    private static final String PREFS_KEY_PACKAGES = "packages";
    private static final String PREFS_KEY_DEBUG = "debug";

    private static final String TARGET_PACKAGE = "com.xiaomi.subscreencenter";
    private static final String TARGET_CLASS = "A2.a";

    private volatile Set<String> mWhitelist = new HashSet<>();
    private volatile boolean mDebug = false;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "Hook initialized");
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        try {
            loadConfig();
            installHooks(param.getClassLoader());
        } catch (Throwable t) {
            // 任何异常都不允许拖垮目标进程
            log(Log.ERROR, TAG, "Class loading failed", t);
        }
    }

    @SuppressWarnings("unchecked")
    private void installHooks(ClassLoader classLoader) throws Throwable {
        Class<?> clazz = classLoader.loadClass(TARGET_CLASS); // A2.a

        // 主 Hook：boolean c(String)
        Method methodC = clazz.getDeclaredMethod("c", String.class);
        deoptimize(methodC);
        hook(methodC)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    String pkg = (String) chain.getArg(0);
                    boolean original = (Boolean) chain.proceed();
                    if (pkg != null && mWhitelist.contains(pkg)) {
                        if (mDebug) {
                            log(Log.INFO, TAG, "package=" + pkg + " original=" + original
                                    + " custom=true final=true");
                        }
                        return true;
                    }
                    return original;
                });
        log(Log.INFO, TAG, "Target method found: " + TARGET_CLASS + ".c(String)");

        // 辅 Hook：HashSet b()
        Method methodB = clazz.getDeclaredMethod("b");
        deoptimize(methodB);
        hook(methodB)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (result instanceof HashSet) {
                        ((HashSet<String>) result).addAll(mWhitelist);
                    }
                    return result;
                });
        log(Log.INFO, TAG, "Target method found: " + TARGET_CLASS + ".b()");
    }

    private void loadConfig() {
        try {
            SharedPreferences prefs = getRemotePreferences(PREFS_GROUP);
            Set<String> set = prefs.getStringSet(PREFS_KEY_PACKAGES, null);
            Set<String> whitelist = new HashSet<>();
            if (set != null) {
                for (String s : set) {
                    if (s != null && !s.trim().isEmpty()) {
                        whitelist.add(s.trim());
                    }
                }
            }
            mWhitelist = whitelist;
            mDebug = prefs.getBoolean(PREFS_KEY_DEBUG, false);
            if (mDebug) {
                log(Log.INFO, TAG, "Loaded whitelist: " + mWhitelist);
            }
        } catch (Throwable t) {
            // 远程配置读取失败不应影响 Hook 本身；以空白名单继续
            log(Log.WARN, TAG, "Failed to load remote preferences", t);
        }
    }
}
