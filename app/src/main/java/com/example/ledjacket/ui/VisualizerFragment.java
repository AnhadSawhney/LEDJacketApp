package com.example.ledjacket.ui;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.ledjacket.Middleman;
import com.example.ledjacket.graphs.GraphThread;
import com.example.ledjacket.databinding.FragmentVisualizerBinding;
import com.example.ledjacket.video.BitmapView;

/**
 * A placeholder fragment containing a simple view.
 */
public class VisualizerFragment extends Fragment {

    private static final String ARG_SECTION_NUMBER = "section_number";

    //private PageViewModel pageViewModel;
    private FragmentVisualizerBinding binding;

    private Context context;

    private Middleman middleman;

    public void setMiddleman(Middleman middleman) {
        this.middleman = middleman;
    }

    public static VisualizerFragment newInstance(int index) {
        VisualizerFragment fragment = new VisualizerFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(ARG_SECTION_NUMBER, index);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //pageViewModel = new ViewModelProvider(this).get(PageViewModel.class);
        int index = 1;
        if (getArguments() != null) {
            index = getArguments().getInt(ARG_SECTION_NUMBER);
        }
        //pageViewModel.setIndex(index);
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        binding = FragmentVisualizerBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        context = getActivity();

        //START ALL THE THREADS

        //AUDIOTHREAD CALLS A CALLBACK FUNCTION FROM EACH OF THESE THREADS TO FEED THEM NEW DATA AND FORCE THEM TO DRAW AGAIN
        //THAT WAY THEY UPDATE WHENEVER THE AUDIOTHREAD IS READY

        // NONE OF THE VISUALIZATIONS CAN EXTEND VIEW BECAUSE IT STOPS UPDATING PERIODICALLY

        final GraphThread swt1 = (GraphThread) binding.oscilloscopeSurfaceView;
        final GraphThread swt2 = (GraphThread) binding.spectrumSurfaceView;
        final GraphThread swt3 = (GraphThread) binding.beatSurfaceView;

        swt1.setMiddleman(middleman);
        swt2.setMiddleman(middleman);
        swt3.setMiddleman(middleman);

        final SurfaceView mainView = binding.mainView;
        final BitmapView dataView = binding.dataView;

        //mainView.setBitmap(middleman.getVideoThread().getMainBitmap());
        // wait for videothread to make the output surface
        while(middleman.getVideoThread().getCodecOutputSurface() == null);

        Log.d("Visualizer Fragment", "Ask for outputsurface from videothread");
        mainView.getHolder().addCallback(middleman.getVideoThread().getCodecOutputSurface());
        //dataView.setBitmap(middleman.getVideoThread().getDataBitmap());

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}