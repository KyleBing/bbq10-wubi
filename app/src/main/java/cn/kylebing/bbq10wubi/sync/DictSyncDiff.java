package cn.kylebing.bbq10wubi.sync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.kylebing.bbq10wubi.data.UserDictDocument;
import cn.kylebing.bbq10wubi.data.UserDictEntry;
import cn.kylebing.bbq10wubi.data.UserDictGroup;

public final class DictSyncDiff {
    private DictSyncDiff() {}

    public static final class WordItem {
        public final String word;
        public final String code;
        public final String priority;
        public final String note;
        public final String groupName;

        public WordItem(String word, String code, String priority, String note, String groupName) {
            this.word = word;
            this.code = code;
            this.priority = priority == null ? "" : priority;
            this.note = note == null ? "" : note;
            this.groupName = groupName == null ? "" : groupName;
        }

        public String key() {
            return groupName + "\t" + word + "\t" + code;
        }
    }

    public static final class Result {
        public final int localCount;
        public final int remoteCount;
        public final List<WordItem> onlyLocal;
        public final List<WordItem> onlyRemote;
        public final int sameCount;
        public final boolean hasRemote;

        public Result(int localCount, int remoteCount, List<WordItem> onlyLocal,
                      List<WordItem> onlyRemote, int sameCount, boolean hasRemote) {
            this.localCount = localCount;
            this.remoteCount = remoteCount;
            this.onlyLocal = onlyLocal;
            this.onlyRemote = onlyRemote;
            this.sameCount = sameCount;
            this.hasRemote = hasRemote;
        }

        public boolean isIdentical() {
            return hasRemote && onlyLocal.isEmpty() && onlyRemote.isEmpty();
        }

        public String summary() {
            if (!hasRemote) return "云端尚无备份，本地 " + localCount + " 条";
            if (isIdentical()) return "本地与云端完全一致（" + localCount + " 条）";
            return "本地 " + localCount + " 条，云端 " + remoteCount + " 条；仅本地 "
                    + onlyLocal.size() + "，仅云端 " + onlyRemote.size();
        }
    }

    public static final class ThreeWayMerge {
        public final UserDictDocument merged;
        public final UserDictDocument nextBase;
        public final List<WordItem> conflicts;
        public final int tookRemote;
        public final int dropped;
        public final boolean needsUpload;
        public final boolean localChanged;

        public ThreeWayMerge(UserDictDocument merged, UserDictDocument nextBase, List<WordItem> conflicts,
                             int tookRemote, int dropped, boolean needsUpload, boolean localChanged) {
            this.merged = merged;
            this.nextBase = nextBase;
            this.conflicts = conflicts;
            this.tookRemote = tookRemote;
            this.dropped = dropped;
            this.needsUpload = needsUpload;
            this.localChanged = localChanged;
        }
    }

    public static List<WordItem> flatten(UserDictDocument doc) {
        ArrayList<WordItem> list = new ArrayList<WordItem>();
        for (UserDictGroup g : doc.groups) {
            for (UserDictEntry e : g.entries) {
                list.add(new WordItem(e.text, e.code, e.weight == null ? "" : String.valueOf(e.weight), e.note, g.name));
            }
        }
        return list;
    }

    public static Result compare(List<WordItem> local, List<WordItem> remote, boolean hasRemote) {
        List<WordItem> remoteList = remote == null ? Collections.<WordItem>emptyList() : remote;
        HashMap<String, WordItem> remoteMap = index(remoteList);
        HashMap<String, WordItem> localMap = index(local);
        ArrayList<WordItem> onlyLocal = new ArrayList<WordItem>();
        for (WordItem it : local) if (!remoteMap.containsKey(it.key())) onlyLocal.add(it);
        ArrayList<WordItem> onlyRemote = new ArrayList<WordItem>();
        for (WordItem it : remoteList) if (!localMap.containsKey(it.key())) onlyRemote.add(it);
        int same = 0;
        for (WordItem l : local) {
            WordItem r = remoteMap.get(l.key());
            if (r != null && sameContent(l, r)) same++;
        }
        return new Result(local.size(), remoteList.size(), onlyLocal, onlyRemote, same, hasRemote);
    }

