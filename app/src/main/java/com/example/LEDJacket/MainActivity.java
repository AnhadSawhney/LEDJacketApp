package com.example.ledjacket;

import android.os.Bundle;
import android.widget.GridView;

//import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;

//import androidx.viewpager.widget.ViewPager;
//import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.ledjacket.ui.main.SectionsPagerAdapter;
import com.example.ledjacket.AudioThread;
import com.example.ledjacket.databinding.ActivityMainBinding;
import com.google.android.material.tabs.TabLayoutMediator;

//https://developer.android.com/training/animation/screen-slide-2

public class MainActivity extends FragmentActivity { //AppCompatActivity {

    private AudioThread audioThread;
    private ActivityMainBinding binding;

    /**
     * The pager widget, which handles animation and allows swiping horizontally to access previous
     * and next wizard steps.
     */
    private ViewPager2 viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SectionsPagerAdapter sectionsPagerAdapter = new SectionsPagerAdapter(this, this); //getSupportFragmentManager()
        viewPager = binding.viewPager;
        viewPager.setAdapter(sectionsPagerAdapter);

        TabLayout tabs = binding.tabs;
        new TabLayoutMediator(tabs, viewPager,
                (tab, position) -> tab.setText(getResources().getStringArray(R.array.tab_titles)[position])
        ).attach();

        /*FloatingActionButton fab = binding.fab;

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });*/
    }

    @Override
    public void onBackPressed() {
        if (viewPager.getCurrentItem() == 0) {
            // If the user is currently looking at the first step, allow the system to handle the
            // Back button. This calls finish() on this activity and pops the back stack.
            super.onBackPressed();
        } else {
            // Otherwise, select the previous step.
            viewPager.setCurrentItem(viewPager.getCurrentItem() - 1);
        }
    }

    /*@Override
    protected void onPause() {
        super.onPause();
        audioThread.stop_recording();
        try {
            audioThread.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        audioThread = new AudioThread();
        audioThread.start();
    }*/
}