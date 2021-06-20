package com.example.ledjacket.ui;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
//import androidx.fragment.app.FragmentManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.ledjacket.Middleman;
import com.example.ledjacket.R;

/**
 * A [FragmentPagerAdapter] that returns a fragment corresponding to
 * one of the sections/tabs/pages.
 */

//Update: https://developer.android.com/training/animation/vp2-migration
public class SectionsPagerAdapter extends FragmentStateAdapter {

    private final Context mContext;

    private Middleman middleman;

    public SectionsPagerAdapter(Context context, FragmentActivity fa, Middleman middleman) {
        super(fa);
        mContext = context;
        this.middleman = middleman;
    }

    @Override
    public Fragment createFragment(int position) {
        // getItem is called to instantiate the fragment for the given page.
        // Return a PlaceholderFragment (defined as a static inner class below).
        //return PlaceholderFragment.newInstance(position + 1);
        switch (position) {
            case 0:
                return new AnimationsFragment();
            case 1:
                VisualizerFragment v = new VisualizerFragment();
                v.setMiddleman(middleman);
                return v;
            case 2:
            default: //SHOULD NEVER DEFAULT, this means getItemCount is higher than the number of pages
                return new SettingsFragment();
        }
    }

    @Nullable
    //@Override
    public CharSequence getPageTitle(int position) {
        //return "OBJECT " + (position + 1);
        return mContext.getResources().getStringArray(R.array.tab_titles)[position];
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}