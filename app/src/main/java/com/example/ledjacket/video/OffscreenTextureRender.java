package com.example.ledjacket.video;

import static com.example.ledjacket.video.ShaderHelpers.*;

import android.opengl.GLES11Ext;
import android.opengl.GLES31;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

// RENDERS A TEXTURE ONTO A PBUFFER EGL SURFACE

public class OffscreenTextureRender {
    private static final String LOG_TAG = "OffscreenTextureRender";
    private static final boolean VERBOSE = true; // lots of logging
    private int mWidth;
    private int mHeight;
    public final boolean USE_EXTERNAL_OES = true; // false for sampler2D, true for samplerexternalOES

    private final int textureType = USE_EXTERNAL_OES ? GLES11Ext.GL_TEXTURE_EXTERNAL_OES : GLES31.GL_TEXTURE_2D; // don't change this

    //private final int pixelBufSize = mWidth * mHeight * 4;
    //private ByteBuffer pixelBuf;

    private int mProgram;

    private final float[] mVertex = new float[] {
         /* -1, -1,
            1, -1,
            -1,  1,
            1,  1 */ // use these coordinates when mMVPMatrix is identity

            0, 0, // use these coordinates when mMVPMatrix is an orthographic projection
            mWidth, 0,
            0, mHeight,
            mWidth, mHeight
    };

    private static final float[] mCoord = new float[] {
            0, 0,
            1, 0,
            0, 1,
            1, 1
    };

    // SEE: https://www.mindcontrol.org/~hplus/graphics/opengl-pixel-perfect.html
    private String VERTEX_SHADER = // pixel perfect rendering
            "uniform vec2 uOffset;\n" + // (0.5 / width, 0.5 / height)"
            "attribute vec3 a_position;\n" +
            "attribute vec2 a_texcoord;\n" +
            "varying vec2 v_texcoord;\n" +
            "void main(void) {\n" +
            "   gl_Position = vec4(a_position.xyz, 1.0);\n" +
            "   v_texcoord  = a_texcoord + uOffset;\n" +
            "}";

    private String FRAGMENT_SHADER =
            (USE_EXTERNAL_OES ? "#extension GL_OES_EGL_image_external : require\n" : "") +
            "precision mediump float;\n" +
            (USE_EXTERNAL_OES ? "uniform samplerExternalOES sTexture;\n" : "uniform sampler2D sTexture;\n") +
            "varying vec2 v_texcoord;\n" +
            "void main(void) {\n" +
            "   gl_FragColor = texture2D(sTexture, v_texcoord);\n" +
            "}";

    private FloatBuffer mVertexBuffer;
    private FloatBuffer mCoordBuffer;
    private float[] mMVPMatrix = new float[16];

    // TODO: invert vertically?
    // TODO: Should offscreentexturerender setup the egl surface or should codecoutputsurface (more likely)

    public OffscreenTextureRender(int width, int height) {
        mWidth = width;
        mHeight = height;

        if(VERBOSE) Log.d(LOG_TAG, "Created");
        //eglSetup();

        // generate orthographic projection matrix, (0,0) is bottom-left corner, (width, height) is top-right corner
        Matrix.orthoM(mMVPMatrix, 0, 0, mWidth, 0, mHeight, -1, 1);

        if(VERBOSE) Log.d(LOG_TAG, "Compile shaders");
        mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER, LOG_TAG);
        GLES31.glViewport(0, 0, mWidth, mHeight);

        if(VERBOSE) Log.d(LOG_TAG, "Setup buffers");
        mVertexBuffer = createFloatBuffer(mVertex);
        mCoordBuffer = createFloatBuffer(mCoord);

