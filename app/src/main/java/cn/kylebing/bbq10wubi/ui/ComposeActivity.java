package cn.kylebing.bbq10wubi.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import cn.kylebing.bbq10wubi.Prefs;
import cn.kylebing.bbq10wubi.R;
import cn.kylebing.bbq10wubi.WubiApp;
import cn.kylebing.bbq10wubi.data.PunctuationPrefs;
import cn.kylebing.bbq10wubi.engine.Candidate;
import cn.kylebing.bbq10wubi.engine.WubiSession;
import cn.kylebing.bbq10wubi.sync.UserDictAutoSync;
import cn.kylebing.bbq10wubi.sync.WubiCloudConfig;

public class ComposeActivity extends Activity implements WubiEditText.Listener {
    private WubiEditText editor;
    private TextView preeditText;
    private TextView langBadge;
    private TextView statusText;
    private LinearLayout candRow;
    private WubiApp app;
    private WubiSession session;
    private boolean english;
    private final PunctuationPrefs.PairState quotePair = new PunctuationPrefs.PairState();
    private List<String> punct = new ArrayList<String>();
    private String caretTail = "";
    private boolean ateFromCommit;
    /** KEYCODE_DEL 与 deleteSurroundingText 成对到达时，同一脉冲只处理一次。 */
    private boolean deletePulse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compose);
        app = (WubiApp) getApplication();
        session = app.session();
        editor = (WubiEditText) findViewById(R.id.editor);
        editor.setWubiListener(this);
        preeditText = (TextView) findViewById(R.id.preeditText);
        langBadge = (TextView) findViewById(R.id.langBadge);
        statusText = (TextView) findViewById(R.id.statusText);
        candRow = (LinearLayout) findViewById(R.id.candRow);
        if (Prefs.p(this).getInt("input.lang.reset", 0) < 2) {
            Prefs.setEnglishMode(this, false);
            Prefs.p(this).edit().putInt("input.lang.reset", 2).apply();
        }
        english = Prefs.englishMode(this);
        hideIme();

        findViewById(R.id.btnCopy).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyAll();
            }
        });
        findViewById(R.id.btnClear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClear();
            }
        });
        findViewById(R.id.btnMore).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMore();
            }
        });
        langBadge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleEnglish();
            }
        });

        if (!app.isReady()) {
            statusText.setText("正在加载词库…");
        }
        app.whenReady(new Runnable() {
            @Override
            public void run() {
                if (app.initError() != null) {
                    statusText.setText("词库失败: " + app.initError());
                    Toast.makeText(ComposeActivity.this, app.initError(), Toast.LENGTH_LONG).show();
                } else {
                    statusText.setText(app.stats());
                }
                refreshBar();
            }
        });
        refreshBar();
    }

    @Override
    protected void onResume() {
        super.onResume();
        english = Prefs.englishMode(this);
        hideIme();
        editor.requestFocus();
        UserDictAutoSync.activate(this);
        String auto = UserDictAutoSync.lastResult(this);
        if (auto != null && app.isReady()) statusText.setText(auto);
        refreshBar();
    }

    private void hideIme() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
        }
    }

    @Override
    public boolean onWubiKey(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return WubiEditText.isTypingKey(event.getKeyCode());
        }
        // 只吞 commitText 后重复的字母/符号，不能吞回删（否则有编码时删不到候选码）
        if (ateFromCommit && WubiEditText.isCommitCharKey(event.getKeyCode())) {
            ateFromCommit = false;
            return true;
        }
        ateFromCommit = false;
        return handleTyping(event);
    }

    @Override
    public boolean onWubiCommit(CharSequence text) {
        if (text == null || text.length() != 1) return false;
        boolean ok = processChar(text.charAt(0), false);
        if (ok) ateFromCommit = true;
        return ok;
    }

    @Override
    public boolean onWubiDelete() {
        handleDelete();
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (WubiEditText.isTypingKey(code) || code == KeyEvent.KEYCODE_SHIFT_LEFT
                    || code == KeyEvent.KEYCODE_SHIFT_RIGHT
                    || code == KeyEvent.KEYCODE_ALT_LEFT
                    || code == KeyEvent.KEYCODE_ALT_RIGHT) {
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (session.composing() || !punct.isEmpty()) {
                punct.clear();
                session.clear();
                refreshBar();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showMore();
            return true;
        }
        if (handleTyping(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    private boolean handleTyping(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT
                || keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT
                || keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
            return false;
        }
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            handleDelete();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            handleReturn();
            ateFromCommit = true;
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            handleSpace();
            ateFromCommit = true;
            return true;
        }
        char ch = resolveChar(event);
        if (ch == 0) return false;
        return processChar(ch, event.isShiftPressed());
    }

    private char resolveChar(KeyEvent event) {
        int unicode = event.getUnicodeChar();
        if (unicode >= 32 && unicode < 0x10000) return (char) unicode;
        int keyCode = event.getKeyCode();
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            char c = (char) ('a' + (keyCode - KeyEvent.KEYCODE_A));
            if (event.isShiftPressed() || event.isCapsLockOn()) c = Character.toUpperCase(c);
            return c;
        }
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return (char) ('0' + (keyCode - KeyEvent.KEYCODE_0));
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_PERIOD: return '.';
            case KeyEvent.KEYCODE_COMMA: return ',';
            case KeyEvent.KEYCODE_MINUS: return '-';
            case KeyEvent.KEYCODE_EQUALS: return '=';
            case KeyEvent.KEYCODE_APOSTROPHE: return '\'';
            case KeyEvent.KEYCODE_SEMICOLON: return ';';
            case KeyEvent.KEYCODE_SLASH: return '/';
            case KeyEvent.KEYCODE_BACKSLASH: return '\\';
            case KeyEvent.KEYCODE_GRAVE: return '`';
            case KeyEvent.KEYCODE_LEFT_BRACKET: return '[';
            case KeyEvent.KEYCODE_RIGHT_BRACKET: return ']';
            default: break;
        }
        if (unicode > 0 && unicode < 0x10000) return (char) unicode;
        return 0;
    }

    private boolean processChar(char ch, boolean shift) {
        if (ch == ' ') {
            handleSpace();
            return true;
        }
        if (english) {
            insert("" + ch);
            return true;
        }
        if (!punct.isEmpty()) {
            return handleWhilePunct(ch);
        }
        if (session.composing()) {
            if (ch >= '1' && ch <= '9') {
                commitOrIgnore(session.selectIndex(this, ch - '1'));
                refreshBar();
                return true;
            }
            if (ch == ',' || ch == '-') {
                session.pagePrev();
                refreshBar();
                return true;
            }
            if (ch == '.' || ch == '=') {
                session.pageNext();
                refreshBar();
                return true;
            }
            if ((ch >= 'A' && ch <= 'Z') && shift) {
                String first = session.commitFirstOrNull();
                if (first != null) insert(first);
                session.clear();
                insert("" + ch);
                refreshBar();
                return true;
            }
            if (ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z') {
                if (!app.isReady()) {
                    Toast.makeText(this, "词库尚未就绪", Toast.LENGTH_SHORT).show();
                    return true;
                }
                String auto = session.typeLetter(this, ch);
                if (auto != null) insert(auto);
                refreshBar();
                return true;
            }
            // 组词中其它符号：先上屏首选再标点
            String first = session.commitFirstOrNull();
            if (first != null) insert(first);
            session.clear();
            applyPunctuation("" + ch);
            refreshBar();
            return true;
        }
        if ((ch >= 'A' && ch <= 'Z') && shift) {
            insert("" + ch);
            return true;
        }
        if (ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z') {
            if (!app.isReady()) {
                Toast.makeText(this, "词库尚未就绪", Toast.LENGTH_SHORT).show();
                return true;
            }
            String auto = session.typeLetter(this, ch);
            if (auto != null) insert(auto);
            refreshBar();
            return true;
        }
        if (ch >= '0' && ch <= '9') {
            insert("" + ch);
            return true;
        }
        applyPunctuation("" + ch);
        refreshBar();
        return true;
    }

    private boolean handleWhilePunct(char ch) {
        if (ch >= '1' && ch <= '9') {
            int i = ch - '1';
            if (i < punct.size()) commitPunct(punct.get(i), false);
            refreshBar();
            return true;
        }
        if (PunctuationPrefs.item("" + ch) != null) {
            applyPunctuation("" + ch);
            refreshBar();
            return true;
        }
        punct.clear();
        if (ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z') {
            String auto = session.typeLetter(this, ch);
            if (auto != null) insert(auto);
        } else {
            insert("" + ch);
        }
        refreshBar();
        return true;
    }

    private void handleSpace() {
        if (!punct.isEmpty()) {
            commitPunct(punct.get(0), false);
            refreshBar();
            return;
        }
        if (english) {
            insert(" ");
            return;
        }
        if (session.composing()) {
            List<Candidate> page = session.pageItems();
            if (!page.isEmpty() && page.get(0).special) {
                commitOrIgnore(session.selectIndex(this, 0));
            } else {
                String text = session.commitFirstOrNull();
                if (text != null) insert(text);
                else {
                    String raw = session.commitRaw();
                    if (raw != null) insert(raw);
                }
            }
            refreshBar();
            return;
        }
        insert(" ");
    }

    private void handleReturn() {
        if (!punct.isEmpty()) {
            punct.clear();
            refreshBar();
            return;
        }
        if (session.composing()) {
            if (Prefs.enterClearsCode(this)) {
                session.clear();
                refreshBar();
                return;
            }
            String raw = session.commitRaw();
            if (raw != null) insert(raw);
            refreshBar();
            return;
        }
        insert("\n");
    }

    private void handleDelete() {
        // KEYCODE_DEL 与 deleteSurroundingText 常成对到达；同一脉冲只退一次，
        // 否则剩一码时第二次会落到正文上。
        if (deletePulse) return;
        deletePulse = true;
        editor.post(new Runnable() {
            @Override
            public void run() {
                deletePulse = false;
            }
        });

        if (!punct.isEmpty()) {
            punct.clear();
            refreshBar();
            return;
        }
        // 有候选编码时只退码，绝不删正文
        if (session.composing()) {
            session.deleteCode();
            if (session.composing()) session.refresh(this);
            refreshBar();
            return;
        }
        Editable ed = editor.getText();
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        if (start < 0) start = ed.length();
        if (end < 0) end = start;
        if (start != end) {
            ed.delete(Math.min(start, end), Math.max(start, end));
        } else if (start > 0) {
            ed.delete(start - 1, start);
            if (caretTail.length() > 0) caretTail = caretTail.substring(0, caretTail.length() - 1);
        }
    }

    private void applyPunctuation(String key) {
        List<String> symbols = PunctuationPrefs.symbols(key);
        PunctuationCommit half = halfWidthDecision(symbols.isEmpty() ? key : symbols.get(0), contextBefore(2));
        if (half != null) {
            commitPunct(half.text, half.replacePrevious);
            return;
        }
        if (symbols.size() <= 1 || "\"".equals(key) || "'".equals(key)) {
            commitPunct(quotePair.directSymbol(key, symbols), false);
            return;
        }
        punct = symbols;
        session.clear();
    }

    private static final class PunctuationCommit {
        final String text;
        final boolean replacePrevious;

        PunctuationCommit(String text, boolean replacePrevious) {
            this.text = text;
            this.replacePrevious = replacePrevious;
        }
    }

    private PunctuationCommit halfWidthDecision(String primary, String prefix) {
        String[][] rules = {{"。", "."}, {"，", ","}, {"：", ":"}};
        String ascii = null;
        for (int i = 0; i < rules.length; i++) {
            if (rules[i][0].equals(primary)) ascii = rules[i][1];
        }
        if (ascii == null || prefix == null || prefix.length() == 0) return null;
        char last = prefix.charAt(prefix.length() - 1);
        if (isDigit(last)) return new PunctuationCommit(ascii, false);
        if (String.valueOf(last).equals(ascii) && prefix.length() >= 2) {
            char prev = prefix.charAt(prefix.length() - 2);
            if (isDigit(prev)) return new PunctuationCommit(primary, true);
        }
        return null;
    }

    private boolean isDigit(char ch) {
        return (ch >= '0' && ch <= '9') || (ch >= '０' && ch <= '９');
    }

    private String contextBefore(int limit) {
        Editable ed = editor.getText();
        int start = editor.getSelectionStart();
        if (start <= 0) return caretTail.length() == 0 ? null : tail(caretTail, limit);
        int from = Math.max(0, start - limit);
        caretTail = ed.subSequence(Math.max(0, start - 8), start).toString();
        return ed.subSequence(from, start).toString();
    }

    private static String tail(String s, int n) {
        return s.length() <= n ? s : s.substring(s.length() - n);
    }

    private void commitPunct(String text, boolean replacePrevious) {
        punct.clear();
        if (replacePrevious) {
            handleDelete();
        }
        insert(text);
    }

    private void commitOrIgnore(String text) {
        if (text != null) insert(text);
    }

    private void insert(String text) {
        if (text == null || text.length() == 0) return;
        session.noteLastCommit(text);
        int start = Math.max(0, editor.getSelectionStart());
        int end = Math.max(start, editor.getSelectionEnd());
        editor.getText().replace(start, end, text);
        caretTail = tail(caretTail + text, 8);
    }

    private void toggleEnglish() {
        if (session.composing()) session.clear();
        punct.clear();
        english = !english;
        Prefs.setEnglishMode(this, english);
        refreshBar();
    }

    private void refreshBar() {
        langBadge.setText(english ? "En" : "中");
        if (!punct.isEmpty()) {
            preeditText.setText("标点");
            renderCandidates(punctToCands(punct), 0, 1);
            return;
        }
        if (session.composing()) {
            int pages = session.pageCount();
            String page = pages > 1 ? (" · " + (session.pageIndex() + 1) + "/" + pages) : "";
            preeditText.setText(session.code() + page);
            renderCandidates(session.pageItems(), session.pageIndex() * WubiSession.PAGE_SIZE, pages);
        } else {
            preeditText.setText(english ? "英文直出  点「En」切中文" : "空格选1  数字选词  点「中」切英文");
            candRow.removeAllViews();
        }
    }

    private List<Candidate> punctToCands(List<String> symbols) {
        ArrayList<Candidate> list = new ArrayList<Candidate>();
        for (String s : symbols) list.add(new Candidate(s, "", true));
        return list;
    }

    private void renderCandidates(List<Candidate> items, final int absOffset, int pages) {
        candRow.removeAllViews();
        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            Candidate cand = items.get(i);
            Button btn = new Button(this);
            btn.setBackgroundResource(R.drawable.cand_item);
            String label = (i + 1) + " " + cand.text;
            if (cand.comment.length() > 0) label = label + " " + cand.comment;
            btn.setText(label);
            btn.setTextColor(0xFFFFFFFF);
            btn.setTextSize(15);
            btn.setPadding(12, 8, 12, 8);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = 4;
            lp.rightMargin = 4;
            btn.setLayoutParams(lp);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!punct.isEmpty()) {
                        if (index < punct.size()) commitPunct(punct.get(index), false);
                    } else {
                        commitOrIgnore(session.selectIndex(ComposeActivity.this, index));
                    }
                    refreshBar();
                }
            });
            candRow.addView(btn);
        }
        if (pages > 1) {
            TextView hint = new TextView(this);
            hint.setText("  ,/. 翻页");
            hint.setTextColor(0xFF9A9A9A);
            hint.setGravity(Gravity.CENTER_VERTICAL);
            candRow.addView(hint);
        }
    }

    private void copyAll() {
        String text = editor.getText().toString();
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("wubi", text));
            Toast.makeText(this, "已复制 " + text.length() + " 字", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setMessage("清空正文？")
                .setPositiveButton("清空", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        editor.setText("");
                        session.clear();
                        punct.clear();
                        refreshBar();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showMore() {
        final boolean logged = WubiCloudConfig.isLoggedIn(this);
        CharSequence[] items = new CharSequence[] {
                "设置",
                logged ? "同步账号（已登录）" : "同步账号",
                "用法",
                english ? "切换到中文" : "切换到英文"
        };
        new AlertDialog.Builder(this)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) startActivity(new Intent(ComposeActivity.this, SettingsActivity.class));
                        else if (which == 1) startActivity(new Intent(ComposeActivity.this, AccountActivity.class));
                        else if (which == 2) startActivity(new Intent(ComposeActivity.this, HelpActivity.class));
                        else toggleEnglish();
                    }
                })
                .show();
    }
}
