package cn.kylebing.bbq10wubi.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class PunctuationPrefs {
    private PunctuationPrefs() {}

    public static final class Item {
        public final String key;
        public final String[] symbols;

        Item(String key, String[] symbols) {
            this.key = key;
            this.symbols = symbols;
        }
    }

    public static final Item[] ITEMS = new Item[] {
            new Item(",", new String[] {"，"}),
            new Item(".", new String[] {"。"}),
            new Item("<", new String[] {"《"}),
            new Item(">", new String[] {"》"}),
            new Item("/", new String[] {"/"}),
            new Item("?", new String[] {"？"}),
            new Item(";", new String[] {"；"}),
            new Item(":", new String[] {"："}),
            new Item("'", new String[] {"‘", "’"}),
            new Item("\"", new String[] {"“", "”"}),
            new Item("\\", new String[] {"、"}),
            new Item("|", new String[] {"|"}),
            new Item("`", new String[] {"`"}),
            new Item("~", new String[] {"~"}),
            new Item("!", new String[] {"！"}),
            new Item("@", new String[] {"@"}),
            new Item("#", new String[] {"#"}),
            new Item("%", new String[] {"%", "％", "°", "℃"}),
            new Item("$", new String[] {"￥"}),
            new Item("^", new String[] {"……"}),
            new Item("&", new String[] {"&"}),
            new Item("*", new String[] {"*"}),
            new Item("(", new String[] {"（"}),
            new Item(")", new String[] {"）"}),
            new Item("-", new String[] {"-"}),
            new Item("_", new String[] {"——"}),
            new Item("+", new String[] {"+"}),
            new Item("=", new String[] {"="}),
            new Item("[", new String[] {"【"}),
            new Item("]", new String[] {"】"}),
            new Item("{", new String[] {"「"}),
            new Item("}", new String[] {"」"})
    };

    public static Item item(String key) {
        for (Item it : ITEMS) {
            if (it.key.equals(key)) return it;
        }
        return null;
    }

    public static List<String> symbols(String key) {
        Item it = item(key);
        ArrayList<String> out = new ArrayList<String>();
        if (it == null) {
            out.add(key);
            return out;
        }
        for (String s : it.symbols) out.add(s);
        return out;
    }

    public static final class PairState {
        private final HashMap<String, Boolean> closingNext = new HashMap<String, Boolean>();

        public void reset() {
            closingNext.clear();
        }

        public String directSymbol(String key, List<String> symbols) {
            String primary = symbols.isEmpty() ? key : symbols.get(0);
            if (symbols.size() < 2 || (!"\"".equals(key) && !"'".equals(key))) return primary;
            Boolean closing = closingNext.get(key);
            boolean isClosing = closing != null && closing.booleanValue();
            closingNext.put(key, Boolean.valueOf(!isClosing));
            return isClosing ? symbols.get(1) : primary;
        }
    }
}
