package com.example.ledjacket.video;

import android.graphics.Bitmap;
import android.opengl.GLES31;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class ShaderHelpers {
    /** Identity matrix for general use.  Don't modify or life will get weird. */
    public static final float[] IDENTITY_MATRIX;
    static {
        IDENTITY_MATRIX = new float[16];
        Matrix.setIdentityM(IDENTITY_MATRIX, 0);
    }

    private static final int SIZEOF_FLOAT = 4;

    /**
     * Helper method to load a GL texture from a bitmap
     *
     * Note that the caller should "recycle" the bitmap
     */
    // COURTESY OF: https://gamedev.stackexchange.com/questions/10829/loading-png-textures-for-use-in-android-opengl-es1
    // TODO: recycle bitmap?
    public static void loadGLTextureFromBitmap(Bitmap bitmap, int textureID) {
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

        // bind this texture
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, textureID);
        //checkGlError("glBindTexture mapTextureID");

        setTexParameters(GLES31.GL_TEXTURE_2D);

        // Use the Android GLUtils to specify a two-dimensional texture image from our bitmap
        GLUtils.texImage2D(GLES31.GL_TEXTURE_2D, 0, bitmap, 0);
    }

    public static void setTexParameters(int target) {
        GLES31.glTexParameterf(target, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_NEAREST); // Nearest neighbor scaling
        GLES31.glTexParameterf(target, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_NEAREST);
        GLES31.glTexParameteri(target, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE);
        GLES31.glTexParameteri(target, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE);
        //checkGlError("glTexParameter " + target);
    }

    public static int loadShader(int shaderType, String source, String LOG_TAG) {
        int shader = GLES31.glCreateShader(shaderType);
        checkGlError("glCreateShader type=" + shaderType, LOG_TAG);
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

    public static void checkGlError(String op, String LOG_TAG) {
        int error;
        while ((error = GLES31.glGetError()) != GLES31.GL_NO_ERROR) {
            String estring = "";
            switch(error) {
                case GLES31.GL_INVALID_ENUM:
                    estring = "Invalid Enum";
                    break;
                case GLES31.GL_INVALID_VALUE:
                    estring = "Invalid Value";
                    break;
                case GLES31.GL_INVALID_OPERATION:
                    estring = "Invalid Operation";
                    break;
                case GLES31.GL_INVALID_FRAMEBUFFER_OPERATION:
                    estring = "Invalid Framebuffer Operation";
                    break;
                case GLES31.GL_OUT_OF_MEMORY:
                    estring = "Out of Memory";
                    break;
            }

            Log.e(LOG_TAG, op + ": glError " + error + " (" + estring + ")");
            throw new RuntimeException(op + ": glError " + error + " (" + estring + ")");
        }
    }

    public static void checkLocation(int location, String label) {
        if (location < 0) {
            throw new RuntimeException("Unable to locate '" + label + "' in program");
        }
    }

    public static int createProgram(String computeSource, String LOG_TAG) {
        int computeShader = loadShader(GLES31.GL_COMPUTE_SHADER, computeSource, LOG_TAG);
        if (computeShader == 0) {
            Log.e(LOG_TAG, "Could not create compute shader");
            return 0;
        }

        int program = GLES31.glCreateProgram();
        if (program == 0) {
            Log.e(LOG_TAG, "Could not create program");
        }
        GLES31.glAttachShader(program, computeShader);

        checkGlError("glAttachShader", LOG_TAG);
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

    private static int compileShader(int type, String source, String LOG_TAG) {
        int shader = GLES31.glCreateShader(type);
        GLES31.glShaderSource(shader, source);
        GLES31.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES31.glGetShaderiv(shader, GLES31.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(LOG_TAG, "Could not compile shader " + type + ":");
            Log.e(LOG_TAG, " " + GLES31.glGetShaderInfoLog(shader));
            GLES31.glDeleteShader(shader);
            shader = 0;
        }
        return shader;
    }

    public static int createProgram(String vertexSource, String fragmentSource, String LOG_TAG) {
        int vShader = compileShader(GLES31.GL_VERTEX_SHADER, vertexSource, LOG_TAG);
        int fShader = compileShader(GLES31.GL_FRAGMENT_SHADER, fragmentSource, LOG_TAG);
        if(vShader == 0 || fShader ==  0) {
            return 0;
        }

        int program = GLES31.glCreateProgram();

        GLES31.glAttachShader(program, vShader);
        GLES31.glAttachShader(program, fShader);
        GLES31.glLinkProgram(program);

        GLES31.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES31.glGetProgramiv(program, GLES31.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES31.GL_TRUE) {
            Log.e(LOG_TAG, "Could not link program: ");
            Log.e(LOG_TAG, GLES31.glGetProgramInfoLog(program));
            GLES31.glDeleteProgram(program);
            program = 0;
        }

        GLES31.glDetachShader(program, vShader);
        GLES31.glDetachShader(program, fShader);
        GLES31.glDeleteShader(vShader);
        GLES31.glDeleteShader(fShader);

        return program;
    }

    //@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static int checkFramebufferStatus(String LOG_TAG) {
        int status = GLES31.glCheckFramebufferStatus(GLES31.GL_FRAMEBUFFER);
        boolean error = true;
        switch (status) {
            case GLES31.GL_FRAMEBUFFER_COMPLETE:
                Log.d(LOG_TAG, "Framebuffer is complete");
                //int[] result = new int[1];
                //GLES31.glGetFramebufferParameteriv(GLES31.GL_READ_FRAMEBUFFER, GLES31.GL_SAMPLE_BUFFERS, result, 0);
                //Log.d(LOG_TAG, result[0] + " sample buffers");
                error = false;
                break;
            case GLES31.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT:
                Log.e(LOG_TAG, "Framebuffer has incomplete attachment");
                break;
            case GLES31.GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS:
                Log.e(LOG_TAG, "Not all attached images have the same width and height. ");
                break;
            case GLES31.GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT:
                Log.e(LOG_TAG, "No images are attached to the framebuffer. ");
                break;
            case GLES31.GL_FRAMEBUFFER_UNSUPPORTED:
                Log.e(LOG_TAG, "Framebuffer has unsupported configuration");
                break;
        }

        if(error) {
            throw new RuntimeException("Framebuffer not complete, status=" + status);
        }

        return status;
    }

    /**
     * Allocates a direct float buffer, and populates it with the float array data.
     */
    public static FloatBuffer createFloatBuffer(float[] coords) {
        // Allocate a direct ByteBuffer, using 4 bytes per float, and copy coords into it.
        ByteBuffer bb = ByteBuffer.allocateDirect(coords.length * SIZEOF_FLOAT);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(coords);
        fb.position(0);
        return fb;
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
