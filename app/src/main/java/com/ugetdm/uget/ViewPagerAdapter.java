package com.ugetdm.uget;

import android.support.v4.view.PagerAdapter;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

// This class can NOT use with RecyclerView.
// RecyclerView can't display in PagerAdapter.

public class ViewPagerAdapter extends PagerAdapter {
    protected List<View>   views;
    protected List<String> titles;

    ViewPagerAdapter() {
        views = new ArrayList<>();
        titles = new ArrayList<>();
    }

    public void add(View view, String title) {
        views.add(view);
        titles.add(title);
    }

    // ------------------------------------------------
    // Override

    @Override
    public int getCount() {
        return views.size();
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == object;
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        View view = views.get(position);
        container.addView(view);
        return view;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView(views.get(position));
        // container.removeView((View)object);
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return titles.get(position);
    }
}

