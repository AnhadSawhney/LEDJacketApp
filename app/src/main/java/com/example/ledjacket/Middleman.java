package com.example.ledjacket;

// Used to pass data from AudioThread to SurfaceViewThreads

import android.view.SurfaceView;

import com.example.ledjacket.video.VideoThread;

public class Middleman {
    private float[] oscData;
    private float[] specData;
    private float[] beatData;
    private float[] luminosityData;
    
    private VideoThread videoThread = null;

    //private SurfaceView mainView = null;

    public Middleman() {}

    // Synchronized not really necessary here because there is only one thread calling put(), and one thread calling each get()

    public synchronized void putOscData(float[] oscData) throws InterruptedException {
        this.oscData = oscData;
        notifyAll();
    }

    public synchronized float[] getOscData() throws InterruptedException {
        return oscData;
    }

    public synchronized void putSpecData(float[] specData) throws InterruptedException {
        this.specData = specData;
        notifyAll();
    }

    public synchronized float[] getSpecData() throws InterruptedException {
        return specData;
    }

    public synchronized void putBeatData(float[] beatData) throws InterruptedException {
        this.beatData = beatData;
        notifyAll();
    }

    public synchronized float[] getBeatData() throws InterruptedException {
        return beatData;
    }

    public synchronized void putLuminosityData(float[] luminosityData) throws InterruptedException {
        this.luminosityData = luminosityData;
        notifyAll();
    }

    public synchronized float[] getLuminosityData() throws InterruptedException {
        return luminosityData;
    }

    public boolean isOscEmpty(){
        return oscData == null;
    }

    public boolean isSpecEmpty(){
        return oscData == null;
    }

    public boolean isBeatEmpty(){
        return beatData == null;
    }

    public boolean isLuminosityEmpty(){
        return luminosityData == null;
    }

    public VideoThread getVideoThread() {
        return videoThread;
    }

    public void setVideoThread(VideoThread videoThread) {
        this.videoThread = videoThread;
    }
}
