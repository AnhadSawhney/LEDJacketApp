package com.example.ledjacket;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceView;
import android.view.SurfaceHolder;

import java.util.logging.Logger;

/**
 * Created by zhaosong on 2018/6/17.
 */

// Courtesy of https://www.dev2qa.com/android-draw-surfaceview-in-thread-example/

public class SurfaceViewThread extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private SurfaceHolder surfaceHolder = null;

    private Paint paint = null;

    private Thread thread = null;

    // Record whether the child thread is running or not.
    private boolean threadRunning = false;

    //True when the surface is ready to draw
    private boolean surfaceReady = false;

    private Canvas canvas = null;

    private int width = 0;

    private int height = 0;

    private static String LOG_TAG = "SURFACE_VIEW_THREAD";

    private volatile float[] waveData;

    private void init() {
        //setFocusable(true);

        // Get SurfaceHolder object.
        surfaceHolder = this.getHolder();
        // Add current object as the callback listener.
        surfaceHolder.addCallback(this);

        // Create the paint object which will draw the text.
        paint = new Paint();
        //paint.setTextSize(100);
        //paint.setColor(Color.GREEN);
        paint.setAntiAlias(true);

        // Set the SurfaceView object at the top of View object.
        setZOrderOnTop(true);

        //setBackgroundColor(Color.RED);
    }

    public SurfaceViewThread(Context context) {
        super(context);
        init();
    }

    public SurfaceViewThread(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SurfaceViewThread(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {

        this.surfaceHolder = surfaceHolder;

        if (thread != null)
        {
            Log.d(LOG_TAG, "draw thread still active");
            // Set thread running flag to true.
            try
            {
                thread.join();
            } catch (InterruptedException e)
            { // do nothing
            }
        } else {
            // Create the child thread when SurfaceView is created.
            thread = new Thread(this);
            // Start to run the child thread.
            thread.start();
        }

        // Set flags to true.
        threadRunning = true;
        surfaceReady = true;

        // Get screen width and height.
        height = getHeight();
        width = getWidth();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        if (width == 0 || height == 0)
        {
            return;
        }

        // resize your UI
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        // Set thread running flag to false when Surface is destroyed.
        // Then the thread will jump out the while loop and complete.
        threadRunning = false;

        if (thread == null) {
            Log.d(LOG_TAG, "DrawThread is null");
        }

        /*while (true)
        {
            try
            {
                Log.d(LOG_TAG, "Request last frame");
                drawThread.join(5000);
                break;
            } catch (Exception e)
            {
                Log.e(LOG_TAG, "Could not join with draw thread");
            }
        }*/

        thread = null;

        // and release the surface
        surfaceHolder.getSurface().release();

        this.surfaceHolder = null;
        surfaceReady = false;
    }

    private void setWaveData(float[] data) { // Array of floats from 0-1 of undefined length
        waveData = data;
    }

    private void setRandomData() {
        waveData = new float[(int)(Math.random()*1000+1)];
        for(int i = 0; i < waveData.length; i++) {
            waveData[i] = (float) Math.random();
        }
    }

    @Override
    public void run() {
        while(threadRunning)
        {
            long startTime = System.currentTimeMillis();

            if (surfaceHolder == null)
            {
                return;
            }

            canvas = surfaceHolder.lockCanvas();

            if (canvas == null)
            {
                return;
            }

            // clear the screen using black
            canvas.drawARGB(255, 0, 0, 0);  //test with green

            // Your drawing here
            paint.setColor(Color.RED);

            setRandomData();

            float[] points = new float[waveData.length*4+4];

            int j = 0;
            float prevx = 0, prevy = height*(1.0f-waveData[0]);

            for(int i = 1; i <= waveData.length; i++) {
                j = i*4;
                points[j] = prevx;
                points[j+1] = prevy;
                points[j+2] = (float)i * width / waveData.length;
                points[j+3] = height*(1.0f-waveData[i-1]);
                prevx = points[j+2];
                prevy = points[j+3];
            }

            canvas.drawLines(points, paint);

            // Send message to main UI thread to update the drawing to the main view special area.
            surfaceHolder.unlockCanvasAndPost(canvas);

            long deltaTime = System.currentTimeMillis() - startTime;

            if(deltaTime < 16)
            {
                try {
                    Thread.sleep(16 - deltaTime);
                }catch (InterruptedException ex)
                {
                    Log.e(LOG_TAG, ex.getMessage());
                }

            }
        }
    }
}