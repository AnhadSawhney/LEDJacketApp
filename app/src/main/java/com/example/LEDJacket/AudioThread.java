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

public class AudioThread extends Thread {
    private volatile static boolean keep_recording = true;
    private volatile static boolean currently_recording = false;

    @Override
    public void run() {
        int SAMPLE_RATE = 44100; //22050; //11025; //When sample rate does not evenly divide 44.1kHz, slowdown
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, // bufferSize in BYTES
                CHANNEL_IN_MONO,
                ENCODING_PCM_16BIT);
        //bufferSize = bufferSize*2;
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

        double[] window = blackman_window(audioBuffer.length);

        double[] fftin = new double[audioBuffer.length];

        DoubleFFT_1D dfft = new DoubleFFT_1D(audioBuffer.length);

        double[] fftout = new double[audioBuffer.length];

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

            double max = 0;

            //Log.d("AUDIOBUFFER", "arr: " + Arrays.toString(audioBuffer));
            for (int i = 0; i < audioBuffer.length /* && audioBuffer[i] == 0 */; i++) {
                fftin[i] = (double) audioBuffer[i] - 16384.0;
                if (fftin[i] > max || -fftin[i] > max) {
                    max = fftin[i];
                }
            }

            //Log.i("NUM_ZEROES:", String.valueOf(ix));
            //Log.i("LENGTH:", String.valueOf(audioBuffer.length));
            // GOOD DATA (no leading 0s) IS BEING SENT TO C++ when array is of size bufferSize/2
            // when array is of size bufferSize (twice as large as min), more good data is pulled from record

            for (int i = 0; i < audioBuffer.length; i++) {
                fftin[i] = fftin[i] * window[i] / max;
            }

            //dfft.realForwardFull(fftin);

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
