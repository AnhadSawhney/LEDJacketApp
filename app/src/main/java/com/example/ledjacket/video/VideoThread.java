package com.example.ledjacket.video;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.example.ledjacket.BuildConfig;
import com.example.ledjacket.R;

import java.io.File;
import java.nio.ByteBuffer;

//20131122: minor tweaks to saveFrame() I/O
//20131205: add alpha to EGLConfig (huge glReadPixels speedup); pre-allocate pixel buffers;
//          log time to run saveFrame()
//20140123: correct error checks on glGet*Location() and program creation (they don't set error)
//20140212: eliminate byte swap

// COURTESY OF https://bigflake.com/mediacodec/#ExtractMpegFramesTest
// https://stackoverflow.com/questions/19754547/mediacodec-get-all-frames-from-video
// https://www.sisik.eu/blog/android/media/video-to-grayscale
// https://stackoverflow.com/questions/34986857/processing-frames-from-mediacodec-output-and-update-the-frames-on-android
// https://gist.github.com/salememd/e78bb82559585239ee6f9128b10913a4

// Make imagereader fast: https://stackoverflow.com/questions/29965725/imagereader-in-android-needs-too-long-time-for-one-frame-to-be-available

// VIDEO FRAMES MUST BE EXTRACTED TO BITMAP THEN PASSED TO COMPUTE / FRAGMENT SHADER
// THIS IS SO THAT SOMETHING ELSE (LIKE OSCILLOSCOPE) CAN BE DRAWN TO A BITMAP AND PROCESSED IN THE SAME WAY

// MediaMetadataRetriever.getFrameAtTime is VERY SLOW (200ms/frame) SEE: https://stackoverflow.com/questions/56791589/how-to-get-frame-by-frame-from-mp4-mediacodec

// TODO: pass texture to fragment shader https://stackoverflow.com/questions/10398965/passing-textures-to-shader
// TODO: render to multiple textures https://stackoverflow.com/questions/50393858/multiple-texture-output-data-in-fragment-shader

