package com.subwhitelist;

import android.app.Application;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/**
 * 模块自身进程的 Application：注册 LSPosed 服务监听，拿到可写的远程配置通道。
 * 框架通过 service AAR 自动合并的 XposedProvider 把 binder 送进来。
 */
public class App extends Application implements XposedServiceHelper.OnServiceListener {

    public interface ServiceListener {
        void onServiceStateChanged(XposedService service);
    }

    private static volatile XposedService sService;
    private static final Set<ServiceListener> sListeners = new CopyOnWriteArraySet<>();

    public static XposedService getService() {
        return sService;
    }

    public static void addServiceListener(ServiceListener listener) {
        sListeners.add(listener);
        XposedService svc = sService;
        if (svc != null) {
            listener.onServiceStateChanged(svc);
        }
    }

    public static void removeServiceListener(ServiceListener listener) {
        sListeners.remove(listener);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(this);
    }

    @Override
    public void onServiceBind(XposedService service) {
        sService = service;
        for (ServiceListener l : sListeners) {
            l.onServiceStateChanged(service);
        }
    }

    @Override
    public void onServiceDied(XposedService service) {
        sService = null;
        for (ServiceListener l : sListeners) {
            l.onServiceStateChanged(null);
        }
    }
}
