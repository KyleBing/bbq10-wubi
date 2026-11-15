package cn.kylebing.bbq10wubi.data;

public final class UserDictEntry {
    public String code;
    public String text;
    public Integer weight;
    public String note;

    public UserDictEntry(String code, String text, Integer weight, String note) {
        this.code = code == null ? "" : code.toLowerCase();
        this.text = text == null ? "" : text;
        this.weight = weight;
        this.note = note == null ? "" : note;
    }

    public String id() {
        return code + "|" + text;
    }
}
