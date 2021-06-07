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

// Courtesy of https://www.dev2qa.com/android-draw-surfaceview-in-thread-example/

public class SurfaceViewThread extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private SurfaceHolder surfaceHolder = null;

    private Middleman middleman;

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

    public void setMiddleman(Middleman middleman) {
        this.middleman = middleman;
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

        this.height = height;
        this.width = width;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        // Set thread running flag to false when Surface is destroyed.
        // Then the thread will jump out the while loop and complete.
        threadRunning = false;

        if (thread == null) {
            Log.d(LOG_TAG, "DrawThread is null");
        }

        while (true)
        {
            try
            {
                Log.d(LOG_TAG, "Request last frame");
                thread.join(5000);
                break;
            } catch (Exception e)
            {
                Log.e(LOG_TAG, "Could not join with draw thread");
            }
        }

        thread = null;

        // and release the surface
        surfaceHolder.getSurface().release();

        this.surfaceHolder = null;
        surfaceReady = false;
    }

    private void setWaveData(float[] data) { // Array of floats from 0-1 of undefined length
        if(data.length > width) {
            System.arraycopy(data, 0, waveData, 0, width);
        } else {
            waveData = data;
        }
    }

    private void getDataFromMiddleman() {
        if(middleman != null && !middleman.isOscEmpty()) {
            try {
                float[] data = middleman.getOscData();
                if(data.length > width) {
                    waveData = new float[width];
                    System.arraycopy(data, 0, waveData, 0, width);
                } else {
                    waveData = data;
                }
            }catch (InterruptedException ex) {
                Log.e(LOG_TAG, ex.getMessage());
            }
        }
    }

    private void setRandomData() {
        waveData = new float[(int)(Math.random()*1000+1)];
        for(int i = 0; i < waveData.length; i++) {
            waveData[i] = (float) Math.random();
        }
    }

    @Override
    public void run() {
        while(threadRunning) {
            long startTime = System.currentTimeMillis();

            if (surfaceHolder == null) {
                return;
            }

            canvas = surfaceHolder.lockCanvas();

            if (canvas == null) {
                return;
            }

            getDataFromMiddleman();

            if(waveData != null) {

                // clear the screen using black
                canvas.drawARGB(255, 16, 16, 16);

                // Your drawing here
                paint.setColor(Color.RED);

                //setRandomData();

                float[] points = new float[waveData.length * 4];

                int j = 0;
                float prevx = 0, prevy = height * (1.0f - waveData[0]);

                for (int i = 1; i < waveData.length; i++) {
                    j = (i - 1) * 4;
                    points[j] = prevx;
                    points[j + 1] = prevy;
                    points[j + 2] = (float) i * width / (waveData.length - 1);
                    points[j + 3] = height * (1.0f - waveData[i]);
                    prevx = points[j + 2];
                    prevy = points[j + 3];
                }

                canvas.drawLines(points, paint);

                // Send message to main UI thread to update the drawing to the main view special area.
                surfaceHolder.unlockCanvasAndPost(canvas);

                long deltaTime = System.currentTimeMillis() - startTime;

                if (deltaTime < 33) { // 30 FPS
                    try {
                        Thread.sleep(33 - deltaTime);
                    } catch (InterruptedException ex) {
                        Log.e(LOG_TAG, ex.getMessage());
                    }
                }
            }
        }
    }
}