package cn.kylebing.bbq10wubi.data;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public final class RimeFiles {
    private RimeFiles() {}

    public static File userDir(Context c) {
        return new File(c.getFilesDir(), "rime");
    }

    public static File userDict(Context c) {
        return new File(userDir(c), "wubi86_jidian_user.dict.yaml");
    }

    public static File extraDict(Context c) {
        return new File(userDir(c), "wubi86_jidian_extra.dict.yaml");
    }

    public static File hamsterDict(Context c) {
        return new File(userDir(c), "wubi86_jidian_user_hamster.dict.yaml");
    }

    public static File syncBase(Context c) {
        return new File(c.getFilesDir(), "wubi-sync-base.yaml");
    }

    public static void ensureUserDir(Context c) throws Exception {
        File dir = userDir(c);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new Exception("无法创建词库目录");
        }
        copyIfMissing(c, "wubi86_jidian_user.dict.yaml", userDict(c));
        copyIfMissing(c, "wubi86_jidian_extra.dict.yaml", extraDict(c));
        copyIfMissing(c, "wubi86_jidian_user_hamster.dict.yaml", hamsterDict(c));
    }

    private static void copyIfMissing(Context c, String asset, File dest) throws Exception {
        if (dest.exists()) return;
        AssetManager am = c.getAssets();
        InputStream in = am.open("rime/" + asset);
        try {
            OutputStream out = new FileOutputStream(dest);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }
}
