package com.example.ledjacket.video;

import static com.example.ledjacket.video.ShaderHelpers.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES31;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

// From ExtractMpegFramesTest, copied to a separate file

/**
 * Holds state associated with a Surface used for MediaCodec decoder output.
 * <p>
 * The constructor for this class will prepare GL, create a SurfaceTexture,
 * and then create a Surface for that SurfaceTexture.  The Surface can be passed to
 * MediaCodec.configure() to receive decoder output.  When a frame arrives, we latch the
 * texture with updateTexImage(), then render the texture with GL to a pbuffer.
 *
 * Creates a Surface that can be passed to MediaCodec.configure().
 * <p>
 * By default, the Surface will be using a BufferQueue in asynchronous mode, so we
 * can potentially drop frames.
 */
public class CodecOutputSurface implements SurfaceTexture.OnFrameAvailableListener, SurfaceHolder.Callback {
    private static final String LOG_TAG = "CodecOutputSurface";
    private static final boolean VERBOSE = false; // lots of logging
    private EGLCore mEGLCore;
    private OffscreenTextureRender videoTextureRender;
    private OffscreenTextureRender transferTextureRender; // renders the offscreen texture onto the onscreen surface
    private int mVideoTextureID = -1, mFramebuffer = -1, mOffscreenTextureID = -1;
    private PixelOrderProcessor mPixelProcessor;
    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface; // decoder renders to this
    private Surface viewSurface = null; // things drawn to this are sent to the screen

    private boolean newSurfaceFlag = false;

    private SurfaceView mainView = null;

    private EGLSurface mOffscreenSurface;

    private EGLSurface mWindowSurface = null;
    private FullFrameRect mFullScreen;

    //private static Bitmap mapBitmap;
    //private Bitmap mainBitmap;
    //private Bitmap dataBitmap;

    //private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    //private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
    //private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;
    static int mWidth;
    static int mHeight;

    private int SurfaceWidth, SurfaceHeight;

    private Object mFrameSyncObject = new Object();     // guards mFrameAvailable
    private boolean mFrameAvailable;

    //private static final int mainPixelBufSize = mWidth * mHeight * 4;
    //private static ByteBuffer mainPixelBuf;
    //private static final int dataPixelBufSize = 300 * 200 * 4;  //36864; // Take this number from the python script
    //private static ByteBuffer dataPixelBuf;

    //private static int mainPBO;
    //private static int dataPBO;
    //private static int mainFBO;
    //private static int dataFBO;

    /**
     * Creates a CodecOutputSurface backed by a pbuffer with the specified dimensions.  The
     * new EGL context and surface will be made current.  Creates a Surface that can be passed
     * to MediaCodec.configure().
     */
    public CodecOutputSurface(int width, int height) { //, Bitmap mapBitmap, Bitmap mainBitmap, Bitmap dataBitmap
        if(VERBOSE) Log.d(LOG_TAG, "Created");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException();
        }
        mWidth = width;
        mHeight = height;

        // shallow copy so we can write to videothread's bitmaps
        // this.mainBitmap = mainBitmap;
        // this.dataBitmap = dataBitmap;

        // Deep copy because original mapbitmap gets recycled
        //this.mapBitmap = Bitmap.createBitmap(CodecOutputSurface.mapBitmap.getWidth(), CodecOutputSurface.mapBitmap.getHeight(), CodecOutputSurface.mapBitmap.getConfig());
        //Canvas copiedCanvas = new Canvas(this.mapBitmap);
        //copiedCanvas.drawBitmap(CodecOutputSurface.mapBitmap, 0f, 0f, null);

        //eglSetup();
        //makeCurrent();
        // render to framebuffer not a pbuffer. Pbuffers are depreciated.

        if(VERBOSE) Log.d(LOG_TAG, "EGL Setup");
        mEGLCore = new EGLCore(null, EGLCore.FLAG_TRY_GLES3);
        mOffscreenSurface = mEGLCore.createOffscreenSurface(mWidth, mHeight);
        mEGLCore.makeCurrent(mOffscreenSurface);

        videoTextureRender = new OffscreenTextureRender(mWidth, mHeight, mWidth, mHeight, true, "videoTextureRender"); // This is just for rendering the texture onto the surface
        transferTextureRender = new OffscreenTextureRender(mWidth, mHeight, mWidth, mHeight, false, "transferTextureRender"); // output width and height are set by the surface when it is created
        //mPixelProcessor = new PixelOrderProcessor();

