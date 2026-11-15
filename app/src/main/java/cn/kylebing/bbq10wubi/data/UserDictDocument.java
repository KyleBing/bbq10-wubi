package cn.kylebing.bbq10wubi.data;

import java.util.ArrayList;
import java.util.List;

public final class UserDictDocument {
    public boolean isGroupMode;
    public final List<UserDictGroup> groups;

    public UserDictDocument(boolean isGroupMode, List<UserDictGroup> groups) {
        this.isGroupMode = isGroupMode;
        this.groups = groups;
    }

    public static UserDictDocument empty() {
        List<UserDictGroup> g = new ArrayList<UserDictGroup>();
        g.add(new UserDictGroup(""));
        return new UserDictDocument(true, g);
    }

    public static UserDictDocument flat(List<UserDictEntry> entries) {
        UserDictGroup g = new UserDictGroup("");
        if (entries != null) g.entries.addAll(entries);
        List<UserDictGroup> groups = new ArrayList<UserDictGroup>();
        groups.add(g);
        return new UserDictDocument(false, groups);
    }

    public List<UserDictEntry> allEntries() {
        ArrayList<UserDictEntry> out = new ArrayList<UserDictEntry>();
        for (UserDictGroup g : groups) out.addAll(g.entries);
        return out;
    }

    public int entryCount() {
        int n = 0;
        for (UserDictGroup g : groups) n += g.entries.size();
        return n;
    }

    public boolean shouldPreserveGroupHeaders() {
        if (isGroupMode) return true;
        if (groups.size() > 1) return true;
        for (UserDictGroup g : groups) {
            if (g.name.trim().length() > 0) return true;
        }
        return false;
    }

    public UserDictDocument deepCopy() {
        List<UserDictGroup> copy = new ArrayList<UserDictGroup>();
        for (UserDictGroup g : groups) {
            UserDictGroup ng = new UserDictGroup(g.id, g.name);
            ng.entries.addAll(g.entries);
            copy.add(ng);
        }
        return new UserDictDocument(isGroupMode, copy);
    }
}
