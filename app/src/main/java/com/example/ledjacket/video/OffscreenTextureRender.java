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
    private String LOG_TAG;
    private static final boolean VERBOSE = true; // lots of logging
    private int inWidth, inHeight, outWidth, outHeight;
    private boolean USE_EXTERNAL_OES; // false for sampler2D, true for samplerexternalOES

    private int textureType;

    //private final int pixelBufSize = mWidth * mHeight * 4;
    //private ByteBuffer pixelBuf;

    private int mProgram;

    private float[] mVertex;

    private static final float[] mCoord = new float[] {
            0, 0, // texture coordinates as they correspond to each vertex
            1, 0,
            0, 1,
            1, 1
    };

    // SEE: https://www.mindcontrol.org/~hplus/graphics/opengl-pixel-perfect.html
    private String VERTEX_SHADER = // pixel perfect rendering
            "uniform vec2 uOffset;\n" + // (0.5 / width, 0.5 / height)"
            //"uniform mat4 uMVPMatrix;\n" +
            "attribute vec4 a_position;\n" +
            "attribute vec2 a_texcoord;\n" +
            "varying vec2 v_texcoord;\n" +
            "void main(void) {\n" +
            "   gl_Position = a_position;\n" + // uMVPMatrix *
            "   v_texcoord  = a_texcoord + uOffset;\n" +
            "}";

    private String FRAGMENT_SHADER =
            (USE_EXTERNAL_OES ? "#extension GL_OES_EGL_image_external : require\n" : "") +
            "precision mediump float;\n" +
            (USE_EXTERNAL_OES ? "uniform samplerExternalOES sTexture;\n" : "uniform sampler2D sTexture;\n") +
            "varying vec2 v_texcoord;\n" +
            "void main(void) {\n" +
            // "   gl_FragColor = texture2D(sTexture, v_texcoord);\n" +
            "   gl_FragColor = vec4(v_texcoord.x, v_texcoord.y, 1.0, 1.0);\n" +
            "}";

    private FloatBuffer mVertexBuffer;
    private FloatBuffer mCoordBuffer;
    private float[] mMVPMatrix = new float[16];

    public OffscreenTextureRender(int iw, int ih, int ow, int oh, boolean oes, String tag) {
        LOG_TAG = tag;
        USE_EXTERNAL_OES = oes;
        textureType = USE_EXTERNAL_OES ? GLES11Ext.GL_TEXTURE_EXTERNAL_OES : GLES31.GL_TEXTURE_2D;

        if(VERBOSE) Log.d(LOG_TAG, "Created, texture type: " + (USE_EXTERNAL_OES ? "GL_TEXTURE_EXTERNAL_OES" : "GLES31.GL_TEXTURE_2D"));

        setOutWidthHeight(ow, oh);
        inWidth = iw;
        inHeight = ih;

        if(VERBOSE) Log.d(LOG_TAG, "Compile shaders");
        mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER, LOG_TAG);
        checkGlError("createProgram", LOG_TAG);

        if(VERBOSE) Log.d(LOG_TAG, "Setup buffers");
        mCoordBuffer = createFloatBuffer(mCoord);

        //pixelBuf = ByteBuffer.allocateDirect(pixelBufSize);
        //pixelBuf.order(ByteOrder.LITTLE_ENDIAN);

        checkGlError("Finish setup", LOG_TAG);
    }

    public void setOutWidthHeight(int width, int height) {
        outWidth = width;
        outHeight = height;

        mVertex = new float[] {
                 -1, -1,
                  1, -1,
                  -1,  1,
                  1,  1  // use these coordinates when mMVPMatrix is identity


                /*
                0, 0, // use these coordinates when mMVPMatrix is an orthographic projection
                outWidth, 0,
                0, outHeight,
                outWidth, outHeight

                 */
        };

        mVertexBuffer = createFloatBuffer(mVertex);

        // generate orthographic projection matrix, (0,0) is bottom-left corner, (width, height) is top-right corner
        Matrix.orthoM(mMVPMatrix, 0, 0, outWidth, outHeight, 0, -1, 1);

        GLES31.glViewport(0, 0, outWidth, outHeight);

        //TODO: invert
    }

    public void draw(int texture) {
        //if(VERBOSE) Log.d(LOG_TAG, "Draw called");
        //if(VERBOSE) Log.d(LOG_TAG, "in size=" + inWidth + "x" + inHeight + " out size=" + outWidth + "x" + outHeight);
        // (optional) clear to green so we can see if we're failing to set pixels
        //GLES31.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
        //GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT);

        checkGlError("Before draw", LOG_TAG);

        //Log.i(LOG_TAG, "draw texture " + texture);
        GLES31.glUseProgram(mProgram);
        int vertexLocation = GLES31.glGetAttribLocation(mProgram, "a_position");
        GLES31.glEnableVertexAttribArray(vertexLocation);
        GLES31.glVertexAttribPointer(vertexLocation, 2, GLES31.GL_FLOAT, false, 0, mVertexBuffer);
        int coordLocation = GLES31.glGetAttribLocation(mProgram, "a_texcoord");
        GLES31.glEnableVertexAttribArray(coordLocation);
        GLES31.glVertexAttribPointer(coordLocation, 2, GLES31.GL_FLOAT, false, 0, mCoordBuffer);
        int textureLocation = GLES31.glGetUniformLocation(mProgram, "sTexture");

        checkGlError("set attributes", LOG_TAG);

        GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
        GLES31.glBindTexture(textureType, texture); // error here, 1282 invalid operation
        GLES31.glUniform1i(textureLocation, 0);

        checkGlError("bind texture", LOG_TAG);

        /*
        // Uniform variables must be set after glUseProgram
        int muMVPMatrixHandle = GLES31.glGetUniformLocation(mProgram, "uMVPMatrix");
        //checkLocation(muMVPMatrixHandle, "uMVPMatrix");
        GLES31.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);
        //checkGlError("glUniformMatrix4fv");

         */

        int mOffsetHandle = GLES31.glGetUniformLocation(mProgram, "uOffset");
        //checkLocation(mOffsetHandle, "uOffset");
        GLES31.glUniform2f(mOffsetHandle, 0.5f / inWidth, 0.5f / inHeight);
        //checkGlError("glUniform2fv");

        checkGlError("upload matrix and offset", LOG_TAG);

        GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4);

        checkGlError("After draw", LOG_TAG);
    }

    public int createTexture() {
        if(VERBOSE) Log.d(LOG_TAG, "creating texture");

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
