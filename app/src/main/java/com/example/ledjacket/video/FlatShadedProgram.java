/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.ledjacket.video;

import static com.example.ledjacket.video.ShaderHelpers.*;
import android.opengl.GLES31;
import android.util.Log;

import java.nio.FloatBuffer;

/**
 * GL program and supporting functions for flat-shaded rendering.
 */
public class FlatShadedProgram {
    private static final String TAG = "flatshadedprogram";

    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;" +
                    "attribute vec4 aPosition;" +
                    "void main() {" +
                    "    gl_Position = uMVPMatrix * aPosition;" +
                    "}";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;" +
                    "uniform vec4 uColor;" +
                    "void main() {" +
                    "    gl_FragColor = uColor;" +
                    "}";

    // Handles to the GL program and various components of it.
    private int mProgramHandle = -1;
    private int muColorLoc = -1;
    private int muMVPMatrixLoc = -1;
    private int maPositionLoc = -1;


    /**
     * Prepares the program in the current EGL context.
     */
    public FlatShadedProgram() {
        mProgramHandle = createProgram(VERTEX_SHADER, FRAGMENT_SHADER, TAG);
        if (mProgramHandle == 0) {
            throw new RuntimeException("Unable to create program");
        }
        Log.d(TAG, "Created program " + mProgramHandle);

        // get locations of attributes and uniforms

        maPositionLoc = GLES31.glGetAttribLocation(mProgramHandle, "aPosition");
        checkLocation(maPositionLoc, "aPosition");
        muMVPMatrixLoc = GLES31.glGetUniformLocation(mProgramHandle, "uMVPMatrix");
        checkLocation(muMVPMatrixLoc, "uMVPMatrix");
        muColorLoc = GLES31.glGetUniformLocation(mProgramHandle, "uColor");
        checkLocation(muColorLoc, "uColor");
    }

    /**
     * Releases the program.
     */
    public void release() {
        GLES31.glDeleteProgram(mProgramHandle);
        mProgramHandle = -1;
    }

    /**
     * Issues the draw call.  Does the full setup on every call.
     *
     * @param mvpMatrix The 4x4 projection matrix.
     * @param color A 4-element color vector.
     * @param vertexBuffer Buffer with vertex data.
     * @param firstVertex Index of first vertex to use in vertexBuffer.
     * @param vertexCount Number of vertices in vertexBuffer.
     * @param coordsPerVertex The number of coordinates per vertex (e.g. x,y is 2).
     * @param vertexStride Width, in bytes, of the data for each vertex (often vertexCount *
     *        sizeof(float)).
     */
    public void draw(float[] mvpMatrix, float[] color, FloatBuffer vertexBuffer,
                     int firstVertex, int vertexCount, int coordsPerVertex, int vertexStride) {
        checkGlError("draw start", TAG);

        // Select the program.
        GLES31.glUseProgram(mProgramHandle);
        checkGlError("glUseProgram", TAG);

        // Copy the model / view / projection matrix over.
        GLES31.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mvpMatrix, 0);
        checkGlError("glUniformMatrix4fv", TAG);

        // Copy the color vector in.
        GLES31.glUniform4fv(muColorLoc, 1, color, 0);
        checkGlError("glUniform4fv ", TAG);

        // Enable the "aPosition" vertex attribute.
        GLES31.glEnableVertexAttribArray(maPositionLoc);
        checkGlError("glEnableVertexAttribArray", TAG);

        // Connect vertexBuffer to "aPosition".
        GLES31.glVertexAttribPointer(maPositionLoc, coordsPerVertex,
                GLES31.GL_FLOAT, false, vertexStride, vertexBuffer);
        checkGlError("glVertexAttribPointer", TAG);

        // Draw the rect.
        GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, firstVertex, vertexCount);
        checkGlError("glDrawArrays", TAG);

        // Done -- disable vertex array and program.
        GLES31.glDisableVertexAttribArray(maPositionLoc);
        GLES31.glUseProgram(0);
    }
}
