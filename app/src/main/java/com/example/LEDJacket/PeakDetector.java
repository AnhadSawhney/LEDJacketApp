package com.example.ledjacket;

// Courtesy of Brakel, J.P.G. van (2014). "Robust peak detection algorithm using z-scores".
// Stack Overflow. Available at:
// https://stackoverflow.com/questions/22583391/peak-signal-detection-in-realtime-timeseries-data/22640362#22640362
// (version: 2020-11-08).

// Modified a lot by me

public class PeakDetector {

    private int lag;

    private double threshold;

    private double influence;

    private int dataCount; // counts up as new data comes in

    // Running statistics
    private float newavg;
    private float oldavg;
    private float newstd;
    private float oldstd;
    private float oldvalue;

    public PeakDetector(int lag, double threshold, double influence) {
        this.lag = lag;
        this.threshold = threshold;
        this.influence = influence;
        reset();
    }

    public void reset() {
        dataCount = 0;
        newavg = 0;
        oldavg = 0;
        newstd = 0;
        oldstd = 0;
        oldvalue = 0;
    }

    // Running stats courtesy of https://stackoverflow.com/questions/1174984/how-to-efficiently-calculate-a-running-standard-deviation/17637351#17637351

    public float run(float value) { // shifts everything in data over by 1
        float out = 0;
        float filteredValue = value;
        dataCount++;

        float stdDev = (float) Math.sqrt( oldstd / (dataCount - 1)); // population standard deviation, divide by n (in this case dataCount - 1 because oldstd)

        if(dataCount > lag) {
            if(Math.abs(value - oldavg) > threshold * stdDev) {
                out = value; // value is a peak, let it through the filter
                // compare with the running average to discern between positive and negative peaks, not used here because peaks are always positive
                filteredValue = (float) (influence * value + (1 - influence) * oldvalue);
            }
        } // else return 0 (out)

        // Update running statistics
        if (dataCount == 1) { // First value
            oldavg = value;
            // this only happens after reset() so oldstd and newstd are 0
        } else {
            newavg = oldavg + (filteredValue - oldavg) / dataCount;
            newstd = oldstd + (filteredValue - oldavg) * (filteredValue - newavg);
        }

        oldavg = newavg;
        oldstd = newstd;
        oldvalue = value;

        // mean: newavg
        // variance: newstd / (dataCount - 1) if dataCount > 1 else 0
        // standard deviation: Math.sqrt(variance)

        return out;
    }
}