    public static UserDictDocument mergeRemoteIntoLocal(UserDictDocument local, UserDictDocument remote) {
        UserDictDocument result = local.deepCopy();
        HashMap<String, Integer> indexByName = new HashMap<String, Integer>();
        for (int i = 0; i < result.groups.size(); i++) {
            indexByName.put(result.groups.get(i).name, Integer.valueOf(i));
        }
        for (UserDictGroup remoteGroup : remote.groups) {
            Integer idx = indexByName.get(remoteGroup.name);
            if (idx != null) {
                UserDictGroup dest = result.groups.get(idx.intValue());
                HashSet<String> existing = new HashSet<String>();
                for (UserDictEntry e : dest.entries) {
                    existing.add(dest.name + "\t" + e.text + "\t" + e.code);
                }
                for (UserDictEntry entry : remoteGroup.entries) {
                    String key = remoteGroup.name + "\t" + entry.text + "\t" + entry.code;
                    if (existing.contains(key)) continue;
                    dest.entries.add(entry);
                    existing.add(key);
                }
            } else {
                UserDictGroup ng = new UserDictGroup(remoteGroup.name);
                ng.entries.addAll(remoteGroup.entries);
                result.groups.add(ng);
                indexByName.put(ng.name, Integer.valueOf(result.groups.size() - 1));
            }
        }
        if (!result.isGroupMode && result.shouldPreserveGroupHeaders()) result.isGroupMode = true;
        return result;
    }

    public static boolean sameSnapshot(UserDictDocument lhs, UserDictDocument rhs) {
        return mapsEqual(indexMap(lhs), indexMap(rhs));
    }

    public static ThreeWayMerge threeWayMerge(UserDictDocument base, UserDictDocument local, UserDictDocument remote) {
        Map<String, WordItem> baseMap = base == null ? new HashMap<String, WordItem>() : indexMap(base);
        Map<String, WordItem> localMap = indexMap(local);
        Map<String, WordItem> remoteMap = indexMap(remote);
        HashSet<String> allKeys = new HashSet<String>();
        allKeys.addAll(baseMap.keySet());
        allKeys.addAll(localMap.keySet());
        allKeys.addAll(remoteMap.keySet());
        ArrayList<String> keys = new ArrayList<String>(allKeys);
        Collections.sort(keys);
        LinkedHashMap<String, WordItem> chosen = new LinkedHashMap<String, WordItem>();
        HashSet<String> conflictKeys = new HashSet<String>();
        ArrayList<WordItem> conflicts = new ArrayList<WordItem>();
        int tookRemote = 0;
        int dropped = 0;
        for (String key : keys) {
            Pick pick = pick(baseMap.get(key), localMap.get(key), remoteMap.get(key));
            if (pick.drop) {
                if (localMap.get(key) != null) dropped++;
            } else if (pick.conflict) {
                conflicts.add(pick.item);
                conflictKeys.add(key);
                if (localMap.get(key) != null) chosen.put(key, localMap.get(key));
            } else {
                chosen.put(key, pick.item);
                WordItem remoteItem = remoteMap.get(key);
                if (remoteItem != null && sameContent(pick.item, remoteItem)) {
                    WordItem localItem = localMap.get(key);
                    if (localItem == null || !sameContent(localItem, remoteItem)) tookRemote++;
                }
            }
        }
        UserDictDocument merged = documentFrom(chosen, local);
        Map<String, WordItem> mergedMap = indexMap(merged);
        LinkedHashMap<String, WordItem> baseNext = new LinkedHashMap<String, WordItem>();
        if (conflictKeys.isEmpty()) {
            baseNext.putAll(mergedMap);
        } else {
            for (Map.Entry<String, WordItem> e : remoteMap.entrySet()) {
                if (conflictKeys.contains(e.getKey())) {
                    WordItem b = baseMap.get(e.getKey());
                    if (b != null) baseNext.put(e.getKey(), b);
                    continue;
                }
                WordItem kept = chosen.get(e.getKey());
                if (kept != null && sameContent(kept, e.getValue())) baseNext.put(e.getKey(), e.getValue());
            }
            for (String k : conflictKeys) {
                WordItem b = baseMap.get(k);
                if (b != null) baseNext.put(k, b);
            }
        }
        return new ThreeWayMerge(
                merged,
                documentFrom(baseNext, local),
                conflicts,
                tookRemote,
                dropped,
                conflictKeys.isEmpty() && !mapsEqual(mergedMap, remoteMap),
                !mapsEqual(mergedMap, localMap));
    }

