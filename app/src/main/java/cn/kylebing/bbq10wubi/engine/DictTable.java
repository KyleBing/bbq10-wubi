package cn.kylebing.bbq10wubi.engine;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import cn.kylebing.bbq10wubi.data.RimeFiles;
import cn.kylebing.bbq10wubi.data.UserDictDocument;
import cn.kylebing.bbq10wubi.data.UserDictEntry;
import cn.kylebing.bbq10wubi.data.UserDictWriter;

/** 极点五笔码表：主词库 + 扩展 + 用户词；z 反查用拼音表。 */
public final class DictTable {
    private static final int MAX_CAND = 180;

    private final CodeIndex wubi = new CodeIndex();
    private final CodeIndex pinyin = new CodeIndex();
    private int mainCount;
    private int extraCount;
    private int userCount;
    private int pinyinCount;
    private int seq;
    private final HashMap<String, String> textToWubi = new HashMap<String, String>();

    public synchronized void loadAll(Context context) throws Exception {
        wubi.clear();
        pinyin.clear();
        mainCount = extraCount = userCount = pinyinCount = 0;
        seq = 0;
        textToWubi.clear();
        RimeFiles.ensureUserDir(context);
        loadAssetOrFile(context, "wubi86_jidian.dict.yaml", DictEntry.SRC_MAIN, wubi);
        reloadOverlay(context);
        loadAssetOrFile(context, "pinyin_simp.dict.yaml", DictEntry.SRC_PINYIN, pinyin);
        wubi.sort();
        pinyin.sort();
    }

    public synchronized void reloadOverlay(Context context) {
        CodeIndex overlay = new CodeIndex();
        extraCount = loadFile(RimeFiles.extraDict(context), DictEntry.SRC_EXTRA, overlay);
        userCount = loadFile(RimeFiles.userDict(context), DictEntry.SRC_USER, overlay);
        File hamster = RimeFiles.hamsterDict(context);
        if (hamster.isFile()) {
            loadFile(hamster, DictEntry.SRC_USER, overlay);
        }
        // rebuild wubi: keep main by re-reading is expensive; overlay is merged at lookup
        this.overlay = overlay;
        overlay.sort();
    }

    private CodeIndex overlay = new CodeIndex();

    private void loadAssetOrFile(Context context, String name, int source, CodeIndex index) throws Exception {
        File file = new File(RimeFiles.userDir(context), name);
        if (file.isFile()) {
            loadStream(new FileInputStream(file), source, index);
            return;
        }
        AssetManager am = context.getAssets();
        InputStream in = am.open("rime/" + name);
        try {
            int n = loadStream(in, source, index);
            if (source == DictEntry.SRC_MAIN) mainCount = n;
            if (source == DictEntry.SRC_PINYIN) pinyinCount = n;
        } finally {
            in.close();
        }
    }