        mVideoTextureID = videoTextureRender.createTexture();

        // videoTextureRender.getTextureId() is videoTextureID
        if (VERBOSE) Log.d(LOG_TAG, "textureID=" + mVideoTextureID);
        mSurfaceTexture = new SurfaceTexture(mVideoTextureID);

        // This doesn't work if this object is created on the thread that CTS started for
        // these test cases.
        //
        // The CTS-created thread has a Looper, and the SurfaceTexture constructor will
        // create a Handler that uses it.  The "frame available" message is delivered
        // there, but since we're not a Looper-based thread we'll never see it.  For
        // this to do anything useful, CodecOutputSurface must be created on a thread without
        // a Looper, so that SurfaceTexture uses the main application Looper instead.
        //
        // Java language note: passing "this" out of a constructor is generally unwise,
        // but we should be able to get away with it here.
        mSurfaceTexture.setOnFrameAvailableListener(this);

        mSurface = new Surface(mSurfaceTexture);

        if(VERBOSE) Log.d(LOG_TAG, "Framebuffer Setup");
        mOffscreenTextureID = transferTextureRender.createTexture();
        prepareFramebuffer(); // this will create a texture attatched to a framebuffer for render to texture.
        // this is necessary so that the video frame is available as a texture for the compute shader
        // this way other things can also render to this texture and then the whole thing can be processed in the compute shader


        //mainPixelBuf = ByteBuffer.allocateDirect(mainPixelBufSize);
        //mainPixelBuf.order(ByteOrder.LITTLE_ENDIAN);
        //dataPixelBuf = ByteBuffer.allocateDirect(dataPixelBufSize);
        //dataPixelBuf.order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Prepares the off-screen framebuffer.
     */
    private void prepareFramebuffer() {
        checkGlError("prepareFramebuffer start", LOG_TAG);

        // used to generate texture given by GL_TEXTURE_2D, now gets it from transferTextureRender

        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, mOffscreenTextureID);
        checkGlError("glBindTexture " + mOffscreenTextureID, LOG_TAG);

        // Create texture storage.
        GLES31.glTexImage2D(GLES31.GL_TEXTURE_2D, 0, GLES31.GL_RGBA, mWidth, mHeight, 0, GLES31.GL_RGBA, GLES31.GL_UNSIGNED_BYTE, null);

        // Set parameters.  We're probably using non-power-of-two dimensions, so
        // some values may not be available for use.
        //setTexParameters(GLES31.GL_TEXTURE_2D);

        int[] values = new int[1];

