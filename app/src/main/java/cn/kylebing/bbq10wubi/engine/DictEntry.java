package cn.kylebing.bbq10wubi.engine;

public final class DictEntry {
    public static final int SRC_MAIN = 0;
    public static final int SRC_EXTRA = 1;
    public static final int SRC_USER = 2;
    public static final int SRC_PINYIN = 3;

    public final String text;
    public final String code;
    public final int weight;
    public final String note;
    public final int source;
    public final int ord;

    public DictEntry(String text, String code, int weight, String note, int source, int ord) {
        this.text = text;
        this.code = code;
        this.weight = weight;
        this.note = note == null ? "" : note;
        this.source = source;
        this.ord = ord;
    }

    /** 对齐 Rime table_translator：整码优先，同码按 weight，再按词库出现顺序。 */
    public static int compareRime(DictEntry a, DictEntry b) {
        int c = a.code.length() - b.code.length();
        if (c != 0) return c;
        c = b.weight - a.weight;
        if (c != 0) return c;
        return a.ord - b.ord;
    }
}
