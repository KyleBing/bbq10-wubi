package cn.kylebing.bbq10wubi.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;

/**
 * Q10 实体键盘经常不走 Activity.dispatchKeyEvent，而是 InputConnection.commitText。
 * 在这里把字母拦下来交给五笔引擎。
 */
public class WubiEditText extends EditText {
    public interface Listener {
        boolean onWubiKey(KeyEvent event);
        boolean onWubiCommit(CharSequence text);
        /** InputConnection 回删；与 KeyEvent 可能同时到达，由 Activity 去重。 */
        boolean onWubiDelete();
    }

    /** commitText 可能重复送达的字符键（不含回删/空格/回车）。 */
    static boolean isCommitCharKey(int code) {
        return (code >= KeyEvent.KEYCODE_A && code <= KeyEvent.KEYCODE_Z)
                || (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9)
                || code == KeyEvent.KEYCODE_PERIOD
                || code == KeyEvent.KEYCODE_COMMA
                || code == KeyEvent.KEYCODE_MINUS
                || code == KeyEvent.KEYCODE_EQUALS
                || code == KeyEvent.KEYCODE_APOSTROPHE
                || code == KeyEvent.KEYCODE_SEMICOLON
                || code == KeyEvent.KEYCODE_SLASH
                || code == KeyEvent.KEYCODE_BACKSLASH
                || code == KeyEvent.KEYCODE_GRAVE
                || code == KeyEvent.KEYCODE_LEFT_BRACKET
                || code == KeyEvent.KEYCODE_RIGHT_BRACKET;
    }

    private Listener listener;

    public WubiEditText(Context context) {
        super(context);
    }

    public WubiEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public WubiEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setWubiListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (listener != null && listener.onWubiKey(event)) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (listener != null && isTypingKey(keyCode)) return true;
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection ic = super.onCreateInputConnection(outAttrs);
        if (ic == null) return null;
        return new WubiConnection(ic, true);
    }

    static boolean isTypingKey(int code) {
        return (code >= KeyEvent.KEYCODE_A && code <= KeyEvent.KEYCODE_Z)
                || (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9)
                || code == KeyEvent.KEYCODE_SPACE
                || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_DEL
                || code == KeyEvent.KEYCODE_PERIOD
                || code == KeyEvent.KEYCODE_COMMA
                || code == KeyEvent.KEYCODE_MINUS
                || code == KeyEvent.KEYCODE_EQUALS
                || code == KeyEvent.KEYCODE_APOSTROPHE
                || code == KeyEvent.KEYCODE_SEMICOLON
                || code == KeyEvent.KEYCODE_SLASH
                || code == KeyEvent.KEYCODE_BACKSLASH
                || code == KeyEvent.KEYCODE_GRAVE
                || code == KeyEvent.KEYCODE_LEFT_BRACKET
                || code == KeyEvent.KEYCODE_RIGHT_BRACKET;
    }

    private class WubiConnection extends InputConnectionWrapper {
        WubiConnection(InputConnection target, boolean mutable) {
            super(target, mutable);
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            if (listener != null && listener.onWubiCommit(text)) return true;
            return super.commitText(text, newCursorPosition);
        }

        @Override
        public boolean sendKeyEvent(KeyEvent event) {
            if (listener != null && event.getAction() == KeyEvent.ACTION_DOWN
                    && listener.onWubiKey(event)) {
                return true;
            }
            return super.sendKeyEvent(event);
        }

        @Override
        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
            // Q10 / 软键盘常走这里而不是 KEYCODE_DEL；组词时必须删编码而非正文
            if (listener != null && beforeLength > 0 && listener.onWubiDelete()) {
                return true;
            }
            return super.deleteSurroundingText(beforeLength, afterLength);
        }
    }
}
