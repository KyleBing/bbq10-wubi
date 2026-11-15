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

    public DictEntry(String text, String code, int weight, String note, int source) {
        this.text = text;
        this.code = code;
        this.weight = weight;
        this.note = note == null ? "" : note;
        this.source = source;
    }

    public int rank() {
        return source * 100000 + weight;
    }
}
