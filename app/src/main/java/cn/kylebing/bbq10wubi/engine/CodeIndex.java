package cn.kylebing.bbq10wubi.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

final class CodeIndex {
    private static final Comparator<DictEntry> BY_RIME = new Comparator<DictEntry>() {
        @Override
        public int compare(DictEntry a, DictEntry b) {
            return DictEntry.compareRime(a, b);
        }
    };

    private final HashMap<String, ArrayList<DictEntry>> prefix = new HashMap<String, ArrayList<DictEntry>>();
    private boolean sorted;

    void add(DictEntry e) {
        if (e == null || e.code == null || e.code.length() == 0) return;
        String code = e.code;
        int n = code.length();
        for (int i = 1; i <= n; i++) {
            String p = code.substring(0, i);
            ArrayList<DictEntry> list = prefix.get(p);
            if (list == null) {
                list = new ArrayList<DictEntry>();
                prefix.put(p, list);
            }
            list.add(e);
        }
        sorted = false;
    }

    void sort() {
        if (sorted) return;
        for (ArrayList<DictEntry> list : prefix.values()) {
            Collections.sort(list, BY_RIME);
        }
        sorted = true;
    }

    List<DictEntry> lookup(String code) {
        if (code == null || code.length() == 0) return Collections.emptyList();
        sort();
        ArrayList<DictEntry> list = prefix.get(code);
        if (list == null) return Collections.emptyList();
        return list;
    }

    void clear() {
        prefix.clear();
        sorted = false;
    }
}
