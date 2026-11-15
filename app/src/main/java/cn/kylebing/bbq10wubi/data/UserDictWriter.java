package cn.kylebing.bbq10wubi.data;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import cn.kylebing.bbq10wubi.sync.UserDictAutoSync;

public final class UserDictWriter {
    public static final String UNNAMED = "未命名";

    private UserDictWriter() {}

    public static UserDictDocument loadActiveDocument(Context c) {
        try {
            RimeFiles.ensureUserDir(c);
        } catch (Exception ignored) {
        }
        return loadDocument(RimeFiles.userDict(c));
    }

    public static UserDictDocument loadDocument(File file) {
        if (file == null || !file.exists()) return UserDictDocument.empty();
        try {
            return parseDocument(readFile(file));
        } catch (Exception e) {
            return UserDictDocument.empty();
        }
    }

    public static void saveActiveDocument(Context c, UserDictDocument document) {
        saveDocument(c, document, RimeFiles.userDict(c), true);
    }

    public static void saveDocument(Context c, UserDictDocument document, File file, boolean markSync) {
        try {
            file.getParentFile().mkdirs();
            writeFile(file, render(document));
            if (markSync) UserDictAutoSync.noteLocalEdit(c);
        } catch (Exception ignored) {
        }
    }

    public static String exportText(Context c) {
        File f = RimeFiles.userDict(c);
        if (!f.exists()) return render(UserDictDocument.empty());
        try {
            return readFile(f);
        } catch (Exception e) {
            return render(loadActiveDocument(c));
        }
    }

    public static void importText(Context c, String text) {
        try {
            writeFile(RimeFiles.userDict(c), text);
            UserDictAutoSync.noteLocalEdit(c);
        } catch (Exception ignored) {
        }
    }

    public static String render(UserDictDocument document) {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        String version = iso.format(new Date());
        if (document.shouldPreserveGroupHeaders()) {
            List<UserDictGroup> groups = document.groups;
            if (groups.isEmpty()) {
                groups = new ArrayList<UserDictGroup>();
                groups.add(new UserDictGroup(""));
            }
            StringBuilder body = new StringBuilder();
            for (int i = 0; i < groups.size(); i++) {
                UserDictGroup g = groups.get(i);
                if (i > 0) body.append("\n\n");
                body.append("## ").append(g.name).append('\n');
                for (UserDictEntry e : g.entries) {
                    body.append(entryLine(e)).append('\n');
                }
                if (body.length() > 0 && body.charAt(body.length() - 1) == '\n') {
                    body.setLength(body.length() - 1);
                }
            }
            StringBuilder out = new StringBuilder();
            out.append("# Rime dictionary\n# encoding: utf-8\n---\n");
            out.append("name: wubi86_jidian_user\nversion: \"").append(version).append("\"\n");
            out.append("dict_grouped: true\nsort: by_weight\n...\n\n");
            if (body.length() > 0) out.append(body).append('\n');
            return out.toString();
        }
        StringBuilder out = new StringBuilder();
        out.append("# Rime dictionary\n# encoding: utf-8\n---\n");
        out.append("name: wubi86_jidian_user\nversion: \"").append(version).append("\"\n");
        out.append("dict_grouped: false\nsort: by_weight\n...\n\n");
        for (UserDictEntry e : document.allEntries()) {
            out.append(entryLine(e)).append('\n');
        }
        return out.toString();
    }

    public static UserDictDocument parseDocument(String fileContent) {
        String normalized = fileContent.replace("\r\n", "\n");
        int headerEnd = normalized.indexOf("...");
        if (headerEnd < 0) {
            if (normalized.contains("##")) {
                List<UserDictGroup> groups = parseGroupModeBody(normalized);
                if (groups.isEmpty()) return UserDictDocument.empty();
                return new UserDictDocument(true, groups);
            }
            List<UserDictEntry> entries = parseFlatBody(normalized);
            if (entries.isEmpty()) return UserDictDocument.empty();
            return UserDictDocument.flat(entries);
        }
        String header = normalized.substring(0, headerEnd + 3);
        String body = normalized.substring(headerEnd + 3);
        boolean isGroup = header.contains("dict_grouped: true") || body.contains("##");
        if (isGroup) {
            List<UserDictGroup> groups = parseGroupModeBody(body);
            if (groups.isEmpty()) {
                groups = new ArrayList<UserDictGroup>();
                groups.add(new UserDictGroup(""));
            }
            return new UserDictDocument(true, groups);
        }
        return UserDictDocument.flat(parseFlatBody(body));
    }

