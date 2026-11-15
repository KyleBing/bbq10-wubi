package cn.kylebing.bbq10wubi.engine;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import cn.kylebing.bbq10wubi.Prefs;

/** 组词状态：编码、候选、翻页。行为对齐 iOS 玫枫五笔。 */
public final class WubiSession {
    public static final int PAGE_SIZE = 9;

    private final DictTable table;
    private String code = "";
    private String lastCommit = "";
    private List<Candidate> all = Collections.emptyList();
    private int page;
    private int specialCount;

    public WubiSession(DictTable table) {
        this.table = table;
    }

    public String code() {
        return code;
    }

    public boolean composing() {
        return code.length() > 0;
    }

    public List<Candidate> pageItems() {
        if (all.isEmpty()) return Collections.emptyList();
        int start = page * PAGE_SIZE;
        if (start >= all.size()) return Collections.emptyList();
        int end = Math.min(all.size(), start + PAGE_SIZE);
        return all.subList(start, end);
    }

    public int pageIndex() {
        return page;
    }

    public int pageCount() {
        if (all.isEmpty()) return 0;
        return (all.size() + PAGE_SIZE - 1) / PAGE_SIZE;
    }

    public List<Candidate> allCandidates() {
        return all;
    }

    public void clear() {
        code = "";
        page = 0;
        all = Collections.emptyList();
        specialCount = 0;
    }

    public String typeLetter(Context c, char ch) {
        char lower = Character.toLowerCase(ch);
        if (lower < 'a' || lower > 'z') return null;
        if (code.length() >= 4 && code.charAt(0) != 'z') {
            // 四码已满：先上屏首选再开始下一码（auto_select 关，但继续打会顶掉）
            String committed = selectIndex(c, 0);
            code = String.valueOf(lower);
            refresh(c);
            String auto = applyFourRules(c);
            if (auto != null) {
                return (committed == null ? "" : committed) + auto;
            }
            return committed;
        }
        code = code + lower;
        refresh(c);
        return applyFourRules(c);
    }

    public boolean deleteCode() {
        if (code.length() == 0) return false;
        code = code.substring(0, code.length() - 1);
        page = 0;
        if (code.length() == 0) {
            all = Collections.emptyList();
            specialCount = 0;
        }
        return true;
    }

    public void refresh(Context c) {
        page = 0;
        rebuild(c);
    }

    public boolean pageNext() {
        if (page + 1 < pageCount()) {
            page++;
            return true;
        }
        return false;
    }

    public boolean pagePrev() {
        if (page > 0) {
            page--;
            return true;
        }
        return false;
    }

    public void resetPage() {
        page = 0;
    }

    public boolean reverseLookup() {
        return code.length() > 1 && (code.charAt(0) == 'z' || code.charAt(0) == 'Z');
    }

    public String reversePinyin() {
        if (!reverseLookup()) return "";
        return code.substring(1).replace("'", "").replace(" ", "");
    }

    public String selectIndex(Context c, int indexOnPage) {
        List<Candidate> pageItems = pageItems();
        int idx = indexOnPage;
        if (idx < 0 || idx >= pageItems.size()) return null;
        Candidate cand = pageItems.get(idx);
        return commitText(cand.text);
    }

    public String selectAbsolute(int index) {
        if (index < 0 || index >= all.size()) return null;
        return commitText(all.get(index).text);
    }

    public String commitFirstOrNull() {
        if (all.isEmpty()) return null;
        return commitText(all.get(0).text);
    }

    public String commitRaw() {
        if (code.length() == 0) return null;
        String text = code;
        return commitText(text);
    }

    public void noteLastCommit(String text) {
        if (text != null && text.length() > 0) lastCommit = text;
    }

    private String commitText(String text) {
        lastCommit = text;
        code = "";
        page = 0;
        all = Collections.emptyList();
        specialCount = 0;
        return text;
    }

    private String applyFourRules(Context c) {
        if (code.length() != 4 || code.charAt(0) == 'z') return null;
        if (DateTimeCandidates.candidates(code) != null) return null;
        if (all.isEmpty() && Prefs.clearEmptyCode(c)) {
            clear();
            return null;
        }
        int rimeCount = all.size() - specialCount;
        if (Prefs.autoSelectUniqueFourCode(c) && rimeCount == 1 && specialCount == 0) {
            return commitFirstOrNull();
        }
        return null;
    }

    private void rebuild(Context c) {
        specialCount = 0;
        if (code.length() == 0) {
            all = Collections.emptyList();
            return;
        }
        ArrayList<Candidate> list = new ArrayList<Candidate>();
        List<String> special = DateTimeCandidates.candidates(code);
        if (special != null) {
            for (String s : special) list.add(new Candidate(s, "", true));
            specialCount = list.size();
        } else if (isSoloZ(code) && lastCommit.length() > 0) {
            list.add(new Candidate(lastCommit, "", true));
            specialCount = 1;
        }

        List<DictEntry> hits;
        if (code.charAt(0) == 'z' && code.length() > 1) {
            String py = code.substring(1).replace("'", "");
            hits = table.lookupPinyin(py);
        } else if (code.charAt(0) == 'z') {
            hits = Collections.emptyList();
        } else {
            hits = table.lookupWubi(code);
        }
        boolean comments = Prefs.showComment(c);
        boolean reverse = reverseLookup();
        for (DictEntry e : hits) {
            if (specialCount > 0 && e.text.equals(lastCommit) && isSoloZ(code)) continue;
            String note;
            if (reverse) {
                String wb = table.wubiCodeOf(e.text);
                note = wb.length() > 0 ? wb : e.code;
            } else if (comments) {
                note = e.note.length() > 0 ? e.note : e.code;
            } else {
                note = "";
            }
            list.add(new Candidate(e.text, note, false));
        }
        all = list;
    }

    public static boolean isSoloZ(String code) {
        return code.length() == 1 && (code.charAt(0) == 'z' || code.charAt(0) == 'Z');
    }

    public String debugCode() {
        return code.toLowerCase(Locale.US);
    }
}