/**
 * Extract frames from an MP4 using MediaExtractor, MediaCodec, and GLES.  Put a .mp4 file
 * in "/sdcard/source.mp4" and look for output files named "/sdcard/frame-XX.png".
 * <p>
 * This uses various features first available in Android "Jellybean" 4.1 (API 16).
 * <p>
 * (This was derived from bits and pieces of CTS tests, and is packaged as such, but is not
 * currently part of CTS.)
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class VideoThread implements Runnable {
    private static final String LOG_TAG = "VideoThread";
    private static final boolean VERBOSE = false; // lots of logging
    // TODO: get size from map, also crop
    private static final int saveWidth = 640;
    private static final int saveHeight = 360;
    private static final int TIMEOUT_USEC = 10000;

    private volatile static boolean keep_looping;
    private volatile static boolean currently_looping = false;

    private final Context context;

    private int trackIndex;

    private MediaCodec decoder = null;
    private CodecOutputSurface outputSurface = null;
    private MediaExtractor extractor = null;

    private Thread thread;

    private Bitmap mainBitmap;
    private Bitmap dataBitmap;

    /**
     * Tests extraction from an MP4 to a series of PNG files.
     * <p>
     * We scale the video to 640x480 for the PNG just to demonstrate that we can scale the
     * video with the GPU.  If the input video has a different aspect ratio, we could preserve
     * it by adjusting the GL viewport to get letterboxing or pillarboxing, but generally if
     * you're extracting frames you don't want black bars.
     */
    public VideoThread(Context context) {
        this.context = context;
        mainBitmap = Bitmap.createBitmap(saveWidth, saveHeight, Bitmap.Config.ARGB_8888);
        mainBitmap.setDensity(Bitmap.DENSITY_NONE); // stop auto-scaling
        // TODO: get width and height properly
        dataBitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888);
        dataBitmap.setDensity(Bitmap.DENSITY_NONE); // stop auto-scaling
        start();
    }

    // COURTESY OF https://stackoverflow.com/questions/24030756/mediaextractor-mediametadataretriever-with-raw-asset-file
    public void setFile(String filename) throws Throwable {
        //File inputFile = new File(FILES_DIR, INPUT_FILE);   // must be an absolute path

        //TODO: put files somewhere other than res/raw (in external storage outside of apk)

        Resources resources = context.getResources();

        int id = resources.getIdentifier(filename,"raw", BuildConfig.APPLICATION_ID);
        id = R.raw.wrmmm_beeple; // OVERRIDE, for testing

        // The MediaExtractor error messages aren't very useful.  Check to see if the input
        // file exists so we can throw a better one if it's not there.
        /*if (!inputFile.canRead()) {
            throw new FileNotFoundException("Unable to read " + inputFile);
        }*/

        AssetFileDescriptor afd = resources.openRawResourceFd(id);

        extractor = new MediaExtractor();
        extractor.setDataSource(afd.getFileDescriptor(),afd.getStartOffset(),afd.getLength());

        //extractor.setDataSource(inputFile.toString());

        trackIndex = findTrack(extractor);
        if (trackIndex < 0) {
            throw new RuntimeException("No video track found in " + filename); //inputFile);
        }
        extractor.selectTrack(trackIndex);

        MediaFormat format = extractor.getTrackFormat(trackIndex);
        if (VERBOSE) {
            Log.d(LOG_TAG, "Video size is " + format.getInteger(MediaFormat.KEY_WIDTH) + "x" +
                    format.getInteger(MediaFormat.KEY_HEIGHT));
        }

        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false; // prevent bitmap from getting scaled because we need the exact map image
        Bitmap bitmap = BitmapFactory.decodeResource(resources, R.drawable.map, o);
        //Log.d(LOG_TAG, bitmap.getWidth() + " " + bitmap.getHeight());

        // Could use width/height from the MediaFormat to get full-size frames.
        outputSurface = new CodecOutputSurface(saveWidth, saveHeight, bitmap);

        bitmap.recycle();

        // Create a MediaCodec decoder, and configure it with the MediaFormat from the
        // extractor.  It's very important to use the format from the extractor because
        // it contains a copy of the CSD-0/CSD-1 codec-specific data chunks.
        String mime = format.getString(MediaFormat.KEY_MIME);
        decoder = MediaCodec.createDecoderByType(mime);
        decoder.configure(format, outputSurface.getSurface(), null, 0); // renders to a Surface

        if(VERBOSE) {
            MediaCodecInfo info = decoder.getCodecInfo();
            Log.d(LOG_TAG, "Decoder Codec Info:");
            Log.d(LOG_TAG, "Name: " + info.getName());
            if(info.isHardwareAccelerated()) {
                Log.d(LOG_TAG, "Hardware accelerated");
            }
            Log.d(LOG_TAG, "Type: " + mime);
        }

        decoder.start();
    }

    public void start() {
        if(!currently_looping) {
            keep_looping = true;
            thread = new Thread(this);
            thread.start();
        }
    }

    @Override
    public void run() {
        // SET FILE MUST BE CALLED IN RUN FOR SOME GODFORSAKEN REASON
        try {
            setFile("null"); // TODO: set default animation to play
        } catch (Throwable ex) {
            Log.e(LOG_TAG, ex.getMessage()); //TODO: bad? handle this exception?
        }

        ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        int inputChunk = 0;
        //int decodeCount = 0;
        //long frameSaveTime = 0;

        boolean outputDone = false;
        //boolean inputDone = false;

        while (keep_looping) {
            long startTime = System.currentTimeMillis();
            //if (VERBOSE) Log.d(LOG_TAG, "Loop"); // NOISY

            // Feed more data to the decoder.
            //if (!inputDone) { // input is never done, because vide file loops
            int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
            if (inputBufIndex >= 0) {
                ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                // Read the sample data into the ByteBuffer.  This neither respects nor
                // updates inputBuf's position, limit, etc.

                int chunkSize = extractor.readSampleData(inputBuf, 0);
                if (chunkSize < 0) { // readSampleData returns -1 if no more samples are available
                    // End of stream -- send empty frame with EOS flag set.
                    //decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    //inputDone = true;
                    //if (VERBOSE) Log.d(LOG_TAG, "sent input EOS");

                    if (VERBOSE) Log.d(LOG_TAG, "Input EOS, go back to beginning");
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC); // Go back to beginning
                    chunkSize = extractor.readSampleData(inputBuf, 0);
                    inputChunk = 0;
                }

                if (extractor.getSampleTrackIndex() != trackIndex) {
                    Log.w(LOG_TAG, "WEIRD: got sample from track " + extractor.getSampleTrackIndex() + ", expected " + trackIndex);
                }

                long presentationTimeUs = extractor.getSampleTime();
                decoder.queueInputBuffer(inputBufIndex, 0, chunkSize, presentationTimeUs, 0);
                if (VERBOSE) Log.d(LOG_TAG, "submitted frame " + inputChunk + " to dec, size=" + chunkSize);
                inputChunk++;
                extractor.advance();
            } else {
                if (VERBOSE) Log.d(LOG_TAG, "input buffer not available");
            }
            //}

            if (!outputDone) {
                int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC); // decoderStatus should hold index of a buffer

                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    if (VERBOSE) Log.d(LOG_TAG, "no output from decoder available");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not important for us, since we're using Surface
                    if (VERBOSE) Log.d(LOG_TAG, "decoder output buffers changed");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = decoder.getOutputFormat();
                    if (VERBOSE) Log.d(LOG_TAG, "decoder output format changed: " + newFormat);
                } else if (decoderStatus < 0) {
                    Log.e(LOG_TAG,"unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                    //decoder.reset(); // requires API 21
                    // FAIL
                } else { // decoderStatus >= 0, so valid index of buffer
                    if (VERBOSE) Log.d(LOG_TAG, "surface decoder given buffer " + decoderStatus + " (size=" + info.size + ")");
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        if (VERBOSE) Log.d(LOG_TAG, "output EOS");
                        decoder.flush(); // reset decoder state
                        outputDone = true;
                    }

                    boolean doRender = (info.size != 0);

                    // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                    // to SurfaceTexture to convert to a texture.  The API doesn't guarantee
                    // that the texture will be available before the call returns, so we
                    // need to wait for the onFrameAvailable callback to fire.
                    decoder.releaseOutputBuffer(decoderStatus, doRender);

                    if (doRender) {
                        outputSurface.awaitNewImage();
                        outputSurface.drawImage(true);

                        outputSurface.putFrameOnBitmap(mainBitmap);
                    } else {
                        if (VERBOSE) Log.d(LOG_TAG, "rendering skipped");
                    }

                    // TODO: sleeping to slow down frame generation, and SPEED RAMP?
                }
            }

            long deltaTime = System.currentTimeMillis() - startTime;
            //Log.d(LOG_TAG, "Loop took: (ms) " + deltaTime);

            if (deltaTime < 32) { //refreshDelay) {
                try {
                    Thread.sleep(32 - deltaTime); // refreshDelay - deltaTime);
                } catch (InterruptedException ex) {
                    Log.e(LOG_TAG, ex.getMessage());
                }
            }
        }

        if (VERBOSE) Log.d(LOG_TAG, "Done looping");

        // release everything we grabbed
        if (outputSurface != null) {
            outputSurface.release();
            outputSurface = null;
        }

        if (decoder != null) {
            decoder.stop();
            decoder.release();
            decoder = null;
        }

        if (extractor != null) {
            extractor.release();
            extractor = null;
        }
    }

    public void stop() {
        keep_looping = false;
        try {
            thread.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
        currently_looping = false;
    }

    /**
     * Selects the video track, if any.
     * @return the track index, or -1 if no video track is found.
     */
    private int findTrack(MediaExtractor extractor) {
        // Select the first video track we find, ignore the rest.
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                if (VERBOSE) Log.d(LOG_TAG, "Extractor selected track " + i + " (" + mime + "): " + format);
                return i;
            }
        }

        return -1;
    }

    public Bitmap getMainBitmap() {
        return mainBitmap;
    }

    public Bitmap getDataBitmap() {
        return mainBitmap;
    }
}
