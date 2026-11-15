package cn.kylebing.bbq10wubi.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.File;

import cn.kylebing.bbq10wubi.Prefs;
import cn.kylebing.bbq10wubi.WubiApp;
import cn.kylebing.bbq10wubi.data.RimeFiles;
import cn.kylebing.bbq10wubi.data.UserDictDocument;
import cn.kylebing.bbq10wubi.data.UserDictWriter;

public final class UserDictAutoSync {
    private static final String LAST_TEXT = "wubi.autoSync.lastText";
    private static final String ATTEMPT = "wubi.autoSync.lastAttempt";
    private static final String REVISION = "wubi.autoSync.revision";
    private static final String LOCK = "wubi.autoSync.lock";
    private static final long INTERVAL_MS = 10 * 60 * 1000L;

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static boolean applying;
    private static boolean running;
    private static Runnable pending;

    private UserDictAutoSync() {}

    public static String lastResult(Context c) {
        if (!Prefs.autoSync(c)) return null;
        return Prefs.p(c).getString(LAST_TEXT, null);
    }

    public static void activate(final Context c) {
        if (!Prefs.autoSync(c) || !WubiCloudConfig.isLoggedIn(c)) return;
        long last = Prefs.p(c).getLong(ATTEMPT, 0L);
        if (System.currentTimeMillis() - last < INTERVAL_MS) return;
        start(c, 2000);
    }

    public static void syncSoon(Context c) {
        start(c, 300);
    }

    public static void noteLocalEdit(final Context c) {
        if (applying || !Prefs.autoSync(c) || !WubiCloudConfig.isLoggedIn(c)) return;
        if (pending != null) handler.removeCallbacks(pending);
        pending = new Runnable() {
            @Override
            public void run() {
                start(c, 0);
            }
        };
        handler.postDelayed(pending, 3000);
    }

    public static void noteCloudRevision(Context c, int revision, UserDictDocument snapshot) {
        if (revision <= 0) return;
        Prefs.p(c).edit().putInt(REVISION, revision).apply();
        saveBase(c, snapshot);
    }

    public static void markApplying(Runnable block) {
        applying = true;
        try {
            block.run();
        } finally {
            applying = false;
        }
    }