    private int loadFile(File file, int source, CodeIndex index) {
        if (file == null || !file.isFile()) return 0;
        try {
            FileInputStream in = new FileInputStream(file);
            try {
                return loadStream(in, source, index);
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private int loadStream(InputStream in, int source, CodeIndex index) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"), 32 * 1024);
        boolean body = false;
        int n = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            if (!body) {
                if (line.startsWith("...")) body = true;
                continue;
            }
            line = line.trim();
            if (line.length() == 0 || line.startsWith("#") || line.startsWith("##")) continue;
            DictEntry e = parseLine(line, source, seq++);
            if (e == null) continue;
            index.add(e);
            if (source != DictEntry.SRC_PINYIN) rememberWubi(e);
            n++;
        }
        return n;
    }

    static DictEntry parseLine(String line, int source, int ord) {
        String[] parts;
        if (line.indexOf('\t') >= 0) {
            parts = splitTab(line);
        } else {
            parts = line.split("\\s+");
        }
        if (parts.length < 2) return null;
        String text = parts[0];
        String code = parts[1].replace("\r", "").replace(" ", "").replace("'", "").toLowerCase();
        if (code.length() == 0) return null;
        int weight = 0;
        String note = "";
        if (parts.length >= 3) {
            try {
                if (parts[2].length() > 0) weight = Integer.parseInt(parts[2].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (parts.length >= 4) {
            StringBuilder sb = new StringBuilder();
            for (int i = 3; i < parts.length; i++) {
                if (i > 3) sb.append('\t');
                sb.append(parts[i]);
            }
            note = sb.toString().trim();
        }
        boolean firstLooksLikeCode = isAsciiCode(text);
        boolean secondHasCjk = hasCjk(code);
        if (firstLooksLikeCode && secondHasCjk) {
            String tmp = text;
            text = parts[1];
            code = tmp.toLowerCase();
        }
        return new DictEntry(text, code, weight, note, source, ord);
    }

    private static String[] splitTab(String line) {
        ArrayList<String> out = new ArrayList<String>(4);
        int start = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '\t') {
                out.add(line.substring(start, i));
                start = i + 1;
            }
        }
        out.add(line.substring(start));
        return out.toArray(new String[out.size()]);
    }

    private static boolean isAsciiCode(String s) {
        if (s.length() == 0) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c)) return false;
        }
        return true;
    }

    private static boolean hasCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) return true;
        }
        return false;
    }

    public synchronized List<DictEntry> lookupWubi(String code) {
        return merge(overlay.lookup(code), wubi.lookup(code));
    }

    public synchronized List<DictEntry> lookupPinyin(String code) {
        if (code == null) return Collections.emptyList();
        String py = code.replace(" ", "").replace("'", "").toLowerCase();
        return merge(Collections.<DictEntry>emptyList(), pinyin.lookup(py));
    }

    public synchronized String wubiCodeOf(String text) {
        if (text == null) return "";
        String code = textToWubi.get(text);
        return code == null ? "" : code;
    }

    private void rememberWubi(DictEntry e) {
        if (e == null || e.text.length() == 0 || e.code.length() == 0) return;
        String prev = textToWubi.get(e.text);
        if (prev == null || e.code.length() > prev.length()) {
            textToWubi.put(e.text, e.code);
        }
    }

    private static List<DictEntry> merge(List<DictEntry> a, List<DictEntry> b) {
        if (a == null) a = Collections.emptyList();
        if (b == null) b = Collections.emptyList();
        ArrayList<DictEntry> out = new ArrayList<DictEntry>(Math.min(MAX_CAND, a.size() + b.size()));
        HashSet<String> seen = new HashSet<String>();
        int i = 0;
        int j = 0;
        while (out.size() < MAX_CAND && (i < a.size() || j < b.size())) {
            DictEntry pick;
            if (i >= a.size()) pick = b.get(j++);
            else if (j >= b.size()) pick = a.get(i++);
            else if (DictEntry.compareRime(a.get(i), b.get(j)) <= 0) pick = a.get(i++);
            else pick = b.get(j++);
            if (seen.add(pick.text)) out.add(pick);
        }
        return out;
    }

    public synchronized void reloadUserFromDocument(Context context) {
        UserDictDocument doc = UserDictWriter.loadActiveDocument(context);
        CodeIndex next = new CodeIndex();
        extraCount = loadFile(RimeFiles.extraDict(context), DictEntry.SRC_EXTRA, next);
        userCount = 0;
        for (UserDictEntry e : doc.allEntries()) {
            int w = e.weight == null ? 0 : e.weight.intValue();
            DictEntry de = new DictEntry(e.text, e.code.toLowerCase(), w, e.note, DictEntry.SRC_USER, seq++);
            next.add(de);
            rememberWubi(de);
            userCount++;
        }
        File hamster = RimeFiles.hamsterDict(context);
        if (hamster.isFile()) {
            loadFile(hamster, DictEntry.SRC_USER, next);
        }
        overlay = next;
        overlay.sort();
    }

    public String stats() {
        return "主库 " + mainCount + " · 扩展 " + extraCount + " · 用户 " + userCount
                + " · 拼音 " + pinyinCount;
    }
}
