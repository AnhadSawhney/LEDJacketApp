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
    private volatile static boolean keep_recording = true;
    private volatile static boolean currently_recording = false;

    private Middleman middleman;

    public AudioThread(Middleman middleman) {
        this.middleman = middleman;
    }

    public void run() {
        int SAMPLE_RATE = 44100; //22050; //11025; //When sample rate does not evenly divide 44.1kHz, slowdown
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
        short[] audioBuffer = new short[bufferSize/2]; // each SHORT is 2 BYTES

        float[] oscBuffer = new float[audioBuffer.length];

        double[] window = blackman_window(audioBuffer.length);

        double[] fftData = new double[audioBuffer.length*2];

        DoubleFFT_1D dfft = new DoubleFFT_1D(audioBuffer.length);

        float[] specBuffer = new float[audioBuffer.length];

        Log.d("AudioThread",String.format("bufferSize: %d", bufferSize));

        while (currently_recording) {
            Log.d("AudioThread","currently recoring, waiting 50ms before attempting again");
            // bufferSize is usually worth 80ms of audio
            // So waiting 50ms - this means, next attempt to grab AudioRecord will succeed.
            SystemClock.sleep(50);
        }
        record.startRecording();
        currently_recording = true;

        while (keep_recording) {
            record.read(audioBuffer, 0, audioBuffer.length); //audioBuffer.length is the number of shorts

            float max = 0;

            // Audio buffer contains signed PCM data, from -32767 to + 32767
            // Visualizers need floats from 0 to 1
            // Jtransforms FFT needs doubles from -1 to 1, with window applied

            for (int i = 0; i < audioBuffer.length /* && audioBuffer[i] == 0 */; i++) {
                oscBuffer[i] = (float) audioBuffer[i];
                float test = Math.abs(oscBuffer[i]);
                if (test > max) {
                    max = test;
                }
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

            for (int i = 0; i < audioBuffer.length; i++) {
                specBuffer[i] = specBuffer[i] / max;
            }

            // fft data is fit to log frequency scale in the draw function

            try {
                middleman.putSpecData(specBuffer); //Seems like the important part of the spectrum only takes up the first half
            }catch (InterruptedException ex) {
                Log.e("AudioThread", ex.getMessage());
            }

            //Log.d("FFTDATA", "arr: " + Arrays.toString(fftData));

        }
        record.stop();
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
