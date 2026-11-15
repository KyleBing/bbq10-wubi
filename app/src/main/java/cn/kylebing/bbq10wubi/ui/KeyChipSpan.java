package cn.kylebing.bbq10wubi.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;

public final class KeyChipSpan extends ReplacementSpan {
    private final int bgColor;
    private final int fgColor;
    private final int padH;
    private final int padV;
    private final float textSizePx;
    private final RectF tmp = new RectF();

    public KeyChipSpan(int bgColor, int fgColor, int padH, int padV, float textSizePx) {
        this.bgColor = bgColor;
        this.fgColor = fgColor;
        this.padH = padH;
        this.padV = padV;
        this.textSizePx = textSizePx;
    }

    private void applyTextSize(Paint paint) {
        if (textSizePx > 0f) paint.setTextSize(textSizePx);
        paint.setFakeBoldText(true);
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end,
                       Paint.FontMetricsInt fm) {
        float oldSize = paint.getTextSize();
        boolean oldBold = paint.isFakeBoldText();
        applyTextSize(paint);
        int width = (int) Math.ceil(paint.measureText(text, start, end)) + padH * 2;
        if (fm != null) {
            Paint.FontMetricsInt pfm = paint.getFontMetricsInt();
            fm.ascent = pfm.ascent - padV;
            fm.descent = pfm.descent + padV;
            fm.top = pfm.top - padV;
            fm.bottom = pfm.bottom + padV;
        }
        paint.setTextSize(oldSize);
        paint.setFakeBoldText(oldBold);
        return width;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end,
                     float x, int top, int y, int bottom, Paint paint) {
        float oldSize = paint.getTextSize();
        boolean oldBold = paint.isFakeBoldText();
        int oldColor = paint.getColor();
        applyTextSize(paint);
        Paint.FontMetricsInt fm = paint.getFontMetricsInt();
        float width = paint.measureText(text, start, end) + padH * 2;
        tmp.set(x, y + fm.ascent - padV, x + width, y + fm.descent + padV);
        paint.setColor(bgColor);
        canvas.drawRect(tmp, paint);
        paint.setColor(fgColor);
        canvas.drawText(text, start, end, x + padH, y, paint);
        paint.setTextSize(oldSize);
        paint.setFakeBoldText(oldBold);
        paint.setColor(oldColor);
    }
}