        // Create framebuffer object and bind it.
        GLES31.glGenFramebuffers(1, values, 0);
        checkGlError("glGenFramebuffers", LOG_TAG);
        mFramebuffer = values[0];    // expected > 0
        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, mFramebuffer);
        checkGlError("glBindFramebuffer " + mFramebuffer, LOG_TAG);

        // Create a depth buffer and bind it.
        /*GLES31.glGenRenderbuffers(1, values, 0);
        checkGlError("glGenRenderbuffers", LOG_TAG);
        mDepthBuffer = values[0];    // expected > 0
        GLES31.glBindRenderbuffer(GLES31.GL_RENDERBUFFER, mDepthBuffer);
        checkGlError("glBindRenderbuffer " + mDepthBuffer, LOG_TAG);

        // Allocate storage for the depth buffer.
        GLES31.glRenderbufferStorage(GLES31.GL_RENDERBUFFER, GLES31.GL_DEPTH_COMPONENT16,
                width, height);
        checkGlError("glRenderbufferStorage", LOG_TAG);*/

        // Attach the depth buffer and the texture (color buffer) to the framebuffer object.
        //GLES31.glFramebufferRenderbuffer(GLES31.GL_FRAMEBUFFER, GLES31.GL_DEPTH_ATTACHMENT, GLES31.GL_RENDERBUFFER, mDepthBuffer);
        checkGlError("glFramebufferRenderbuffer", LOG_TAG);
        GLES31.glFramebufferTexture2D(GLES31.GL_FRAMEBUFFER, GLES31.GL_COLOR_ATTACHMENT0,
                GLES31.GL_TEXTURE_2D, mOffscreenTextureID, 0);
        checkGlError("glFramebufferTexture2D", LOG_TAG);

        // See if GLES is happy with all this.
        checkFramebufferStatus(LOG_TAG);

        // Switch back to the default framebuffer.
        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, 0);

        checkGlError("prepareFramebuffer done", LOG_TAG);
    }

    /**
     * Discard all resources held by this class, notably the EGL context.
     */

    public void release() {
        /*if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
            EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(mEGLDisplay);
        }
        mEGLDisplay = EGL14.EGL_NO_DISPLAY;
        mEGLContext = EGL14.EGL_NO_CONTEXT;
        mEGLSurface = EGL14.EGL_NO_SURFACE;*/

        videoTextureRender.destroy();
        transferTextureRender.destroy();

        if (mFramebuffer > 0) {
            int[] values = new int[1];
            values[0] = mFramebuffer;
            GLES31.glDeleteFramebuffers(1, values, 0);
            mFramebuffer = -1;
        }

        mEGLCore.release();

        mSurface.release();

        //mapBitmap.recycle();

        // this causes a bunch of warnings that appear harmless but might confuse someone:
        //  W BufferQueue: [unnamed-3997-2] cancelBuffer: BufferQueue has been abandoned!
        //mSurfaceTexture.release();

        videoTextureRender = null;
        mSurface = null;
        mSurfaceTexture = null;
    }

    /**
     * Returns the Surface.
     */
    public Surface getSurface() {
        return mSurface;
    }

    /**
     * Latches the next buffer into the texture.  Must be called from the thread that created
     * the CodecOutputSurface object.  (More specifically, it must be called on the thread
     * with the EGLContext that contains the GL texture object used by SurfaceTexture.)
     */
    public void awaitNewImage() {
        if(VERBOSE) Log.d(LOG_TAG, "Waiting for new frame");

        final int TIMEOUT_MS = 2500;

        synchronized (mFrameSyncObject) {
            while (!mFrameAvailable) {
                try {
                    // Wait for onFrameAvailable() to signal us.  Use a timeout to avoid
                    // stalling the test if it doesn't arrive.
                    mFrameSyncObject.wait(TIMEOUT_MS);
                    if (!mFrameAvailable) {
                        // TODO: if "spurious wakeup", continue while loop
                        //throw new RuntimeException("frame wait timed out");
                        Log.e(LOG_TAG, "frame wait timed out");
                        break;
                    }
                } catch (InterruptedException ie) {
                    // shouldn't happen
                    throw new RuntimeException(ie);
                }
            }
            mFrameAvailable = false;
        }

        // Latch the data.
        checkGlError("before updateTexImage", LOG_TAG);

        //Log.d(LOG_TAG, mEGLDisplay.toString());

        mSurfaceTexture.updateTexImage();
    }

    /**
     * Draws the data from SurfaceTexture onto the current EGL surface.
     */
    public void drawImage() { // This is where stuff happens
        if(newSurfaceFlag) {
            prepareGl(viewSurface);
            newSurfaceFlag = false;
        }

        if(VERBOSE) Log.d(LOG_TAG, "render offscreen");
        // https://stackoverflow.com/questions/30061753/drawing-on-multiple-surfaces-by-using-only-eglsurface
        mEGLCore.makeCurrent(mOffscreenSurface);
        // steps from grafika recordFBOactivity line 955
        // draw the video texture to the framebuffer (render offscreen), which means its not on mOffscreenTexture
        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, mFramebuffer); // select the offscreen framebuffer instead of the default one
        checkGlError("glBindFramebuffer", LOG_TAG);
        videoTextureRender.draw(mVideoTextureID);

        if(mWindowSurface != null) {
            if(VERBOSE) Log.d(LOG_TAG, "render onscreen");
            mEGLCore.makeCurrent(mWindowSurface);

            // blit the offscreen framebuffer to the one shown to the user
            GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, 0);
            checkGlError("glBindFramebuffer", LOG_TAG);
            // TODO: currently drawing the video frame twice. Instead, draw the offscreen texture
            //GLES31.glClearColor(1.0f, 1.0f, 0.0f, 1.0f);
            //GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT);

            // blit the offscreen stuff onto the visible surfaces
            transferTextureRender.draw(mVideoTextureID);

            boolean swapResult = mEGLCore.swapBuffers(mWindowSurface);

            if (!swapResult) {
                // This can happen if the Activity stops without waiting for us to halt.
                Log.w(LOG_TAG, "swapBuffers failed, killing renderer thread");
            }
        }


        // blit again to rearrange the pixels

        // rearrange the pixels

        //putMainImageOnBitmap();
        //putDataImageOnBitmap();
    }

    // SurfaceTexture callback
    @Override
    public void onFrameAvailable(SurfaceTexture st) {
        if (VERBOSE) Log.d(LOG_TAG, "new frame available");
        synchronized (mFrameSyncObject) {
            if (mFrameAvailable) {
                throw new RuntimeException("mFrameAvailable already set, frame could be dropped");
            }
            mFrameAvailable = true;
            mFrameSyncObject.notifyAll();
        }
    }

    /**
     * Prepares window surface and GL state.
     */
    private void prepareGl(Surface surface) {
        Log.d(LOG_TAG, "prepareGl");

        mWindowSurface = mEGLCore.createWindowSurface(surface);
        mEGLCore.makeCurrent(mWindowSurface);

        //mWindowSurface = new WindowSurface(mEGLCore, surface, false);

        //EGLContext sharedContext = EGL14.getCurrentContext();
        //EGLDisplay display = EGL14.eglGetCurrentDisplay();
        //mWindowSurface.makeCurrent();

        // Used for blitting texture to FBO.
        //mFullScreen = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_2D));

        // Program used for drawing onto the screen.
        //FlatShadedProgram mProgram = new FlatShadedProgram();

        // Set the background color.
        GLES31.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        // Disable depth testing -- we're 2D only.
        GLES31.glDisable(GLES31.GL_DEPTH_TEST);

        // Don't need backface culling.  (If you're feeling pedantic, you can turn it on to
        // make sure we're defining our shapes correctly.)
        GLES31.glDisable(GLES31.GL_CULL_FACE);

        //mActivityHandler.sendGlesVersion(mEglCore.getGlVersion());
    }

    // SurfaceView Methods, from grafika RecordFBOactivity line 155

    public SurfaceView getMainView() {
        return mainView;
    }

    public void setMainView(SurfaceView mainView) {
        this.mainView = mainView;
        mainView.getHolder().addCallback(this);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        Log.d(LOG_TAG, "surfaceCreated holder=" + holder);

        viewSurface = holder.getSurface();
        newSurfaceFlag = true;

        /*
        Rect r = holder.getSurfaceFrame();
        SurfaceWidth = r.width();
        SurfaceHeight = r.height();
        transferTextureRender.setOutWidthHeight(SurfaceWidth, SurfaceHeight);

         */

        /*SurfaceView sv = (SurfaceView) findViewById(R.id.fboActivity_surfaceView);
        //mRenderThread = new RenderThread(sv.getHolder(), new ActivityHandler(this), outputFile,
                MiscUtils.getDisplayRefreshNsec(this));
        mRenderThread.setName("RecordFBO GL render");
        mRenderThread.start();
        mRenderThread.waitUntilReady();
        mRenderThread.setRecordMethod(mSelectedRecordMethod);

        RenderHandler rh = mRenderThread.getHandler();
        if (rh != null) {
            rh.sendSurfaceCreated();
        }*/

        // start the draw events
        //Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        Log.d(LOG_TAG, "surfaceChanged fmt=" + format + " size=" + width + "x" + height +
                " holder=" + holder);

        viewSurface = holder.getSurface();
        newSurfaceFlag = true;

        SurfaceWidth = width;
        SurfaceHeight = height;
        transferTextureRender.setOutWidthHeight(SurfaceWidth, SurfaceHeight);

        //RenderHandler rh = mRenderThread.getHandler();
        /*if (rh != null) {
            rh.sendSurfaceChanged(format, width, height);
        }*/
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        Log.d(LOG_TAG, "surfaceDestroyed holder=" + holder);

        // TODO: We need to wait for the render thread to shut down before continuing because we
        // don't want the Surface to disappear out from under it mid-render.  The frame
        // notifications will have been stopped back in onPause(), but there might have
        // been one in progress.

        /*RenderHandler rh = mRenderThread.getHandler();
        if (rh != null) {
            rh.sendShutdown();
            try {
                mRenderThread.join();
            } catch (InterruptedException ie) {
                // not expected
                throw new RuntimeException("join was interrupted", ie);
            }
        }
        mRenderThread = null;
        mRecordingEnabled = false;*/

        // If the callback was posted, remove it.  Without this, we could get one more
        // call on doFrame().
        //Choreographer.getInstance().removeFrameCallback(this);
        viewSurface = null;
        mWindowSurface = null;

        Log.d(LOG_TAG, "surfaceDestroyed complete");
    }
}