    private static void start(final Context c, long delayMs) {
        if (!Prefs.autoSync(c) || !WubiCloudConfig.isLoggedIn(c)) return;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        runOnce(c.getApplicationContext());
                    }
                }, "wubi-autosync").start();
            }
        }, delayMs);
    }

    private static void runOnce(Context c) {
        if (!Prefs.autoSync(c) || !WubiCloudConfig.isLoggedIn(c)) return;
        synchronized (UserDictAutoSync.class) {
            if (running) return;
            if (!tryLock(c)) return;
            running = true;
        }
        try {
            Prefs.p(c).edit().putLong(ATTEMPT, System.currentTimeMillis()).apply();
            String outcome = syncBody(c);
            if (Prefs.autoSync(c)) record(c, outcome);
        } catch (Exception e) {
            if (Prefs.autoSync(c)) record(c, "自动同步失败：" + (e.getMessage() == null ? "失败" : e.getMessage()));
        } finally {
            unlock(c);
            synchronized (UserDictAutoSync.class) {
                running = false;
            }
        }
    }

    private static String syncBody(Context c) throws Exception {
        WubiCloudApi.DictMeta meta = WubiCloudApi.fetchMeta(c);
        int savedRev = Prefs.p(c).getInt(REVISION, 0);
        UserDictDocument base = loadBase(c);
        UserDictDocument local = UserDictWriter.loadActiveDocument(c);
        boolean localDirty = base == null || !DictSyncDiff.sameSnapshot(local, base);
        if (meta != null && meta.revision == savedRev && savedRev > 0 && !localDirty) {
            return "自动同步：本地与云端一致";
        }
        if (meta != null && meta.revision == savedRev && savedRev > 0 && base != null && localDirty) {
            return mergeAndPush(c, base, savedRev, base, true);
        }
        return pullMergeAndPush(c, true);
    }

    private static String pullMergeAndPush(Context c, boolean allowRetry) throws Exception {
        WubiCloudApi.PullResult pulled = WubiCloudApi.pullDict(c);
        UserDictDocument base = loadBase(c);
        if (pulled == null) {
            UserDictDocument local = UserDictWriter.loadActiveDocument(c);
            int revision = WubiCloudApi.pushDict(c, UserDictWriter.exportText(c), local.entryCount(), Integer.valueOf(0));
            saveBase(c, local);
            Prefs.p(c).edit().putInt(REVISION, revision).apply();
            return "自动同步：云端还没有备份，已上传本地 " + local.entryCount() + " 条";
        }
        UserDictDocument remote = UserDictWriter.parseDocument(pulled.yaml);
        return mergeAndPush(c, remote, pulled.revision, base, allowRetry);
    }

    private static String mergeAndPush(final Context c, UserDictDocument remote, int remoteRevision,
                                       UserDictDocument base, boolean allowRetry) throws Exception {
        UserDictDocument local = UserDictWriter.loadActiveDocument(c);
        DictSyncDiff.Result probe = DictSyncDiff.compare(DictSyncDiff.flatten(local), DictSyncDiff.flatten(remote), true);
        if (isLarge(probe)) {
            return "自动同步已暂停：差异较大，请到「同步账号」手动选择。";
        }
        final DictSyncDiff.ThreeWayMerge merged = DictSyncDiff.threeWayMerge(base, local, remote);
        if (merged.localChanged) {
            markApplying(new Runnable() {
                @Override
                public void run() {
                    UserDictWriter.saveActiveDocument(c, merged.merged);
                    WubiApp app = WubiApp.from(c);
                    if (app != null) app.reloadUserDict();
                }
            });
        }
        int revision = remoteRevision;
        if (merged.needsUpload) {
            UserDictDocument fresh = UserDictWriter.loadActiveDocument(c);
            if (!DictSyncDiff.sameSnapshot(fresh, merged.merged)) {
                noteLocalEdit(c);
                return "自动同步：本地又有新改动，稍后重试";
            }
            try {
                revision = WubiCloudApi.pushDict(c, UserDictWriter.exportText(c), merged.merged.entryCount(),
                        Integer.valueOf(remoteRevision));
            } catch (CloudException e) {
                if (allowRetry && e.getMessage() != null && e.getMessage().contains("版本已变化")) {
                    return pullMergeAndPush(c, false);
                }
                throw e;
            }
        }
        saveBase(c, merged.nextBase);
        if (revision > 0) Prefs.p(c).edit().putInt(REVISION, revision).apply();
        return message(merged);
    }

    private static boolean isLarge(DictSyncDiff.Result r) {
        if (r.isIdentical()) return false;
        if (r.localCount == 0 && r.remoteCount >= 5) return true;
        if (r.localCount <= 2 && r.remoteCount >= 20) return true;
        if (r.hasRemote && r.remoteCount == 0 && r.localCount >= 5) return true;
        int divergent = r.onlyLocal.size() + r.onlyRemote.size();
        if (divergent >= 30) return true;
        int total = Math.max(Math.max(r.localCount, r.remoteCount), 1);
        return divergent >= 10 && (divergent * 2 >= total);
    }

    private static String message(DictSyncDiff.ThreeWayMerge merged) {
        if (!merged.conflicts.isEmpty()) {
            return "自动同步：有冲突已保留本地，未上传";
        }
        if (merged.needsUpload && merged.tookRemote > 0) {
            return "自动同步：已收下云端 " + merged.tookRemote + " 条，并上传本地改动";
        }
        if (merged.needsUpload) return "自动同步：已上传本地改动";
        if (merged.tookRemote > 0 || merged.dropped > 0) {
            return "自动同步：已更新本地，收下 " + merged.tookRemote + " 条，删除 " + merged.dropped + " 条";
        }
        return "自动同步：本地与云端一致";
    }

    private static void record(Context c, String text) {
        Prefs.p(c).edit().putString(LAST_TEXT, text).apply();
    }

    private static UserDictDocument loadBase(Context c) {
        File file = RimeFiles.syncBase(c);
        if (!file.exists()) return null;
        try {
            return UserDictWriter.parseDocument(UserDictWriter.readFile(file));
        } catch (Exception e) {
            return null;
        }
    }

    private static void saveBase(Context c, UserDictDocument document) {
        try {
            UserDictWriter.writeFile(RimeFiles.syncBase(c), UserDictWriter.render(document));
        } catch (Exception ignored) {
        }
    }

    private static boolean tryLock(Context c) {
        long now = System.currentTimeMillis();
        SharedPreferences p = Prefs.p(c);
        long held = p.getLong(LOCK, 0L);
        if (held > now) return false;
        return p.edit().putLong(LOCK, now + 45000L).commit();
    }

    private static void unlock(Context c) {
        Prefs.p(c).edit().putLong(LOCK, 0L).commit();
    }
}
