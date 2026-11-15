package cn.kylebing.bbq10wubi;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import cn.kylebing.bbq10wubi.engine.DictTable;
import cn.kylebing.bbq10wubi.engine.WubiSession;
import cn.kylebing.bbq10wubi.sync.LegacyHttps;

public class WubiApp extends Application {
    private static final String TAG = "BBQ10Wubi";
    private final DictTable table = new DictTable();
    private WubiSession session;
    private volatile boolean ready;
    private String initError;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Runnable> readyWaiters = new ArrayList<Runnable>();

    public static WubiApp from(Context c) {
        Context app = c.getApplicationContext();
        if (app instanceof WubiApp) return (WubiApp) app;
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LegacyHttps.install(this);
        session = new WubiSession(table);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    table.loadAll(WubiApp.this);
                    ready = true;
                    initError = null;
                    Log.i(TAG, table.stats());
                } catch (Throwable t) {
                    Log.e(TAG, "dict load failed", t);
                    initError = t.getMessage() == null ? t.toString() : t.getMessage();
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        for (Runnable r : readyWaiters) r.run();
                        readyWaiters.clear();
                    }
                });
            }
        }, "wubi-dict").start();
    }

    public DictTable table() {
        return table;
    }

    public WubiSession session() {
        return session;
    }

    public boolean isReady() {
        return ready;
    }

    public String initError() {
        return initError;
    }

    public String stats() {
        return table.stats();
    }

    public void whenReady(Runnable r) {
        if (ready || initError != null) {
            r.run();
            return;
        }
        readyWaiters.add(r);
    }

    public void reloadUserDict() {
        if (!ready) return;
        table.reloadUserFromDocument(this);
    }

    public void reloadOverlay() {
        if (!ready) return;
        table.reloadOverlay(this);
    }
}
