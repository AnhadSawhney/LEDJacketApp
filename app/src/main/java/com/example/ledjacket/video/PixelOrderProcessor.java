package com.example.ledjacket.video;

import static com.example.ledjacket.video.ShaderHelpers.checkGlError;
import static com.example.ledjacket.video.ShaderHelpers.checkLocation;
import static com.example.ledjacket.video.ShaderHelpers.createProgram;
import static com.example.ledjacket.video.ShaderHelpers.loadGLTextureFromBitmap;
import static com.example.ledjacket.video.ShaderHelpers.setTexParameters;

import android.graphics.Bitmap;
import android.opengl.GLES11Ext;
import android.opengl.GLES31;

public class PixelOrderProcessor {
    private static final String LOG_TAG = "PixelOrderProcessor";
    private int mWidth;
    private int mHeight;

    // FROM:https://github.com/detsikas/ComputeShaderExample
    // SEE: https://antongerdelan.net/opengl/compute.html
    // https://stackoverflow.com/questions/38994785/opengl-shader-fails-to-compile-on-device
    private static final String COMPUTE_SHADER =
        "#version 310 es\n" +
        "#extension GL_OES_EGL_image_external_essl3 : require\n" +
        "precision lowp image2D;\n" +
        // NUM_X * NUM_Y * NUM_Z threads per work group.
        "layout(local_size_x = 16, local_size_y = 8, local_size_z = 1) in;\n" +
        // INPUT TEXTURES
        "layout(rgba32f, binding = 0) uniform readonly sampler2D videoTexture;\n" + // This is the surfaceTexture with the video frame
        "layout(rgba32f, binding = 1) uniform readonly sampler2D mapTexture;\n" + // This texture is the mapBitmap
        // OUTPUT TEXTURES, layouts: https://www.khronos.org/opengl/wiki/Layout_Qualifier_(GLSL)
        //"layout(rgba32f, binding = 2) uniform writeonly image2D mainImage;\n" + // binding qualifier not necessary?
        "layout(rgba32f, binding = 3) uniform writeonly image2D dataImage;\n" +
        "void main() {\n" +
        "   ivec2 texelCoords = ivec2(gl_GlobalInvocationID.xy);\n" +
        "   ivec2 bounds = textureSize(videoTexture, 0);\n" +
        "   if (texelCoords.x < bounds.x && texelCoords.y < bounds.y) {\n"+
        "       vec4 videoColor = texelFetch(videoTexture, texelCoords, 0);\n" + // colors are floats from 0-1
        "       vec4 mapColor = texelFetch(mapTexture, texelCoords, 0);\n" +
        //"       int row = (int) floor(mapColor.r * 255.);\n" + // float to byte
        //"       int column = (int) floor(mapColor.b * 65535. + mapColor.g * 255.);\n" + // float to 16 bit int
        "       if (mapColor.r == 1.) {\n" +
        "           ivec2 mapCoords = ivec2(floor(mapColor.b * 65535. + mapColor.g * 255.), floor(mapColor.r * 255.));\n" + // TODO: calculate row and column
        //"           imageStore(mainImage, texelCoords, vec4(0.063,0.063,0.063,1.));\n" + // clear to RGBA(16,16,16, 255) to avoid black smearing
        "           imageStore(dataImage, mapCoords, videoColor);\n" +
        "       }\n" +
        "   }\n" +
        "}\n";

    private int mProgram;
    private int videoTextureID = -12345; // invalid value, gets overridden by glGenTextures
    private int mapTextureID = -12345;
    //private int mainImageID = -12345;
    private int dataImageID = -12345;
    private final int WORK_GROUPS_X = mWidth / 16; // TODO: what is a good value for this
    private final int WORK_GROUPS_Y = mHeight / 8;
    private static final int WORK_GROUPS_Z = 1;

    private int mapTextureHandle;
    //private int mainImageHandle;
    private int dataImageHandle;

    public void PixelOrderProcessor() {


    }

    // everything here and below is a mess

    public void execute() {
        GLES31.glUseProgram(mProgram);
        checkGlError("glUseProgram", LOG_TAG);

        // From: https://github.com/ibraimgm/opengles2-2d-demos/blob/master/src/ibraim/opengles2/TextureActivity.java
        GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
        GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureID);
        checkGlError("glBindTexture", LOG_TAG);

