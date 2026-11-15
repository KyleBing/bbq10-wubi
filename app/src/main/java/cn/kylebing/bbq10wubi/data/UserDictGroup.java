package cn.kylebing.bbq10wubi.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class UserDictGroup {
    public final String id;
    public String name;
    public final List<UserDictEntry> entries = new ArrayList<UserDictEntry>();

    public UserDictGroup(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name == null ? "" : name;
    }

    public UserDictGroup(String id, String name) {
        this.id = id == null ? UUID.randomUUID().toString() : id;
        this.name = name == null ? "" : name;
    }
}
