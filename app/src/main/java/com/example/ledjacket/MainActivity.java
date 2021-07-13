package com.example.ledjacket;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

//import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.example.ledjacket.audio.AudioThread;
import com.example.ledjacket.video.VideoThread;
import com.google.android.material.tabs.TabLayout;

//import androidx.viewpager.widget.ViewPager;
//import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.ledjacket.ui.SectionsPagerAdapter;
import com.example.ledjacket.databinding.ActivityMainBinding;
import com.google.android.material.tabs.TabLayoutMediator;

//https://developer.android.com/training/animation/screen-slide-2

public class MainActivity extends FragmentActivity {

    private AudioThread audioThread;
    private VideoThread videoThread;

    private ActivityMainBinding binding;

    public Middleman middleman = new Middleman(); // THIS MUST BE PASSED TO VISUALIZERFRAGMENT

    /**
     * The pager widget, which handles animation and allows swiping horizontally to access previous
     * and next wizard steps.
     */
    private ViewPager2 viewPager;

    boolean canRecord = false;

    boolean canRead = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SectionsPagerAdapter sectionsPagerAdapter = new SectionsPagerAdapter(this, this, middleman); //getSupportFragmentManager()
        viewPager = binding.viewPager;
        viewPager.setAdapter(sectionsPagerAdapter);

        // Start on visualizer tab
        viewPager.setCurrentItem(1);

        TabLayout tabs = binding.tabs;
        new TabLayoutMediator(tabs, viewPager,
                (tab, position) -> tab.setText(getResources().getStringArray(R.array.tab_titles)[position])
        ).attach();

        // CRASH IF THE USER DOES NOT GRANT AUDIO RECORD PERMISSION?

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.MODIFY_AUDIO_SETTINGS) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS}, 1234);
            canRecord = false;
        } else {
            canRecord = true;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 5678);
            canRead = false;
        } else {
            canRead = true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1234:
                canRecord = checkPermissionResult(permissions, grantResults);
                return;
            case 5678:
                canRead = checkPermissionResult(permissions, grantResults);
                return;
        }
    }

    private boolean checkPermissionResult(String permissions[], int[] grantResults) {
        // If request is cancelled, the result arrays are empty.
        boolean success = true;
        for(int result : grantResults) {
            success &= (result == PackageManager.PERMISSION_GRANTED);
        }

        if (!success) {
            String msg = "Permissions denied by user:";
            for (String per : permissions) {
                msg += "\n" + per;
            }
            Log.d("MainActivity", msg);
        }
        return success;
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

    @Override
    protected void onPause() {
        super.onPause();

        // UNCOMMENT THIS TO STOP THREADS RUNNING IN BACKGROUND

        /*if(audioThread != null) {
            audioThread.stop();
        }*/

        /*if(videoThread != null) {
            videoThread.stop();
        }*/
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(canRecord) {
            if(audioThread == null) {
                audioThread = new AudioThread(middleman);
            } else {
                audioThread.start();
            }
        }

        if(canRead) {
            if (videoThread == null) {
                videoThread = new VideoThread(this);
                middleman.setVideoThread(videoThread); // In order to send images to visualizerFragment
            } else {
                videoThread.start();
            }
        }
    }
}