        // TODO: move stuff out of drawFrame?
        //GLES31.glUniform1i(mapTextureID, 1); // not needed because of binding qualifier
        GLES31.glActiveTexture(GLES31.GL_TEXTURE1);
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, mapTextureID);
        checkGlError("glBindTexture mapTextureHandle", LOG_TAG);

        //GLES31.glUniform1i(mainImageHandle, 2); // not needed because of binding qualifier
        //GLES31.glActiveTexture(GLES31.GL_TEXTURE2);
        //GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, mainImageID);

        //setTexParameters(GLES31.GL_TEXTURE_2D);
        //GLES31.glBindImageTexture(2, mainImageID, 0, false, 0, GLES31.GL_WRITE_ONLY, GLES31.GL_RGBA32F);
        //checkGlError("glBindImageTexture mainImageHandle", LOG_TAG);

        //GLES31.glUniform1i(dataImageHandle, 3); // not needed because of binding qualifier
        GLES31.glActiveTexture(GLES31.GL_TEXTURE3);
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, dataImageID);

        setTexParameters(GLES31.GL_TEXTURE_2D);
        GLES31.glBindImageTexture(3, dataImageID, 0, false, 0, GLES31.GL_READ_WRITE, GLES31.GL_RGBA32F);
        checkGlError("glBindImageTexture dataImageHandle", LOG_TAG);

        //if(VERBOSE) Log.d(LOG_TAG, "about to dispatch compute");

        GLES31.glDispatchCompute(WORK_GROUPS_X, WORK_GROUPS_Y, WORK_GROUPS_Z);
        checkGlError("glDispatchCompute", LOG_TAG);

        // SHUT DOWN
        GLES31.glFinish();
        //GLES31.glDeleteProgram(mProgram);
        GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0); // release main texture
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, 0); // release mapBitmap texture
        GLES31.glMemoryBarrier(GLES31.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT); // TODO: move this somewhere useful
    }

    /**
     * Initializes GL state.  Call this after the EGL surface has been created and made current.
     */
    public void surfaceCreated() {
        mProgram = createProgram(COMPUTE_SHADER, LOG_TAG);

        mapTextureHandle = GLES31.glGetUniformLocation(mProgram, "mapTexture");
        checkLocation(mapTextureHandle, "mapTexture");

        //mainImageHandle = GLES31.glGetUniformLocation(mProgram, "mainImage");
        //checkLocation(mainImageHandle, "mainImage");

        dataImageHandle = GLES31.glGetUniformLocation(mProgram, "dataImage");
        checkLocation(dataImageHandle, "dataImage");

        int[] textures = new int[3];
        GLES31.glGenTextures(3, textures, 0);

        videoTextureID = textures[0];
        mapTextureID = textures[1];
        //mainImageID = textures[2];
        dataImageID = textures[2];

        GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureID);
        checkGlError("glBindTexture videoTextureID", LOG_TAG);

        // MINIMIZATION FILTER IS TAKING EFFECT
        setTexParameters(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);

        //loadGLTextureFromBitmap(mapBitmap);

        //GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, mainImageID);
        //setTexParameters(GLES31.GL_TEXTURE_2D);
        //GLES31.glTexStorage2D(GLES31.GL_TEXTURE_2D, 1, GLES31.GL_RGBA32F, mWidth, mHeight);
        //checkGlError("glTexStorage2D mainImageHandle", LOG_TAG);

        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, dataImageID);
        setTexParameters(GLES31.GL_TEXTURE_2D);                             // Get from python script 286, 128
        GLES31.glTexStorage2D(GLES31.GL_TEXTURE_2D, 1, GLES31.GL_RGBA32F, 300, 200);
        checkGlError("glTexStorage2D mainImageHandle", LOG_TAG);

        GLES31.glViewport(0, 0, mWidth, mHeight);

        /*int[] fbos = new int[1];
        GLES31.glGenFramebuffers(1, fbos, 0);
        //mainFBO = fbos[0];
        dataFBO = fbos[0];
        checkGlError("glGenFramebuffers", LOG_TAG);

        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, mainFBO); // READ OR DRAW?
        GLES31.glFramebufferTexture2D(GLES31.GL_FRAMEBUFFER, GLES31.GL_COLOR_ATTACHMENT0, GLES31.GL_TEXTURE_2D, mainImageID, 0);
        checkGlError("glFramebufferTexture2D mainFBO", LOG_TAG);

        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, dataFBO); // READ OR DRAW?
        GLES31.glFramebufferTexture2D(GLES31.GL_FRAMEBUFFER, GLES31.GL_COLOR_ATTACHMENT0, GLES31.GL_TEXTURE_2D, dataImageID, 0);
        checkGlError("glFramebufferTexture2D mainFBO", LOG_TAG); */

        /*int[] pbos = new int[1];
        GLES31.glGenBuffers(1, pbos, 0);
        //mainPBO = pbos[0];
        dataPBO = pbos[1];
        checkGlError("glGenBuffers", LOG_TAG);

        //GLES31.glBindBuffer(GLES31.GL_PIXEL_PACK_BUFFER, mainPBO);
        //GLES31.glBufferData(GLES31.GL_PIXEL_PACK_BUFFER, mainPixelBufSize, null, GLES31.GL_DYNAMIC_READ);
        GLES31.glBindBuffer(GLES31.GL_PIXEL_PACK_BUFFER, dataPBO);
        GLES31.glBufferData(GLES31.GL_PIXEL_PACK_BUFFER, dataPixelBufSize, null, GLES31.GL_DYNAMIC_READ);
        GLES31.glBindBuffer(GLES31.GL_PIXEL_PACK_BUFFER, 0);
        checkGlError("glBufferData", LOG_TAG);*/
    }

    /*
    // TODO: speedup? https://vec.io/posts/faster-alternatives-to-glreadpixels-and-glteximage2d-in-opengl-es
    public void putDataOnBitmap(Bitmap bmp) { // Bitmap must have Bitmap.Config.ARGB_8888, mWidth, mHeight
        mainPixelBuf.rewind();
        GLES31.glReadPixels(0, 0, mWidth, mHeight, GLES31.GL_RGBA, GLES31.GL_UNSIGNED_BYTE, mainPixelBuf);
        //Bitmap bmp = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
        bmp.copyPixelsFromBuffer(mainPixelBuf);
    }

     */

    /*
    // SEE: https://stackoverflow.com/questions/53993820/opengl-es-2-0-android-c-glgetteximage-alternative
    // TODO: move to sTextureRender and create wrappers in CodecOutputSurface
    public void putMainImageOnBitmap() {
        checkGlError("before reading image", LOG_TAG);

        int[] t = new int[1];
        GLES31.glGenTextures(1, t, 0);
        int id = t[0];
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, id);
        mTextureRender.setTexParameters(GLES31.GL_TEXTURE_2D);
        //GLUtils.texImage2D(GLES31.GL_TEXTURE_2D, 0, mapBitmap, 0);
        GLES31.glTexStorage2D(GLES31.GL_TEXTURE_2D, 1, GLES31.GL_RGBA32F, mWidth, mHeight);

        mTextureRender.checkGlError("texImage2D");

        // PBOS are used to speedup glReadPixels, however FBOS are still needed to bind and read textures

        //GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, mainFBO); // bind to both read and draw framebuffers
        // should this be read framebuffer?

        // done on FBO creation
        //GLES31.glFramebufferTexture2D(GLES31.GL_FRAMEBUFFER, GLES31.GL_COLOR_ATTACHMENT0, GLES31.GL_TEXTURE_2D, mTextureRender.mainImageID, 0);
        //mTextureRender.checkGlError("glFramebufferTexture2D mainFBO");
        //checkFramebufferStatus(GLES31.GL_READ_FRAMEBUFFER);

        GLES31.glBindFramebuffer(GLES31.GL_READ_FRAMEBUFFER, mainFBO);
        GLES31.glBindFramebuffer(GLES31.GL_DRAW_FRAMEBUFFER, 0);

        // https://stackoverflow.com/questions/58042393/rendering-compute-shader-output-to-screen
        GLES31.glBlitFramebuffer(0, 0, mWidth, mHeight,
                                 0, 0, mWidth, mHeight,
                                GLES31.GL_COLOR_BUFFER_BIT, GLES31.GL_NEAREST);



        //GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, 0);

        //GLES31.glBindBuffer(GLES31.GL_PIXEL_PACK_BUFFER, 0);
        //mTextureRender.checkGlError("glBindBuffer");

        //GLES31.glReadBuffer(GLES31.GL_COLOR_ATTACHMENT0);
        //mTextureRender.checkGlError("glReadBuffer");

        mainPixelBuf.rewind();
        GLES31.glReadPixels(0, 0, mWidth, mHeight, GLES31.GL_RGBA, GLES31.GL_UNSIGNED_BYTE, mainPixelBuf);
        checkGlError("glReadPixels mainPixelBuf", LOG_TAG);

        bmp.copyPixelsFromBuffer(mainPixelBuf);

        //GLES31.glBindFramebuffer(GLES31.GL_READ_FRAMEBUFFER, 0); // unbind for when other put___OnBitmap() is called

        // Speedup with pbo?

        //GLES31.glBindBuffer(GLES31.GL_PIXEL_PACK_BUFFER, mainPBO);
        //GLES31.glBufferData(GLES31.GL_PIXEL_PACK_BUFFER, size, null, GLES31.GL_STATIC_READ);
        //mTextureRender.checkGlError("glBufferData mainPixelBuf");

        // THIS STILL READS FROM FRAMEBUFFER
        //GLES31.glReadPixels(0, 0, mWidth, mHeight, GLES31.GL_RGBA, GLES31.GL_UNSIGNED_BYTE, 0);


        //mainPixelBuf = (ByteBuffer)GLES31.glMapBufferRange(GLES31.GL_PIXEL_PACK_BUFFER, 0, size, GLES31.GL_MAP_READ_BIT);
        // put the pixels in the buffer here
        //GLES31.glUnmapBuffer(GLES31.GL_PIXEL_PACK_BUFFER);

        //GLES31.glBindBuffer(GLES31.GL_PIXEL_PACK_BUFFER, 0);
    }

     */
}
