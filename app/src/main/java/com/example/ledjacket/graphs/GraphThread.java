package com.example.ledjacket.graphs;

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

import com.example.ledjacket.Middleman;

import java.util.logging.Logger;

// Courtesy of https://www.dev2qa.com/android-draw-surfaceview-in-thread-example/

// Must use surfaceview instead of view, so it can be continuously updated in the background

public abstract class GraphThread extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private static final int refreshDelay = 16; // 60 FPS

    private SurfaceHolder surfaceHolder = null;

    protected Middleman middleman;

    protected Paint paint = null;

    private Thread thread = null;

    // Record whether the child thread is running or not.
    private boolean threadRunning = false;

    protected Canvas canvas = null;

    protected int width = 0;

    protected int height = 0;

    protected String LOG_TAG;

    protected volatile float[] waveData;

    private void init() {
        //setFocusable(true);

        // Get SurfaceHolder object.
        surfaceHolder = this.getHolder();
        // Add current object as the callback listener.
        surfaceHolder.addCallback(this);

        // Create the paint object which will draw
        paint = new Paint();
        paint.setAntiAlias(true);

        // Set the SurfaceView object at the top of View object.
        setZOrderOnTop(true);
    }

    public void setMiddleman(Middleman middleman) {
        this.middleman = middleman;
    }

    public GraphThread(Context context) {
        super(context);
        init();
    }

    public GraphThread(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GraphThread(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        this.surfaceHolder = surfaceHolder;

        if (thread != null) {
            Log.d(LOG_TAG, "draw thread still active");
            // Set thread running flag to true.
            try {
                thread.join();
            } catch (InterruptedException e) { // do nothing
            }
        } else {
            // Create the child thread when SurfaceView is created.
            thread = new Thread(this);
            // Start to run the child thread.
            thread.start();
        }

        // Set flags to true.
        threadRunning = true;

        // Get screen width and height.
        height = getHeight();
        width = getWidth();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        if (width == 0 || height == 0) {
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

        while (true) {
            try {
                Log.d(LOG_TAG, "Request last frame");
                thread.join(5000);
                break;
            } catch (Exception e) {
                Log.e(LOG_TAG, "Could not join with draw thread");
            }
        }

        thread = null;

        // and release the surface
        surfaceHolder.getSurface().release();

        this.surfaceHolder = null;
    }

    protected void setWaveData(float[] data) { // Array of floats from 0-1 of undefined length
        if(data.length > width) {
            waveData = new float[width];
            System.arraycopy(data, 0, waveData, 0, width); // deep copy
        } else {
            waveData = data;
            //waveData = new float[data.length];
            //.arraycopy(data, 0, waveData, 0, data.length);
        }
    }

    private void setRandomData() {
        waveData = new float[(int)(Math.random()*1000+1)];
        for(int i = 0; i < waveData.length; i++) {
            waveData[i] = (float) Math.random();
        }
    }

    protected abstract void getDataFromMiddleman();

    protected abstract void draw();

    //TODO: https://developer.android.com/reference/android/view/View#postOnAnimation(java.lang.Runnable)

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
                draw();

                // Send message to main UI thread to update the drawing to the main view special area.
                surfaceHolder.unlockCanvasAndPost(canvas);

                long deltaTime = System.currentTimeMillis() - startTime;

                if (deltaTime < refreshDelay) {
                    try {
                        Thread.sleep(refreshDelay - deltaTime);
                    } catch (InterruptedException ex) {
                        Log.e(LOG_TAG, ex.getMessage());
                    }
                }
            }
        }
    }
}