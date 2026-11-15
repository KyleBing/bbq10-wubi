package cn.kylebing.bbq10wubi.sync;

import android.content.Context;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import cn.kylebing.bbq10wubi.data.RimeFiles;
import cn.kylebing.bbq10wubi.data.UserDictDocument;
import cn.kylebing.bbq10wubi.data.UserDictEntry;
import cn.kylebing.bbq10wubi.data.UserDictGroup;
import cn.kylebing.bbq10wubi.data.UserDictWriter;

public final class ExtraDictUpdater {
    private ExtraDictUpdater() {}

    public static final class ApplyResult {
        public final int added;
        public final int wordCount;
        public final int groupCount;

        public ApplyResult(int added, int wordCount, int groupCount) {
            this.added = added;
            this.wordCount = wordCount;
            this.groupCount = groupCount;
        }
    }

    public static ApplyResult applyOnlineItems(Context c, List<WubiCloudApi.ExtraWord> items) throws Exception {
        HashSet<String> previous = localKeys(c);
        UserDictDocument document = buildDocument(items);
        HashSet<String> incoming = new HashSet<String>();
        for (UserDictEntry e : document.allEntries()) incoming.add(e.text + "\t" + e.code);
        int added = 0;
        for (String k : incoming) if (!previous.contains(k)) added++;
        File dest = RimeFiles.extraDict(c);
        dest.getParentFile().mkdirs();
        UserDictWriter.writeFile(dest, serialize(document));
        WubiCloudConfig.setLastExtraPullAt(c, System.currentTimeMillis());
        return new ApplyResult(added, document.entryCount(), document.groups.size());
    }

    private static HashSet<String> localKeys(Context c) {
        File file = RimeFiles.extraDict(c);
        HashSet<String> set = new HashSet<String>();
        if (!file.exists()) return set;
        try {
            for (UserDictEntry e : UserDictWriter.parseDocument(UserDictWriter.readFile(file)).allEntries()) {
                set.add(e.text + "\t" + e.code);
            }
        } catch (Exception ignored) {
        }
        return set;
    }

    public static UserDictDocument buildDocument(List<WubiCloudApi.ExtraWord> items) {
        ArrayList<WubiCloudApi.ExtraWord> sorted = new ArrayList<WubiCloudApi.ExtraWord>(items);
        Collections.sort(sorted, new Comparator<WubiCloudApi.ExtraWord>() {
            @Override
            public int compare(WubiCloudApi.ExtraWord a, WubiCloudApi.ExtraWord b) {
                int c = a.categoryId - b.categoryId;
                if (c != 0) return c;
                return a.id - b.id;
            }
        });
        List<UserDictGroup> groups = new ArrayList<UserDictGroup>();
        String lastName = null;
        for (WubiCloudApi.ExtraWord item : sorted) {
            Integer weight = item.weight > 0 ? Integer.valueOf(item.weight) : null;
            UserDictEntry entry = new UserDictEntry(
                    item.code.toLowerCase(), item.word, weight,
                    item.comment == null ? "" : item.comment.trim());
            if (lastName == null || !lastName.equals(item.categoryName)) {
                UserDictGroup g = new UserDictGroup(item.categoryName);
                g.entries.add(entry);
                groups.add(g);
                lastName = item.categoryName;
            } else if (!groups.isEmpty()) {
                groups.get(groups.size() - 1).entries.add(entry);
            }
        }
        if (groups.isEmpty()) {
            groups.add(new UserDictGroup(""));
        }
        return new UserDictDocument(true, groups);
    }

    private static String serialize(UserDictDocument document) {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        String version = iso.format(new Date());
        List<UserDictGroup> groups = document.groups;
        if (groups.isEmpty()) {
            groups = new ArrayList<UserDictGroup>();
            groups.add(new UserDictGroup(""));
        }
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < groups.size(); i++) {
            UserDictGroup group = groups.get(i);
            if (i > 0) body.append("\n\n");
            body.append("## ").append(group.name).append('\n');
            for (UserDictEntry e : group.entries) {
                String note = e.note.replace("\n", " ").trim();
                if (note.length() == 0) {
                    if (e.weight != null) body.append(e.text).append('\t').append(e.code).append('\t').append(e.weight);
                    else body.append(e.text).append('\t').append(e.code);
                } else {
                    String w = e.weight != null ? String.valueOf(e.weight) : "";
                    body.append(e.text).append('\t').append(e.code).append('\t').append(w).append('\t').append(note);
                }
                body.append('\n');
            }
            if (body.length() > 0 && body.charAt(body.length() - 1) == '\n') {
                body.setLength(body.length() - 1);
            }
        }
        StringBuilder out = new StringBuilder();
        out.append("# Rime dictionary\n# encoding: utf-8\n---\n");
        out.append("name: wubi86_jidian_extra\nversion: \"").append(version).append("\"\n");
        out.append("dict_grouped: true\nsort: by_weight\n...\n\n");
        if (body.length() > 0) out.append(body).append('\n');
        return out.toString();
    }
}