    private static List<UserDictGroup> parseGroupModeBody(String body) {
        String[] lines = body.replace("\r\n", "\n").split("\n", -1);
        List<UserDictGroup> wordsGroup = new ArrayList<UserDictGroup>();
        UserDictGroup temp = null;
        boolean lastEmpty = false;
        for (String item : lines) {
            if (item.startsWith("##")) {
                if (temp != null) {
                    if (temp.name.length() > 0) wordsGroup.add(temp);
                    else if (!temp.entries.isEmpty()) {
                        temp.name = UNNAMED;
                        wordsGroup.add(temp);
                    }
                }
                temp = new UserDictGroup(item.substring(2).trim());
                lastEmpty = false;
            } else if (item.indexOf('\t') >= 0) {
                UserDictEntry entry = parseEntryLine(item);
                if (entry == null) continue;
                if (temp == null) temp = new UserDictGroup("");
                temp.entries.add(entry);
                lastEmpty = false;
            } else if (item.startsWith("#")) {
                lastEmpty = false;
            } else {
                if (!lastEmpty && temp != null) {
                    if (temp.name.length() == 0) temp.name = UNNAMED;
                    wordsGroup.add(temp);
                    temp = new UserDictGroup("");
                }
                lastEmpty = true;
            }
        }
        if (temp != null && (temp.name.length() > 0 || !temp.entries.isEmpty())) {
            wordsGroup.add(temp);
        }
        return wordsGroup;
    }

    private static List<UserDictEntry> parseFlatBody(String body) {
        List<UserDictEntry> out = new ArrayList<UserDictEntry>();
        String[] lines = body.split("\n");
        for (String line : lines) {
            String t = line.trim();
            if (t.length() == 0 || t.startsWith("#")) continue;
            UserDictEntry e = parseEntryLine(line);
            if (e != null) out.add(e);
        }
        return out;
    }

    public static UserDictEntry parseEntryLine(String line) {
        String[] parts;
        if (line.indexOf('\t') >= 0) parts = line.split("\t", -1);
        else parts = line.trim().split("\\s+");
        if (parts.length < 2) return null;
        String text = parts[0];
        String code = parts[1].replace("\r", "");
        Integer weight = null;
        if (parts.length >= 3) {
            String raw = parts[2].trim();
            if (raw.length() > 0) {
                try {
                    weight = Integer.valueOf(Integer.parseInt(raw));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        String note = parts.length >= 4 ? joinFrom(parts, 3).replace("\r", "").trim() : "";
        boolean firstLooksLikeCode = isAscii(text);
        boolean secondHasCjk = hasCjk(code);
        if (firstLooksLikeCode && secondHasCjk) {
            String tmp = text;
            text = code;
            code = tmp;
        }
        return new UserDictEntry(code.toLowerCase(), text, weight, note);
    }

    private static String entryLine(UserDictEntry entry) {
        String note = entry.note.replace("\n", " ").replace("\r", "").trim();
        if (note.length() == 0) {
            if (entry.weight != null) return entry.text + "\t" + entry.code + "\t" + entry.weight;
            return entry.text + "\t" + entry.code;
        }
        String w = entry.weight != null ? String.valueOf(entry.weight) : "";
        return entry.text + "\t" + entry.code + "\t" + w + "\t" + note;
    }

    private static String joinFrom(String[] parts, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < parts.length; i++) {
            if (i > start) sb.append('\t');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private static boolean isAscii(String s) {
        if (s.length() == 0) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isLetterOrDigit(s.charAt(i))) return false;
        }
        return true;
    }

    private static boolean hasCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= 0x4E00 && ch <= 0x9FFF) return true;
        }
        return false;
    }

    public static String readFile(File file) throws Exception {
        FileInputStream in = new FileInputStream(file);
        try {
            byte[] buf = new byte[(int) file.length()];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
            return new String(buf, 0, off, "UTF-8");
        } finally {
            in.close();
        }
    }

    public static void writeFile(File file, String text) throws Exception {
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(text.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }
}
