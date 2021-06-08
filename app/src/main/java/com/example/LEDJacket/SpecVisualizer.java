package com.example.ledjacket;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Log;

// Courtesy of https://github.com/billthefarmer/scope/blob/2963e0e1b4ad8a68b81a6a1253b9b81dea8ba15e/src/main/java/org/billthefarmer/scope/Spectrum.java

public class SpecVisualizer extends SurfaceViewThread {
    private Bitmap graticule;

    private Path path;

    public SpecVisualizer(Context context) {
        super(context);
        LOG_TAG = "Spectrum Visualizer";
        path = new Path();
    }

    public SpecVisualizer(Context context, AttributeSet attrs) {
        super(context, attrs);
        LOG_TAG = "Spectrum Visualizer";
        path = new Path();
    }

    public SpecVisualizer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        LOG_TAG = "Spectrum Visualizer";
        path = new Path();
    }

    private void drawGraticule() {
        graticule = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        Canvas c = new Canvas(graticule);

        // Black background
        c.drawARGB(255, 16, 16, 16);

        // Set up paint
        paint.setStrokeWidth(2);
        //paint.setAntiAlias(false);
        //paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.argb(255, 0, 63, 0));

        // Calculate x scale
        float xscale = (float) Math.log(waveData.length) / width;

        // Draw graticule
        float fa[] = {1, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.6f, 1.7f, 1.8f, 1.9f, 2, 2.2f, 2.5f, 3, 3.5f, 4, 4.5f, 5, 6, 7, 8, 9};
        float ma[] = {1, 10, 100, 1000, 10000};

        for (float m : ma) { // VERTICAL LINES
            for (float f : fa) {
                float x = (float) Math.log((f * m) / 43.066f) / xscale;  // frequency of band k is k * samplerate / window size, in this case 44100 / 1024 = 43.066
                c.drawLine(x, 0, x, height, paint);
            }
        }

        for (int i = 0; i < 10; i ++) { // HORIZONTAL LINES
            float y = (float) (Math.sqrt(i/10.0) * height);
            c.drawLine(0, y, width, y, paint);
        }
    }

    @Override
    protected void getDataFromMiddleman() {
        if(middleman != null && !middleman.isOscEmpty()) {
            try {
                setWaveData(middleman.getSpecData());
            }catch (InterruptedException ex) {
                Log.e(LOG_TAG, ex.getMessage());
            }
        }
    }

    @Override
    protected void draw() {
        if (graticule == null || graticule.getWidth() != width || graticule.getHeight() != height) {
            drawGraticule(); //recreate the graticule because things changed
        }

        // rearrange coords so 0,0 is bottom left
        canvas.translate(0, height);
        canvas.scale(1, -1);

        // clear the screen using black
        //canvas.drawARGB(255, 16, 16, 16);

        // Draw the graticule
        canvas.drawBitmap(graticule, 0, 0, null);

        // Rewind path
        path.rewind();
        path.moveTo(0, 0);

        float xscale = (float) Math.log(waveData.length) / width;

        // Create trace
        int last = 1;
        for (int x = 0; x < width; x++) { // This does log frequency scale
            float value = 0.0f;

            int index = (int) Math.round(Math.pow(Math.E, x * xscale));
            if (index == last)
                continue;

            for (int i = last; i <= index; i++) {
                // Don't show DC component and don't overflow
                if (i > 0 && i < waveData.length) {
                    if (value < waveData[i])
                        value = (float) waveData[i];
                }
            }

            // Update last index
            last = index;

            float y;

            if(value < 0 || value > 1) { // Catch clicks
                y = 0;
            } else {
                y = value * height; // sqrt volume scale, happens in audiothread
            }                          //TODO: move here?

            path.lineTo(x, y);
        }

        // Color green
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);

        // Draw path
        canvas.drawPath(path, paint);

        // Complete path for fill
        path.lineTo(width, 0);
        path.close();

        // Colour translucent green
        paint.setColor(Color.argb(63, 0, 255, 0));
        paint.setStyle(Paint.Style.FILL);

        // Fill path
        canvas.drawPath(path, paint);
    }
}