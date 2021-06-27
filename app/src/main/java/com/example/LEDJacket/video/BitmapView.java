package com.example.ledjacket.video;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.example.ledjacket.Middleman;

public class BitmapView extends View {

    private Bitmap bmp = null;

    private void init() {
        //setFocusable(true);
    }

    public BitmapView(Context context) {
        super(context);
        init();
    }

    public BitmapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BitmapView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public void setBitmap(Bitmap bmp) {
        this.bmp = bmp;
    }

    // On draw
    @Override
    protected void onDraw(Canvas canvas) {
        if(bmp != null) {
            canvas.drawBitmap(bmp, 0, 0, null);
        }
    }
}
