package cn.kylebing.bbq10wubi.ui;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.widget.TextView;

import cn.kylebing.bbq10wubi.R;

public class HelpActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        HomeNav.bind(this);
        TextView body = (TextView) findViewById(R.id.helpBody);
        float chipSp = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 12f, getResources().getDisplayMetrics());
        int padH = Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 4f, getResources().getDisplayMetrics()));
        int padV = Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 1.5f, getResources().getDisplayMetrics()));
        body.setText(formatHelp(getString(R.string.help_body), chipSp, padH, padV));
    }

    static CharSequence formatHelp(String raw, float chipTextPx, int chipPadH, int chipPadV) {
        if (raw == null) return "";
        SpannableStringBuilder out = new SpannableStringBuilder();
        int i = 0;
        while (i < raw.length()) {
            int open = raw.indexOf('[', i);
            if (open < 0) {
                out.append(raw.substring(i));
                break;
            }
            if (open > i) out.append(raw.substring(i, open));
            int closeName = raw.indexOf(']', open);
            if (closeName < 0) {
                out.append(raw.substring(open));
                break;
            }
            String name = raw.substring(open + 1, closeName);
            if (name.startsWith("/")) {
                out.append(raw.substring(open, closeName + 1));
                i = closeName + 1;
                continue;
            }
            String closeTag = "[/" + name + "]";
            int close = raw.indexOf(closeTag, closeName + 1);
            if (close < 0) {
                out.append(raw.substring(open));
                break;
            }
            String inner = raw.substring(closeName + 1, close);
            int start = out.length();
            out.append(inner);
            int end = out.length();
            if ("h".equals(name)) {
                out.setSpan(new AbsoluteSizeSpan(17, true), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new ForegroundColorSpan(0xFFFFFFFF), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if ("k".equals(name)) {
                out.setSpan(new KeyChipSpan(0xFF2A4A7A, 0xFFFFFFFF, chipPadH, chipPadV, chipTextPx),
                        start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if ("f".equals(name)) {
                out.setSpan(new ForegroundColorSpan(0xFF7DCE82), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            i = close + closeTag.length();
        }
        return out;
    }
}
