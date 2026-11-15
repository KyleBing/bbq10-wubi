package cn.kylebing.bbq10wubi;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private Prefs() {}

    public static SharedPreferences p(Context c) {
        return c.getSharedPreferences("bbq10wubi", Context.MODE_PRIVATE);
    }

    public static boolean autoSelectUniqueFourCode(Context c) {
        return p(c).getBoolean("keyboard.fourCode.autoSelectUnique", true);
    }

    public static void setAutoSelectUniqueFourCode(Context c, boolean v) {
        p(c).edit().putBoolean("keyboard.fourCode.autoSelectUnique", v).commit();
    }

    public static boolean clearEmptyCode(Context c) {
        return p(c).getBoolean("keyboard.emptyCode.clear", false);
    }

    public static void setClearEmptyCode(Context c, boolean v) {
        p(c).edit().putBoolean("keyboard.emptyCode.clear", v).commit();
    }

    public static boolean enterClearsCode(Context c) {
        return p(c).getBoolean("keyboard.enter.clearsCode", false);
    }

    public static void setEnterClearsCode(Context c, boolean v) {
        p(c).edit().putBoolean("keyboard.enter.clearsCode", v).commit();
    }

    public static boolean showComment(Context c) {
        if (!p(c).contains("keyboard.candidate.showComment")) return true;
        return p(c).getBoolean("keyboard.candidate.showComment", true);
    }

    public static void setShowComment(Context c, boolean v) {
        p(c).edit().putBoolean("keyboard.candidate.showComment", v).commit();
    }

    public static boolean autoSync(Context c) {
        return p(c).getBoolean("wubi.autoSync.enabled", false);
    }

    public static void setAutoSync(Context c, boolean v) {
        p(c).edit().putBoolean("wubi.autoSync.enabled", v).commit();
    }

    public static boolean englishMode(Context c) {
        return p(c).getBoolean("input.english", false);
    }

    public static void setEnglishMode(Context c, boolean v) {
        p(c).edit().putBoolean("input.english", v).apply();
    }
}
