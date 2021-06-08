package com.example.ledjacket;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;

public class BeatVisualizer extends SurfaceViewThread {

    public BeatVisualizer(Context context) {
        super(context);
        LOG_TAG = "Oscilloscope Visualizer";
    }

    public BeatVisualizer(Context context, AttributeSet attrs) {
        super(context, attrs);
        LOG_TAG = "Oscilloscope Visualizer";
    }

    public BeatVisualizer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        LOG_TAG = "Oscilloscope Visualizer";
    }

    @Override
    protected void getDataFromMiddleman() {
        if(middleman != null && !middleman.isOscEmpty()) {
            try {
                setWaveData(middleman.getOscData());
            }catch (InterruptedException ex) {
                Log.e(LOG_TAG, ex.getMessage());
            }
        }
    }

    @Override
    protected void draw() {
        // clear the screen using black
        canvas.drawARGB(255, 16, 16, 16);

        // rearrange coords so 0,0 is bottom left
        canvas.translate(0, height);
        canvas.scale(1, -1);

        paint.setColor(Color.WHITE);

        canvas.drawLine(0, height/2, width, height/2, paint);

        paint.setColor(Color.RED);

        float[] points = new float[waveData.length * 4];

        int j = 0;
        float prevx = 0, prevy = height * waveData[0];

        for (int i = 1; i < waveData.length; i++) {
            j = (i - 1) * 4;
            points[j] = prevx;
            points[j + 1] = prevy;
            points[j + 2] = (float) i * width / (waveData.length - 1);
            points[j + 3] = height * waveData[i];
            prevx = points[j + 2];
            prevy = points[j + 3];
        }

        canvas.drawLines(points, paint);
    }
}