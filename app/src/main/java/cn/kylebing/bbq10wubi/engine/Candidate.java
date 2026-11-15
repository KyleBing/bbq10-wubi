package cn.kylebing.bbq10wubi.engine;

public final class Candidate {
    public final String text;
    public final String comment;
    public final boolean special;

    public Candidate(String text, String comment, boolean special) {
        this.text = text;
        this.comment = comment == null ? "" : comment;
        this.special = special;
    }
}
