package com.example.ledjacket.graphs;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;

public class BeatVisualizer extends GraphThread {

    private volatile float[] secondData;

    public BeatVisualizer(Context context) {
        super(context);
        LOG_TAG = "Beat Visualizer";
    }

    public BeatVisualizer(Context context, AttributeSet attrs) {
        super(context, attrs);
        LOG_TAG = "Beat Visualizer";
    }

    public BeatVisualizer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        LOG_TAG = "Beat Visualizer";
    }

    private void setSecondData(float[] data) {
        if(data.length > width) {
            secondData = new float[width];
            System.arraycopy(data, 0, secondData, 0, width); // deep copy
        } else {
            secondData = data;
            //waveData = new float[data.length];
            //.arraycopy(data, 0, waveData, 0, data.length);
        }
    }

    @Override
    protected void getDataFromMiddleman() { // BOTH THE LISTS MUST BE THE EXACT SAME SIZE
        if(middleman != null && !middleman.isOscEmpty()) {
            try {
                setWaveData(middleman.getBeatData());
                setSecondData(middleman.getLuminosityData());
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

        //paint.setColor(Color.WHITE);

        //canvas.drawLine(0, height/2, width, height/2, paint);

        paint.setColor(Color.RED);

        float[] points = new float[waveData.length * 4];

        // This introduces clicks, when AudioThread is refreshing too fast

        int j = 0;
        float prevx = 0, prevy = height * waveData[0];

        for (int i = 1; i < waveData.length; i++) {
            j = (i - 1) * 4;
            points[j] = prevx;
            points[j + 1] = prevy;
            points[j + 2] = (float) i * width / (waveData.length - 1);
            points[j + 3] = height * waveData[i]; // randomly sets values to 0 or height
            prevx = points[j + 2];
            prevy = points[j + 3];
        }

        // This removes clicks:

        // TODO: dont reset points to 0
        
        /*for(int i = 0; i < waveData.length * 2; i++) {
            if(points[i*2+1] < 0 || points[i*2+1] > height) {
                points[i*2+1] = height / 2;
            }
        }*/

        canvas.drawLines(points, paint);

        paint.setColor(Color.YELLOW);

        prevy = height * secondData[0];

        for (int i = 1; i < waveData.length; i++) {
            j = (i - 1) * 4;
            points[j + 1] = prevy;
            points[j + 3] = height * secondData[i]; // randomly sets values to 0 or height
            prevy = points[j + 3];
        }

        canvas.drawLines(points, paint);
    }
}