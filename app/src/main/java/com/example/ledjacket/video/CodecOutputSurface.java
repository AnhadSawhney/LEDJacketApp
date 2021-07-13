package com.example.ledjacket.video;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES31;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

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
public class CodecOutputSurface implements SurfaceTexture.OnFrameAvailableListener {
    private static final String LOG_TAG = "CodecOutputSurface";
    private static final boolean VERBOSE = false; // lots of logging
    private VideoTextureRender mTextureRender;
    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface; // decoder renders to this

    private static Bitmap map;

    private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;
    static int mWidth;
    static int mHeight;

    private Object mFrameSyncObject = new Object();     // guards mFrameAvailable
    private boolean mFrameAvailable;

    private static final int mainPixelBufSize = mWidth * mHeight * 4;
    private static ByteBuffer mainPixelBuf;
    private static final int dataPixelBufSize = 300 * 200 * 4;  //36864; // Take this number from the python script
    private static ByteBuffer dataPixelBuf;

    /**
     * Creates a CodecOutputSurface backed by a pbuffer with the specified dimensions.  The
     * new EGL context and surface will be made current.  Creates a Surface that can be passed
     * to MediaCodec.configure().
     */
    public CodecOutputSurface(int width, int height, Bitmap map) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException();
        }
        mWidth = width;
        mHeight = height;

        // Deep copy because original bitmap gets recycled
        this.map = Bitmap.createBitmap(map.getWidth(), map.getHeight(), map.getConfig());
        Canvas copiedCanvas = new Canvas(this.map);
        copiedCanvas.drawBitmap(map, 0f, 0f, null);

        eglSetup();
        makeCurrent();
        setup();
    }

    /**
     * Creates interconnected instances of TextureRender, SurfaceTexture, and Surface.
     */
    private void setup() {
        mTextureRender = new VideoTextureRender();
        mTextureRender.surfaceCreated();

        if (VERBOSE) Log.d(LOG_TAG, "textureID=" + mTextureRender.getTextureId());
        mSurfaceTexture = new SurfaceTexture(mTextureRender.getTextureId());

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

        mainPixelBuf = ByteBuffer.allocateDirect(mainPixelBufSize);
        mainPixelBuf.order(ByteOrder.LITTLE_ENDIAN);
        dataPixelBuf = ByteBuffer.allocateDirect(dataPixelBufSize);
        dataPixelBuf.order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Prepares EGL.  We want a GLES 2.0 context and a surface that supports pbuffer.
     */
    private void eglSetup() {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("unable to get EGL14 display");
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            mEGLDisplay = null;
            throw new RuntimeException("unable to initialize EGL14");
        }

        // Configure EGL for pbuffer and OpenGL ES 2.0, 24-bit RGB.
        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 0, // new
                EGL14.EGL_STENCIL_SIZE, 0, // new
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
        };

        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.length, numConfigs, 0)) {
            throw new RuntimeException("unable to find RGB888+recordable ES2 EGL config");
        }

        // Configure context for OpenGL ES 2.0.
        int[] attrib_list = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], EGL14.EGL_NO_CONTEXT, attrib_list, 0);
        checkEglError("eglCreateContext");
        if (mEGLContext == null) {
            throw new RuntimeException("null context");
        }

        // Create a pbuffer surface.
        int[] surfaceAttribs = {
                EGL14.EGL_WIDTH, mWidth,
                EGL14.EGL_HEIGHT, mHeight,
                EGL14.EGL_NONE
        };
        mEGLSurface = EGL14.eglCreatePbufferSurface(mEGLDisplay, configs[0], surfaceAttribs, 0);
        checkEglError("eglCreatePbufferSurface");
        if (mEGLSurface == null) {
            throw new RuntimeException("surface was null");
        }
    }

    /**
     * Makes our EGL context and surface current.
     */
    public void makeCurrent() {
        if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }

    /**
     * Discard all resources held by this class, notably the EGL context.
     */
    public void release() {
        if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
            EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(mEGLDisplay);
        }
        mEGLDisplay = EGL14.EGL_NO_DISPLAY;
        mEGLContext = EGL14.EGL_NO_CONTEXT;
        mEGLSurface = EGL14.EGL_NO_SURFACE;

        mSurface.release();

        map.recycle();

        // this causes a bunch of warnings that appear harmless but might confuse someone:
        //  W BufferQueue: [unnamed-3997-2] cancelBuffer: BufferQueue has been abandoned!
        //mSurfaceTexture.release();

        mTextureRender = null;
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
        if(VERBOSE) Log.d(LOG_TAG, "Waiting for new image");

        final int TIMEOUT_MS = 2500;

        synchronized (mFrameSyncObject) {
            while (!mFrameAvailable) {
                try {
                    // Wait for onFrameAvailable() to signal us.  Use a timeout to avoid
                    // stalling the test if it doesn't arrive.
                    mFrameSyncObject.wait(TIMEOUT_MS);
                    if (!mFrameAvailable) {
                        // TODO: if "spurious wakeup", continue while loop
                        throw new RuntimeException("frame wait timed out");
                    }
                } catch (InterruptedException ie) {
                    // shouldn't happen
                    throw new RuntimeException(ie);
                }
            }
            mFrameAvailable = false;
        }

        // Latch the data.
        mTextureRender.checkGlError("before updateTexImage");

        //Log.d(LOG_TAG, mEGLDisplay.toString());

        mSurfaceTexture.updateTexImage();
    }

    /**
     * Draws the data from SurfaceTexture onto the current EGL surface.
     *
     * @param invert if set, render the image with Y inverted (0,0 in top left)
     */
    public void drawImage(boolean invert) {
        mTextureRender.drawFrame(mSurfaceTexture, invert);
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

    // TODO: speedup? https://vec.io/posts/faster-alternatives-to-glreadpixels-and-glteximage2d-in-opengl-es

    public void putFrameOnBitmap(Bitmap bmp) { // Bitmap must have Bitmap.Config.ARGB_8888, mWidth, mHeight
        mainPixelBuf.rewind();
        GLES31.glReadPixels(0, 0, mWidth, mHeight, GLES31.GL_RGBA, GLES31.GL_UNSIGNED_BYTE, mainPixelBuf);
        //Bitmap bmp = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
        bmp.copyPixelsFromBuffer(mainPixelBuf);
    }

    public void putMainImageOnBitmap(Bitmap bmp) {
        // TODO: implement this
    }

    public void putDataImageOnBitmap(Bitmap bmp) {
        // TODO: implement this
    }

    /**
     * Checks for EGL errors.
     */
    private void checkEglError(String msg) {
        int error;
        if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
            throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
        }
    }

    /**
     * Code for rendering a texture onto a surface using OpenGL ES 2.0.
     */
    private static class VideoTextureRender {
        private static final int FLOAT_SIZE_BYTES = 4;
        private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
        private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
        private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;
        private final float[] mTriangleVerticesData = {
            // X, Y, Z, U, V
            /*-1.0f, -1.0f, 0, 0.f, 0.f, // use these coordinates when mMVPMatrix is identity
            1.0f, -1.0f, 0, 1.f, 0.f,
            -1.0f,  1.0f, 0, 0.f, 1.f,
            1.0f,  1.0f, 0, 1.f, 1.f,*/

            0f, 0f, 0, 0.f, 0.f, // use these coordinates when mMVPMatrix is an orthographic projection
            mWidth, 0f, 0, 1.f, 0.f,
            0f,  mHeight, 0, 0.f, 1.f,
            mWidth,  mHeight, 0, 1.f, 1.f,
        }; // aPosition   | aTextureCoord

        private FloatBuffer mTriangleVertices;

        private static final String VERTEX_SHADER = // Simple vertex shader, rectangle filling the screen
            "uniform mat4 uMVPMatrix;\n" +
            "uniform vec2 uOffset;\n" + // (0.5 / width, 0.5 / height)
            //"uniform mat4 uMVPMatrix;\n" +
            "uniform mat4 uSTMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aTextureCoord;\n" + // homogenous texture coordinates ([0-1],[0-1],0,1)
            "varying vec2 vTextureCoord;\n" + // texture coordinates to be sent to Fragment Shader
            "void main() {\n" +
            "   gl_Position = uMVPMatrix * aPosition;\n" +
            "   vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" + // coordinates to sample the surfaceTexture (usually same as aTextureCoord)
            "   vTextureCoord += uOffset;\n" + // offset to center of texel for pixel perfect
            "}\n"; // TODO: send untransformed aTextureCoord to fragment shader for map sampling to handle inversion

        // SEE: https://stackoverflow.com/questions/13376254/android-opengl-combination-of-surfacetexture-external-image-and-ordinary-textu

        private static final String FRAGMENT_SHADER =
            "#version 310 es\n" +
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +      // highp here doesn't seem to matter
            "layout(std430) buffer;\n" + // Sets the default layout for SSBOs.
            "layout(binding = 0) buffer SSBO {\n" +
            "   vec4 data[];\n" +
            "};\n" +
            "uniform int rows;\n" +
            "uniform int columns;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" + // This is the surfaceTexture with the video frame
            "uniform sampler2D MapTexture;\n" + // This texture is the map
            "void main() {\n" +
            //"   gl_FragColor = texture2D(sTexture, vTextureCoord);\n" + // get the pixel from the video frame in the position vTextureCoord
            "   vec4 videoColor = texture2D(sTexture, vTextureCoord);\n" +
            "   vec4 mapColor = texture2D(MapTexture, vTextureCoord);\n" +
            "   if (mapColor.r != 1.) {\n" +
            "       gl_FragColor = videoColor;\n" +
            "       int row = (int)floor(mapColor.r * 255.);\n" + // float to byte
            "       int column = (int)floor(mapColor.b * 65535. + mapColor.g * 255.);\n" + // float to 16 bit int
            "       if(column < columns && row < rows) {\n" + // safety
            "           atomicExchange(data[column*rows + row], videoColor);\n" + // write to location, blocking other threads
            "       }\n" +
            "   } else {\n" +
            "       gl_FragColor = vec4(0.063,0.063,0.063,1.);\n" + // clear to RGBA(16,16,16, 255) to avoid black smearing
            "   }\n" +
            "}\n";

        private float[] mMVPMatrix = new float[16]; // This is the matrix that transforms the points into the triangle into homogenous world coordinates ([0-1],[0-1],0,1)
        private float[] mSTMatrix = new float[16]; // This is the matrix that transforms homogenous texture coordinates to surfaceTexture coordinates

        private int mProgram;
        private int mainTextureID = -12345; // invalid value, gets overridden by glGenTextures
        private int mapTextureID = -12345;
        private int muMVPMatrixHandle;
        private int muSTMatrixHandle;
        private int maPositionHandle;
        private int maTextureCoordHandle;
        private int mOffsetHandle;
        private int mMapTextureHandle;
        private int SSBOHandle;

        // SEE: https://www.mindcontrol.org/~hplus/graphics/opengl-pixel-perfect.html

        public VideoTextureRender() {
            // Put triangle vertices data into bytebuffer to send to GLES
            mTriangleVertices = ByteBuffer.allocateDirect(mTriangleVerticesData.length * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
            mTriangleVertices.put(mTriangleVerticesData).position(0);

            // generate orthographic projection matrix, (0,0) is bottom-left corner, (width, height) is top-right corner
            Matrix.orthoM(mMVPMatrix, 0, 0, mWidth, 0, mHeight, -1, 1);
            //Matrix.setIdentityM(mMVPMatrix, 0);
            Matrix.setIdentityM(mSTMatrix, 0); // this gets overwritten by the matrix from the surfaceTexture
        }

        public int getTextureId() {
            return mainTextureID;
        }

        /**
         * Draws the external texture in SurfaceTexture onto the current EGL surface.
         */
        public void drawFrame(SurfaceTexture st, boolean invert) {
            checkGlError("onDrawFrame start");
            st.getTransformMatrix(mSTMatrix);
            // This returns a matrix that maps 2D homogeneous texture coordinates of the form (s, t, 0, 1) with
            // s and t in the inclusive range [0, 1] to the texture coordinate that should be used to sample
            // that location from the texture.

            //Log.d(LOG_TAG, Arrays.toString(mSTMatrix));

            if (invert) { // invert y axis
                mSTMatrix[5] = -mSTMatrix[5];
                mSTMatrix[13] = 1.0f - mSTMatrix[13];
            }

            // (optional) clear to green so we can see if we're failing to set pixels
            GLES31.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);

            // Clear to RGB(16,16,16) to avoid black smearing
            //GLES31.glClearColor(0.125f, 0.125f, 0.125f, 1.0f);
            GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT);

            GLES31.glUseProgram(mProgram);
            checkGlError("glUseProgram");

            GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0, SSBOHandle);

            // From: https://github.com/ibraimgm/opengles2-2d-demos/blob/master/src/ibraim/opengles2/TextureActivity.java
            GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
            GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mainTextureID);
            checkGlError("glBindTexture");

            // TODO: move stuff out of drawFrame?
            GLES31.glUniform1i(mMapTextureHandle, 1);
            GLES31.glActiveTexture(GLES31.GL_TEXTURE1);
            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, mapTextureID);
            checkGlError("glBindTexture");

            mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
            GLES31.glVertexAttribPointer(maPositionHandle, 3, GLES31.GL_FLOAT, false, TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
            checkGlError("glVertexAttribPointer maPosition");
            GLES31.glEnableVertexAttribArray(maPositionHandle);
            checkGlError("glEnableVertexAttribArray maPositionHandle");

            mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
            GLES31.glVertexAttribPointer(maTextureCoordHandle, 2, GLES31.GL_FLOAT, false, TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
            checkGlError("glVertexAttribPointer maTextureCoordHandle");
            GLES31.glEnableVertexAttribArray(maTextureCoordHandle);
            checkGlError("glEnableVertexAttribArray maTextureCoordHandle");

            // Uniform variables must be set after glUseProgram

            GLES31.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);
            GLES31.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);
            checkGlError("glUniformMatrix4fv");

            GLES31.glUniform2f(mOffsetHandle, 0.5f / mWidth, 0.5f / mHeight);
            checkGlError("glUniform2fv");

            GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4);
            checkGlError("glDrawArrays");

            GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        }

        /**
         * Initializes GL state.  Call this after the EGL surface has been created and made current.
         */
        public void surfaceCreated() {
            mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            if (mProgram == 0) {
                throw new RuntimeException("failed creating program");
            }

            final int[] ssbos = new int[1];
            GLES31.glGenBuffers(1, ssbos, 0);
            SSBOHandle = ssbos[0];

            GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, SSBOHandle);
            GLES31.glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, dataPixelBufSize, null, GLES31.GL_STATIC_DRAW);

            int bufMask = GLES31.GL_MAP_WRITE_BIT | GLES31.GL_MAP_INVALIDATE_BUFFER_BIT;
            dataPixelBuf = (ByteBuffer) GLES31.glMapBufferRange(GLES31.GL_SHADER_STORAGE_BUFFER, 0, dataPixelBufSize, bufMask);
            dataPixelBuf.rewind();
            while(dataPixelBuf.hasRemaining())
                dataPixelBuf.put((byte) 0);
            // Maybe set to zero
            GLES31.glUnmapBuffer(GLES31.GL_SHADER_STORAGE_BUFFER);

            maPositionHandle = GLES31.glGetAttribLocation(mProgram, "aPosition");
            checkLocation(maPositionHandle, "aPosition");
            maTextureCoordHandle = GLES31.glGetAttribLocation(mProgram, "aTextureCoord");
            checkLocation(maTextureCoordHandle, "aTextureCoord");

            muMVPMatrixHandle = GLES31.glGetUniformLocation(mProgram, "uMVPMatrix");
            checkLocation(muMVPMatrixHandle, "uMVPMatrix");
            muSTMatrixHandle = GLES31.glGetUniformLocation(mProgram, "uSTMatrix");
            checkLocation(muSTMatrixHandle, "uSTMatrix");

            mOffsetHandle = GLES31.glGetUniformLocation(mProgram, "uOffset");
            checkLocation(mOffsetHandle, "uOffset");

            mMapTextureHandle = GLES31.glGetUniformLocation(mProgram, "MapTexture");
            checkLocation(mMapTextureHandle, "MapTexture");

            int rowshandle = GLES31.glGetUniformLocation(mProgram, "rows");
            checkLocation(rowshandle, "rows");
            GLES31.glUniform1i(rowshandle, mWidth);

            int columnshandle = GLES31.glGetUniformLocation(mProgram, "columns");
            checkLocation(columnshandle, "columns");
            GLES31.glUniform1i(columnshandle, mHeight);

            int[] textures = new int[2];
            GLES31.glGenTextures(2, textures, 0);

            mainTextureID = textures[0];
            mapTextureID = textures[1];

            GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mainTextureID);
            checkGlError("glBindTexture mainTextureID");

            // MINIMIZATION FILTER IS TAKING EFFECT
            GLES31.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_NEAREST); // Nearest neighbor scaling
            GLES31.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_NEAREST); //GLES31.GL_NEAREST);
            GLES31.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE);
            GLES31.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE);
            checkGlError("glTexParameter");

            loadGLTextureFromBitmap(map);

            GLES31.glViewport(0, 0, mWidth, mHeight);
        }

        /**
         * Helper method to load a GL texture from a bitmap
         *
         * Note that the caller should "recycle" the bitmap
         */
        // COURTESY OF: https://gamedev.stackexchange.com/questions/10829/loading-png-textures-for-use-in-android-opengl-es1
        // TODO: recycle bitmap?
        public void loadGLTextureFromBitmap(Bitmap bitmap) {
            /*
            int originalWidth = bitmap.getWidth(); //(int)(image.getIntrinsicWidth() / density);
            int originalHeight = bitmap.getHeight(); //(int)(image.getIntrinsicHeight() / density);

            int powWidth = getNextHighestPO2(originalWidth);
            int powHeight = getNextHighestPO2(originalHeight);

            // Create an empty, mutable bitmap
            newbitmap = Bitmap.createBitmap(powWidth, powHeight, Bitmap.Config.ARGB_8888);
            // get a canvas to paint over the bitmap
            Canvas canvas = new Canvas(newbitmap);
            newbitmap.eraseColor(0);

            canvas.drawBitmap(bitmap, 0, 0, null);
            // then use newbitmap instead of bitmap
             */

            // Texture id is already obtained in surfaceCreated
            //int[] textureIds = new int[1];
            //GLES31.glGenTextures( 1, textureIds, 0 );
            //mapTextureID = textureIds[0];

            // bind this texture
            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, mapTextureID);
            checkGlError("glBindTexture mapTextureID");

            GLES31.glTexParameterf(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_NEAREST); // Nearest neighbor scaling
            GLES31.glTexParameterf(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_NEAREST); //GLES31.GL_NEAREST);
            GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE);
            GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE);
            checkGlError("glTexParameter");

            // Use the Android GLUtils to specify a two-dimensional texture image from our bitmap
            GLUtils.texImage2D(GLES31.GL_TEXTURE_2D, 0, bitmap, 0);
        }

        /**
         * Replaces the fragment shader.  Pass in null to reset to default.
         */
        public void changeFragmentShader(String fragmentShader) {
            if (fragmentShader == null) {
                fragmentShader = FRAGMENT_SHADER;
            }
            GLES31.glDeleteProgram(mProgram);
            mProgram = createProgram(VERTEX_SHADER, fragmentShader);
            if (mProgram == 0) {
                throw new RuntimeException("failed creating program");
            }
        }

        private int loadShader(int shaderType, String source) {
            int shader = GLES31.glCreateShader(shaderType);
            checkGlError("glCreateShader type=" + shaderType);
            GLES31.glShaderSource(shader, source);
            GLES31.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES31.glGetShaderiv(shader, GLES31.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                Log.e(LOG_TAG, "Could not compile shader " + shaderType + ":");
                Log.e(LOG_TAG, " " + GLES31.glGetShaderInfoLog(shader));
                GLES31.glDeleteShader(shader);
                shader = 0;
            }
            return shader;
        }

        private int createProgram(String vertexSource, String fragmentSource) {
            int vertexShader = loadShader(GLES31.GL_VERTEX_SHADER, vertexSource);
            if (vertexShader == 0) {
                return 0;
            }
            int pixelShader = loadShader(GLES31.GL_FRAGMENT_SHADER, fragmentSource);
            if (pixelShader == 0) {
                return 0;
            }

            int program = GLES31.glCreateProgram();
            if (program == 0) {
                Log.e(LOG_TAG, "Could not create program");
            }
            GLES31.glAttachShader(program, vertexShader);
            checkGlError("glAttachShader");
            GLES31.glAttachShader(program, pixelShader);
            checkGlError("glAttachShader");
            GLES31.glLinkProgram(program);
            int[] linkStatus = new int[1];
            GLES31.glGetProgramiv(program, GLES31.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES31.GL_TRUE) {
                Log.e(LOG_TAG, "Could not link program: ");
                Log.e(LOG_TAG, GLES31.glGetProgramInfoLog(program));
                GLES31.glDeleteProgram(program);
                program = 0;
            }
            return program;
        }

        public void checkGlError(String op) {
            int error;
            while ((error = GLES31.glGetError()) != GLES31.GL_NO_ERROR) {
                Log.e(LOG_TAG, op + ": glError " + error);
                throw new RuntimeException(op + ": glError " + error);
            }
        }

        public static void checkLocation(int location, String label) {
            if (location < 0) {
                throw new RuntimeException("Unable to locate '" + label + "' in program");
            }
        }

        /**
         * Calculates the next highest power of two for a given integer.
         *
         * @param n the number
         * @return a power of two equal to or higher than n
         */
        public static int getNextHighestPO2( int n ) {
            n -= 1;
            n = n | (n >> 1);
            n = n | (n >> 2);
            n = n | (n >> 4);
            n = n | (n >> 8);
            n = n | (n >> 16);
            n = n | (n >> 32);
            return n + 1;
        }
    }
}