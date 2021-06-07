package com.example.ledjacket;

// Used to pass data from AudioThread to SurfaceViewThreads

public class Middleman {
    private float[] oscData;
    private float[] specData;
    private float[] beatData;

    public Middleman() {}

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

    public boolean isOscEmpty(){
        return oscData == null;
    }

    public boolean isSpecEmpty(){
        return oscData == null;
    }

    public boolean isbeatEmpty(){
        return oscData == null;
    }
}