        //pixelBuf = ByteBuffer.allocateDirect(pixelBufSize);
        //pixelBuf.order(ByteOrder.LITTLE_ENDIAN);
    }

    /*
    private void eglSetup() {
        EGLDisplay mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
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
                EGL14.EGL_DEPTH_SIZE, 0,
                EGL14.EGL_STENCIL_SIZE, 0,
                EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
        };

        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.length, numConfigs, 0)) {
            throw new RuntimeException("unable to find RGB888+recordable ES2 EGL config");
        }

        // Configure context for OpenGL ES 3.0.
        int[] attrib_list = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL14.EGL_NONE
        };
        EGLContext mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], EGL14.EGL_NO_CONTEXT, attrib_list, 0);
        //checkEglError("eglCreateContext");
        if (mEGLContext == null) {
            throw new RuntimeException("null context");
        }

        // Create a pbuffer surface.
        int[] surfaceAttribs = {
                EGL14.EGL_WIDTH, mWidth,
                EGL14.EGL_HEIGHT, mHeight,
                EGL14.EGL_NONE
        };
        EGLSurface mEGLSurface = EGL14.eglCreatePbufferSurface(mEGLDisplay, configs[0], surfaceAttribs, 0);
        //checkEglError("eglCreatePbufferSurface");
        if (mEGLSurface == null) {
            throw new RuntimeException("surface was null");
        }

        if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }
    */

    public void draw(int texture) {
        // (optional) clear to green so we can see if we're failing to set pixels
        //GLES31.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);

        //Log.i(LOG_TAG, "draw texture " + texture);
        GLES31.glUseProgram(mProgram);
        int vertexLocation = GLES31.glGetAttribLocation(mProgram, "a_position");
        GLES31.glEnableVertexAttribArray(vertexLocation);
        GLES31.glVertexAttribPointer(vertexLocation, 2, GLES31.GL_FLOAT, false, 0, mVertexBuffer);
        int coordLocation = GLES31.glGetAttribLocation(mProgram, "a_texcoord");
        GLES31.glEnableVertexAttribArray(coordLocation);
        GLES31.glVertexAttribPointer(coordLocation, 2, GLES31.GL_FLOAT, false, 0, mCoordBuffer);
        int textureLocation = GLES31.glGetUniformLocation(mProgram, "sTexture");

        GLES31.glActiveTexture(GLES31.GL_TEXTURE0);

        GLES31.glBindTexture(textureType, texture);

        GLES31.glUniform1i(textureLocation, 0);

        // Uniform variables must be set after glUseProgram
        int muMVPMatrixHandle = GLES31.glGetUniformLocation(mProgram, "uMVPMatrix");
        //checkLocation(muMVPMatrixHandle, "uMVPMatrix");
        GLES31.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);
        //checkGlError("glUniformMatrix4fv");

        int mOffsetHandle = GLES31.glGetUniformLocation(mProgram, "uOffset");
        //checkLocation(mOffsetHandle, "uOffset");
        GLES31.glUniform2f(mOffsetHandle, 0.5f / mWidth, 0.5f / mHeight);
        //checkGlError("glUniform2fv");

        GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4);
    }

    public int createTexture() {
        Log.d(LOG_TAG, "creating texture");

        int[] textures = new int[1];
        GLES31.glGenTextures(1, textures, 0);

        int texID = textures[0];

        GLES31.glBindTexture(textureType, texID);
        checkGlError("glBindTexture texID", LOG_TAG);

        setTexParameters(textureType);
        checkGlError("glTexParameter", LOG_TAG);

        return texID;
    }

    public void destroy() {
        GLES31.glBindTexture(textureType, 0); // necessary?
        GLES31.glDeleteProgram(mProgram);
    }

    /*public void putFrameOnBitmap(Bitmap bmp) { // Bitmap must have Bitmap.Config.ARGB_8888, mWidth, mHeight
        pixelBuf.rewind();
        GLES31.glReadPixels(0, 0, mWidth, mHeight, GLES31.GL_RGBA, GLES31.GL_UNSIGNED_BYTE, pixelBuf);
        //Bitmap bmp = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
        bmp.copyPixelsFromBuffer(pixelBuf);
    }*/
}
