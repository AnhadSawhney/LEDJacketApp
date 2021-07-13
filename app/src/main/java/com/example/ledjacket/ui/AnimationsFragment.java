package com.example.ledjacket.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.ledjacket.GridViewAdapter;
import com.example.ledjacket.ImageItem;
import com.example.ledjacket.R;
import com.example.ledjacket.databinding.FragmentAnimationsBinding;

import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;

import java.util.ArrayList;

/**
 * A placeholder fragment containing a simple view.
 */

//Gridview stuff courtesy of https://stacktips.com/tutorials/android/android-gridview-example-building-image-gallery-in-android

public class AnimationsFragment extends Fragment {

    private static final String ARG_SECTION_NUMBER = "section_number";

    //private PageViewModel pageViewModel;
    private FragmentAnimationsBinding binding;
    private GridView gridView;
    private GridViewAdapter gridAdapter;

    private Context context;

    public static AnimationsFragment newInstance(int index) {
        AnimationsFragment fragment = new AnimationsFragment();
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

        binding = FragmentAnimationsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        context = getActivity();

        /*final TextView textView = binding.sectionLabel;
        pageViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                //textView.setText(s);
            }
        });*/

        gridView = binding.gridView; //(GridView) root.findViewById(root.id.gridView);
        gridAdapter = new GridViewAdapter(context, R.layout.grid_item_layout, getData());
        gridView.setAdapter(gridAdapter);

        gridView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                ImageItem item = (ImageItem) parent.getItemAtPosition(position);

                //Create intent
                //Intent intent = new Intent(MainActivity.this, DetailsActivity.class);
                //intent.putExtra("title", item.getTitle());
                //intent.putExtra("image", item.getImage());

                //Start details activity
                //startActivity(intent);
            }
        });

        return root;
    }

    // TODO: make thumbnails, https://stackoverflow.com/questions/32517124/how-to-create-a-video-thumbnail-from-a-video-file-path-in-android

    // Prepare some dummy data for gridview
    private ArrayList<ImageItem> getData() {
        final ArrayList<ImageItem> imageItems = new ArrayList<>();
        //TypedArray imgs = getResources().obtainTypedArray(R.array.image_ids);
        for (int i = 0; i < 20 /*imgs.length()*/; i++) {
            //Bitmap bitmap = BitmapFactory.decodeResource(getResources(), imgs.getResourceId(i, -1));
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.thumb);
            imageItems.add(new ImageItem(bitmap, "Image#" + i));
        }
        return imageItems;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}