    private static final class Pick {
        final WordItem item;
        final boolean drop;
        final boolean conflict;

        Pick(WordItem item, boolean drop, boolean conflict) {
            this.item = item;
            this.drop = drop;
            this.conflict = conflict;
        }
    }

    private static Pick pick(WordItem base, WordItem local, WordItem remote) {
        if (local != null && remote != null && sameContent(local, remote)) return new Pick(local, false, false);
        if (base == null && local != null && remote != null) return new Pick(local, false, true);
        if (base != null && local != null && remote != null) {
            boolean localChanged = !sameContent(local, base);
            boolean remoteChanged = !sameContent(remote, base);
            if (localChanged && remoteChanged) return new Pick(local, false, true);
            if (remoteChanged) return new Pick(remote, false, false);
            return new Pick(local, false, false);
        }
        if (base != null && local != null && remote == null) {
            if (sameContent(local, base)) return new Pick(null, true, false);
            return new Pick(local, false, true);
        }
        if (base != null && local == null && remote != null) {
            if (sameContent(remote, base)) return new Pick(null, true, false);
            return new Pick(remote, false, true);
        }
        if (local != null) return new Pick(local, false, false);
        if (remote != null) return new Pick(remote, false, false);
        return new Pick(null, true, false);
    }

    private static boolean sameContent(WordItem lhs, WordItem rhs) {
        return lhs.priority.equals(rhs.priority) && lhs.note.equals(rhs.note) && lhs.groupName.equals(rhs.groupName);
    }

    private static Map<String, WordItem> indexMap(UserDictDocument document) {
        LinkedHashMap<String, WordItem> map = new LinkedHashMap<String, WordItem>();
        for (WordItem it : flatten(document)) map.put(it.key(), it);
        return map;
    }

    private static HashMap<String, WordItem> index(List<WordItem> list) {
        HashMap<String, WordItem> map = new HashMap<String, WordItem>();
        for (WordItem it : list) map.put(it.key(), it);
        return map;
    }

    private static boolean mapsEqual(Map<String, WordItem> lhs, Map<String, WordItem> rhs) {
        if (lhs.size() != rhs.size()) return false;
        for (Map.Entry<String, WordItem> e : lhs.entrySet()) {
            WordItem other = rhs.get(e.getKey());
            if (other == null || !sameContent(e.getValue(), other)) return false;
        }
        return true;
    }

    private static Integer weightValue(String priority) {
        String raw = priority.trim();
        if (raw.length() == 0) return null;
        try {
            return Integer.valueOf(Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static UserDictDocument documentFrom(Map<String, WordItem> chosen, UserDictDocument template) {
        UserDictDocument result = template.deepCopy();
        HashSet<String> used = new HashSet<String>();
        for (UserDictGroup group : result.groups) {
            ArrayList<UserDictEntry> kept = new ArrayList<UserDictEntry>();
            for (UserDictEntry entry : group.entries) {
                String key = group.name + "\t" + entry.text + "\t" + entry.code;
                WordItem item = chosen.get(key);
                if (item == null) continue;
                kept.add(new UserDictEntry(entry.code, entry.text, weightValue(item.priority), item.note));
                used.add(key);
            }
            group.entries.clear();
            group.entries.addAll(kept);
        }
        ArrayList<String> leftover = new ArrayList<String>();
        for (String key : chosen.keySet()) if (!used.contains(key)) leftover.add(key);
        Collections.sort(leftover);
        for (String key : leftover) {
            WordItem item = chosen.get(key);
            if (item == null) continue;
            UserDictEntry entry = new UserDictEntry(item.code, item.word, weightValue(item.priority), item.note);
            int index = -1;
            for (int i = 0; i < result.groups.size(); i++) {
                if (result.groups.get(i).name.equals(item.groupName)) {
                    index = i;
                    break;
                }
            }
            if (index >= 0) {
                result.groups.get(index).entries.add(entry);
            } else {
                UserDictGroup g = new UserDictGroup(item.groupName);
                g.entries.add(entry);
                result.groups.add(g);
                if (item.groupName.length() > 0) result.isGroupMode = true;
            }
        }
        return result;
    }
}
