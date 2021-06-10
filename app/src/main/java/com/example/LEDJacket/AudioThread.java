package com.example.ledjacket;

import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;
import android.util.Log;

import org.jtransforms.fft.DoubleFFT_1D;

import java.util.Arrays;

import static android.media.AudioFormat.CHANNEL_IN_MONO;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;

//COURTESY OF https://github.com/Plasmabot1/Android-Loopback-Oscilloscope

public class AudioThread implements Runnable {
    private static int refreshDelay = 16; // 60 FPS

    private volatile static boolean keep_recording = true;
    private volatile static boolean currently_recording = false;

    private GrabAudio grabAudio;

    private Middleman middleman;

    private float runningmax;

    private PeakDetector peakDetector = new PeakDetector(10, 2, 0);

    public AudioThread(Middleman middleman) {
        this.middleman = middleman;
    }

    public void run() {
        /*int SAMPLE_RATE = 44100; //22050; //11025; //When sample rate does not evenly divide 44.1kHz, slowdown
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, // bufferSize in BYTES
                CHANNEL_IN_MONO,
                ENCODING_PCM_16BIT);

        //minbuffersize gives 3584

        // Override buffersize to a power of 2
        if(4096 < bufferSize) {
            Log.d("AudioThread","MIN BUFFERSIZE > 4096");
        } else {
            bufferSize = 4096;
        }

        AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                SAMPLE_RATE,
                CHANNEL_IN_MONO,
                ENCODING_PCM_16BIT,
                bufferSize); // in BYTES
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.d("AudioThread","ERROR in bufferSize, size = samplerate * 2");
            bufferSize = SAMPLE_RATE * 2;
        }
         */

        runningmax = 0;

        // TODO: check valid capture sizes, max seems to be 1024
        // TODO: add back in functionality to get mic audio
        // TODO: merge grabaudio and audiothread
        // TODO: Use visualizer fft

        int bufferSize = 1024;

        grabAudio = new GrabAudio(0, bufferSize, 0);

        // used to be short array
        int[] audioBuffer = new int[bufferSize]; // each SHORT is 2 BYTES, so divide length by 2 for short array

        float[] oscBuffer = new float[audioBuffer.length];

        double[] window = blackman_window(audioBuffer.length);

        double[] fftData = new double[audioBuffer.length*2];

        DoubleFFT_1D dfft = new DoubleFFT_1D(audioBuffer.length);

        float[] specBuffer = new float[audioBuffer.length];

        float[] beatDetect = new float[300]; // TODO: what is a good size?
        float[] beatBuffer = new float[300];

        Log.d("AudioThread",String.format("bufferSize: %d", bufferSize));

        while (currently_recording) {
            Log.d("AudioThread","currently recoring, waiting 50ms before attempting again");
            // bufferSize is usually worth 80ms of audio
            // So waiting 50ms - this means, next attempt to grab AudioRecord will succeed.
            SystemClock.sleep(50);
        }

        //record.startRecording();
        grabAudio.start();
        currently_recording = true;

        while (keep_recording) {
            long startTime = System.currentTimeMillis();
            //record.read(audioBuffer, 0, audioBuffer.length); //audioBuffer.length is the number of shorts

            // FIX: this is giving weird clicks
            
            //TODO: get samplerate

            audioBuffer = grabAudio.getFormattedData(1, 1);

            float max = 0;

            // Audio buffer contains signed PCM data, from -127 to 127
            // Visualizers need floats from 0 to 1
            // Jtransforms FFT needs doubles from -1 to 1, with window applied

            for (int i = 0; i < audioBuffer.length /* && audioBuffer[i] == 0 */; i++) {
                oscBuffer[i] = (float) audioBuffer[i];
                float test = Math.abs(oscBuffer[i]);
                /*if(test >= 50) { // limiter
                    if(i > 0) {
                        oscBuffer[i] = oscBuffer[i-1];
                    } else {
                        oscBuffer[i] = 0;
                    }
                } else { */
                    if (test > max) {
                        max = test;
                    }
                //}
            }

            // max = 128;

            if(max == 0) { // prevent div/0 error
                max = 1;
            }

            //Log.d("AUDIOBUFFER", "arr: " + Arrays.toString(audioBuffer));
            //Log.i("LENGTH:", String.valueOf(audioBuffer.length));

            Arrays.fill(fftData, 0); // Clear fftData (may not be necessary)

            for (int i = 0; i < audioBuffer.length; i++) {
                fftData[i] = (double) (oscBuffer[i] * window[i] / max);
            }
            
            max = max * 2.0f;

            for (int i = 0; i < audioBuffer.length; i++) {
                oscBuffer[i] = oscBuffer[i] / max + 0.5f;
            }

            try {
                middleman.putOscData(oscBuffer);
            }catch (InterruptedException ex) {
                Log.e("AudioThread", ex.getMessage());
            }

            // See http://incanter.org/docs/parallelcolt/api/edu/emory/mathcs/jtransforms/fft/DoubleFFT_1D.html#realForwardFull(double[])

            // Real data in the first half of fftData, zeroes in the second half

            dfft.realForwardFull(fftData);

            max = 0;

            for(int i = 0; i < audioBuffer.length; i++) { // get the magnitude of each value
                double re = fftData[2*i];
                double im = fftData[2*i+1];
                specBuffer[i] = (float) Math.sqrt(re * re + im * im);
                float test = Math.abs(specBuffer[i]);
                if (test > max) {
                    max = test;
                }
            }

            if(max == 0) { // prevent div/0 error
                max = 1;
            }

            if(max > runningmax) { // Running max for normalizing fft data
                runningmax = max;
            }

            for (int i = 0; i < audioBuffer.length; i++) {
                specBuffer[i] = (float) Math.sqrt(specBuffer[i] / runningmax);
            }

            //TODO: fix this, this is a bad way of truncating
            // Stop at band 233 = 10kHz
            float[] tmp = new float[233];
            System.arraycopy(specBuffer, 0, tmp, 0, 233);

            //Log.v("Runningmax", String.valueOf(runningmax));
            // Runningmax becomes 191.89 for pure sine tone

            // fft data is fit to log frequency scale in the draw function

            try {
                middleman.putSpecData(tmp);
            }catch (InterruptedException ex) {
                Log.e("AudioThread", ex.getMessage());
            }

            for(int i = beatDetect.length-1; i > 0; i--) {
                beatDetect[i] = beatDetect[i-1];
            }

            float beat = (tmp[3] + tmp[4]) / 2.0f; // TODO: bad way of calculating beats

            beatDetect[0] = peakDetector.run(beat);



            //beatBuffer[0] = beat - runningavg + 0.5f;

            try {
                middleman.putBeatData(beatDetect);
            }catch (InterruptedException ex) {
                Log.e("AudioThread", ex.getMessage());
            }

            //Log.d("FFTDATA", "arr: " + Arrays.toString(fftData));
            long deltaTime = System.currentTimeMillis() - startTime;

            if (deltaTime < refreshDelay) { // Slow down audio refresh rate. Clicks happen when it goes so fast it conflicts with drawing threads reading data
                try {
                    Thread.sleep(refreshDelay - deltaTime);
                } catch (InterruptedException ex) {
                    Log.e("AudioThread", ex.getMessage());
                }
            }
        }
        //record.stop();
        grabAudio.stop();
        grabAudio.release();
        grabAudio = null;
        currently_recording = false;
    }

    public void stop_recording() {
        keep_recording = false;
    }

    private double[] blackman_window(int length) {
        double phase = 0, delta;
        double[] out = new double[length];

        delta = 2 * Math.PI / (double)length;

        for (int i = 0; i < length; i++)
        {
            out[i] = (double)(0.42 - .5 * Math.cos(phase) + .08 * Math.cos(2 * phase));
            phase += delta;
        }

        return out;
    }
}
