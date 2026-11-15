package cn.kylebing.bbq10wubi.sync;

import android.content.Context;
import android.content.SharedPreferences;

import cn.kylebing.bbq10wubi.Prefs;

public final class WubiCloudConfig {
    public static final String DEFAULT_BASE = "https://kylebing.cn/wubi/portal/";
    public static final String SYNC_DICT = "wubi86_jidian_user.dict.yaml";
    public static final int SYNC_MAX = 40000;

    private WubiCloudConfig() {}

    public static String resolveBaseURL(String raw) {
        String base = raw == null ? "" : raw.trim();
        if (base.length() == 0) base = DEFAULT_BASE;
        if (base.startsWith("http://kylebing.cn/")) {
            base = "https://" + base.substring("http://".length());
        }
        if ("https://kylebing.cn/portal/".equals(base) || "https://kylebing.cn/portal".equals(base)) {
            base = DEFAULT_BASE;
        }
        if (!base.endsWith("/")) base = base + "/";
        return base;
    }

    public static String baseURL(Context c) {
        return resolveBaseURL(Prefs.p(c).getString("wubi.cloud.baseURL", DEFAULT_BASE));
    }

    public static void setBaseURL(Context c, String v) {
        Prefs.p(c).edit().putString("wubi.cloud.baseURL", resolveBaseURL(v)).apply();
    }

    public static WubiCloudUserInfo userInfo(Context c) {
        String raw = Prefs.p(c).getString("wubi.cloud.userInfo", null);
        if (raw == null) return null;
        return WubiCloudUserInfo.fromJson(raw);
    }

    public static void setUserInfo(Context c, WubiCloudUserInfo v) {
        SharedPreferences.Editor e = Prefs.p(c).edit();
        if (v != null) e.putString("wubi.cloud.userInfo", v.toJson());
        else e.remove("wubi.cloud.userInfo");
        e.apply();
    }

    public static boolean isLoggedIn(Context c) {
        WubiCloudUserInfo u = userInfo(c);
        return u != null && u.isLoggedIn();
    }

    public static void setLastSyncAt(Context c, long v) {
        Prefs.p(c).edit().putLong("wubi.cloud.lastSyncAt", v).apply();
    }

    public static long lastExtraPullAt(Context c) {
        return Prefs.p(c).getLong("wubi.cloud.lastExtraPullAt", 0L);
    }

    public static void setLastExtraPullAt(Context c, long v) {
        SharedPreferences.Editor e = Prefs.p(c).edit();
        if (v <= 0L) e.remove("wubi.cloud.lastExtraPullAt");
        else e.putLong("wubi.cloud.lastExtraPullAt", v);
        e.apply();
    }

    public static void updateTokenIfNeeded(Context c, String token) {
        if (token == null || token.length() == 0) return;
        WubiCloudUserInfo info = userInfo(c);
        if (info == null || token.equals(info.token)) return;
        setUserInfo(c, info.withToken(token));
    }

    public static void logout(Context c) {
        setUserInfo(c, null);
    }
}
