package com.example.ledjacket.ui.main;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.SurfaceView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.ledjacket.Middleman;
import com.example.ledjacket.SurfaceViewThread;
import com.example.ledjacket.databinding.FragmentVisualizerBinding;

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
        final SurfaceViewThread swt1 = (SurfaceViewThread) binding.oscilloscopeSurfaceView;
        final SurfaceViewThread swt2 = (SurfaceViewThread) binding.spectrumSurfaceView;
        final SurfaceViewThread swt3 = (SurfaceViewThread) binding.beatSurfaceView;

        swt1.setMiddleman(middleman);
        swt2.setMiddleman(middleman);
        swt3.setMiddleman(middleman);

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}