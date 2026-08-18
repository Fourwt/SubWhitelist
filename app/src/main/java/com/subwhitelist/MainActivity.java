package com.subwhitelist;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import io.github.libxposed.service.XposedService;

/**
 * 模块 UI：SubWhitelist
 * 通过 XposedService 写远程配置（group="whitelist"），Hook 进程只读。
 */
public class MainActivity extends Activity implements App.ServiceListener {

    private static final String PREFS_GROUP = "whitelist";
    private static final String PREFS_KEY_PACKAGES = "packages";
    private static final String PREFS_KEY_DEBUG = "debug";

    private static final String TARGET_PACKAGE = "com.xiaomi.subscreencenter";

    private static final Pattern PKG_PATTERN =
            Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$");

    private MaterialToolbar mToolbar;
    private EditText mPkgInput;
    private TextView mEmptyHint;
    private PackageAdapter mAdapter;
    private final List<String> mItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mToolbar = findViewById(R.id.toolbar);
        mPkgInput = findViewById(R.id.pkg_input);
        mEmptyHint = findViewById(R.id.empty_hint);
        MaterialButton addButton = findViewById(R.id.add_button);
        RecyclerView recyclerView = findViewById(R.id.recycler_view);

        mToolbar.inflateMenu(R.menu.main_menu);
        mToolbar.setOnMenuItemClickListener(this::onMenuItemClick);

        mAdapter = new PackageAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(mAdapter);

        addButton.setOnClickListener(v -> onAdd());

        refresh();
    }

    private boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_restart) {
            restartBackScreen();
            return true;
        } else if (id == R.id.action_debug) {
            boolean debug = !readDebug();
            writeDebug(debug);
            item.setChecked(debug);
            toast(debug ? "Debug 已开启" : "Debug 已关闭");
            return true;
        }
        return false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        App.addServiceListener(this);
    }

    @Override
    protected void onStop() {
        App.removeServiceListener(this);
        super.onStop();
    }

    @Override
    public void onServiceStateChanged(XposedService service) {
        runOnUiThread(this::refresh);
    }

    private XposedService service() {
        return App.getService();
    }

    private void refresh() {
        mItems.clear();
        mItems.addAll(readPackages());
        Collections.sort(mItems);
        mAdapter.notifyDataSetChanged();

        mEmptyHint.setVisibility(mItems.isEmpty() ? View.VISIBLE : View.GONE);

        MenuItem debugItem = mToolbar.getMenu().findItem(R.id.action_debug);
        if (debugItem != null) {
            debugItem.setChecked(readDebug());
        }
    }

    private Set<String> readPackages() {
        XposedService svc = service();
        if (svc == null) {
            return new HashSet<>();
        }
        try {
            SharedPreferences prefs = svc.getRemotePreferences(PREFS_GROUP);
            Set<String> set = prefs.getStringSet(PREFS_KEY_PACKAGES, null);
            return set == null ? new HashSet<>() : new HashSet<>(set);
        } catch (Throwable t) {
            return new HashSet<>();
        }
    }

    private boolean readDebug() {
        XposedService svc = service();
        if (svc == null) {
            return false;
        }
        try {
            return svc.getRemotePreferences(PREFS_GROUP).getBoolean(PREFS_KEY_DEBUG, false);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean writePackages(Set<String> packages) {
        XposedService svc = service();
        if (svc == null) {
            toast("LSPosed 服务未连接，无法保存");
            return false;
        }
        try {
            svc.getRemotePreferences(PREFS_GROUP)
                    .edit()
                    .putStringSet(PREFS_KEY_PACKAGES, packages)
                    .apply();
            return true;
        } catch (Throwable t) {
            toast("保存失败：" + t.getMessage());
            return false;
        }
    }

    private void writeDebug(boolean debug) {
        XposedService svc = service();
        if (svc == null) {
            return;
        }
        try {
            svc.getRemotePreferences(PREFS_GROUP)
                    .edit()
                    .putBoolean(PREFS_KEY_DEBUG, debug)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    private void onAdd() {
        String pkg = mPkgInput.getText().toString().trim();
        if (!PKG_PATTERN.matcher(pkg).matches()) {
            toast("包名格式不合法");
            return;
        }
        Set<String> packages = readPackages();
        if (packages.contains(pkg)) {
            toast("该包名已存在，未重复添加");
            return;
        }
        if (!isInstalled(pkg)) {
            toast("警告：未检测到已安装的应用 " + pkg + "，仍将添加");
        }
        packages.add(pkg);
        if (writePackages(packages)) {
            mPkgInput.setText("");
            refresh();
        }
    }

    private void onDeletePackage(String pkg) {
        Set<String> packages = readPackages();
        packages.remove(pkg);
        if (writePackages(packages)) {
            refresh();
            Snackbar.make(mToolbar, "已删除 " + pkg, Snackbar.LENGTH_LONG)
                    .setAction("撤销", v -> {
                        Set<String> restored = readPackages();
                        restored.add(pkg);
                        writePackages(restored);
                        refresh();
                    })
                    .show();
        }
    }

    private void copyToClipboard(String pkg) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("package", pkg));
        }
        toast("已复制 " + pkg);
    }

    /**
     * 通过 root 执行 am force-stop 杀掉 SubScreenCenter 进程。
     * 背屏是系统常驻进程，force-stop 后系统会自动重新拉起，从而重载白名单配置。
     */
    private void restartBackScreen() {
        toast("正在重启背屏…");
        new Thread(() -> {
            String output = "";
            boolean finished = false;
            int code = -1;
            try {
                Process p = new ProcessBuilder("su", "-c", "am force-stop " + TARGET_PACKAGE)
                        .redirectErrorStream(true)
                        .start();
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                }
                output = sb.toString().trim();
                finished = p.waitFor(15, TimeUnit.SECONDS);
                if (finished) {
                    code = p.exitValue();
                }
            } catch (Exception e) {
                output = e.getMessage();
            }
            final String finalOutput = output;
            final boolean finalFinished = finished;
            final int finalCode = code;
            runOnUiThread(() -> {
                if (finalFinished && finalCode == 0) {
                    toast("已重启背屏 SubScreenCenter");
                } else if (!finalFinished) {
                    toast("重启超时（可能未授予 Root 权限）");
                } else {
                    toast("重启失败 (code=" + finalCode + ")"
                            + (finalOutput == null || finalOutput.isEmpty() ? "" : ": " + finalOutput));
                }
            });
        }).start();
    }

    private boolean isInstalled(String pkg) {
        try {
            return getPackageManager().getPackageInfo(pkg, 0) != null;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private class PackageAdapter extends RecyclerView.Adapter<PackageAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_package, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            String pkg = mItems.get(position);
            holder.name.setText(pkg);
            holder.itemView.setOnClickListener(v -> copyToClipboard(pkg));
            holder.delete.setOnClickListener(v -> onDeletePackage(pkg));
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView name;
            final ImageButton delete;

            VH(View v) {
                super(v);
                name = v.findViewById(R.id.pkg_name);
                delete = v.findViewById(R.id.delete_icon);
            }
        }
    }
}
