package com.example.ledjacket.video;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import com.example.ledjacket.Middleman;

// TODO: Sync with choreographer

public class BitmapView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private static final int refreshDelay = 32; // 30 FPS
    private static final String LOG_TAG = "BitmapView";

    private static final boolean scale = false;

    private int width = 0;
    private int height = 0;
    private float aspectRatio;

    private SurfaceHolder surfaceHolder = null;

    private Thread thread = null;

    // Record whether the child thread is running or not.
    private boolean threadRunning = false;

    private Bitmap bmp = null;

    private Bitmap scaledbmp = null;

    private Canvas canvas = null;

    private void init() {
        //setFocusable(true);

        // Get SurfaceHolder object.
        surfaceHolder = this.getHolder();
        // Add current object as the callback listener.
        surfaceHolder.addCallback(this);

        // Create the paint object which will draw
        //paint = new Paint();
        //paint.setAntiAlias(true);

        // Set the SurfaceView object at the top of View object.
        setZOrderOnTop(true);
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
        this.aspectRatio = bmp.getWidth() / (float) bmp.getHeight();
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

        // release the surface
        surfaceHolder.getSurface().release();

        this.surfaceHolder = null;

        // destroy bitmaps
        if(scaledbmp != null) {
            scaledbmp.recycle();
        }
        // don't recycle bmp because it is a reference to the bitmap in videothread, which needs to still be there
    }

    @Override
    public void run() {
        while(threadRunning) {
            long startTime = System.currentTimeMillis();

            if (surfaceHolder == null) {
                return;
            }

            canvas = surfaceHolder.lockCanvas();

            if (canvas == null || bmp == null) {
                return;
            }


            // Filter: false - nearest neighbor, true - bilinear

            if(scale) {
                scaledbmp = Bitmap.createScaledBitmap(bmp, width, Math.round(width / aspectRatio), false);
                canvas.drawBitmap(scaledbmp, 0, 0, null);
            } else {
                canvas.drawBitmap(bmp, 0, 0, null);
            }

            // Send message to main UI thread to update the drawing to the main view special area.
            surfaceHolder.unlockCanvasAndPost(canvas);

            long deltaTime = System.currentTimeMillis() - startTime;
            //Log.d(LOG_TAG, "Loop took: (ms) " + deltaTime);

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
