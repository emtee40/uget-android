/*
 *
 *   Copyright (C) 2018-2019 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

package com.ugetdm.uget;

import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.widget.LinearLayout;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

public class AdManager {

    protected static final int nElements = 8;
    protected static final int nSeconds = 60;   // hide ads after N seconds

    public class Element {
        View         view;
        LinearLayout parent;
    }

    protected Element[]  elements;
    protected boolean disableAd = false;

    // ------------------------------------------------------------------------
    private Handler  adDisableHandler;
    private Runnable adDisableRunnable = new Runnable() {
        @Override
        public void run() {
            if (BuildConfig.HAVE_ADS) {
                disableAd = true;

                Element element;
                for (int index = 0;  index < nElements;  index++) {
                    element = elements[index];
                    if (element.view != null) {
                        element.parent.removeView(element.view);
                        element.view = null;
                    }
                }
            }
        }
    };

    // ------------------------------------------------------------------------
    public AdManager() {
        if (BuildConfig.HAVE_ADS) {
            elements = new Element[nElements];
            for (int index = 0;  index < nElements;  index++)
                elements[index] = new Element();
        }
    }

    public Element find(View key) {
        if (BuildConfig.HAVE_ADS) {
            Element element = null;

            for (int index = 0;  index < nElements;  index++) {
                element = elements[index];
                if (element.view == key)
                    return element;
            }
            return null;
        }
        else
            return null;
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    public View add(LinearLayout adParent, Context context, int viewWidthDp) {
        if (BuildConfig.HAVE_ADS)
            return add(adParent, null, context, viewWidthDp);
        else
            return null;
    }

    public View add(LinearLayout adParent, LinearLayout.LayoutParams lParams,
                      Context context, int viewWidthDp)
    {
        if (BuildConfig.HAVE_ADS == true) {
            if (disableAd)
                return null;

            AdSize adSize = AdSize.SMART_BANNER;
            // int     hMargin = (int) getResources().getDimension(R.dimen.activity_horizontal_margin);
            int     hMargin = (int) 0;
            // decide ad size
            if (viewWidthDp >= 728 + hMargin)
                adSize = AdSize.LEADERBOARD;  // 728x90
            else if (viewWidthDp >= 468 + hMargin)
                adSize = AdSize.FULL_BANNER;  // 468x60
            else if (viewWidthDp >= 320 + hMargin)
                adSize = AdSize.BANNER;       // 320x50
//            else if (viewWidthDp == 0)
//                adSize = AdSize.SMART_BANNER;

            AdView adView;
            adView = new AdView(context);
            adView.setAdUnitId("ca-app-pub-2883534618110931/1238672609");
            adView.setAdSize(adSize);
            // adView.setId(R.id.adView);

            adView.setAdListener(new AdListener() {
                @Override
                public void  onAdFailedToLoad(int errorCode) {
                    if (errorCode == AdRequest.ERROR_CODE_NETWORK_ERROR) {
                        if (adDisableHandler == null) {
                            disableAd = true;
                            adDisableHandler = new Handler();
                            adDisableHandler.postDelayed(adDisableRunnable, 0);
                        }
                    }
                }

                @Override
                public void onAdLoaded() {
                    if (adDisableHandler == null) {
                        adDisableHandler = new Handler();
                        adDisableHandler.postDelayed(adDisableRunnable, 1000 * nSeconds);
                    }
                }
            });

            if (lParams == null) {
                lParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        0);
            }
            adParent.addView(adView, lParams);

            AdRequest adRequest = new AdRequest.Builder()
                    .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                    .addTestDevice("TEST_DEVICE_ID")
                    .build();
            adView.loadAd(adRequest);  // SIGSEGV (signal SIGSEGV: invalid address (fault address: 0x0))

            Element element;
            element = find (null);
            if (element != null) {
                element.view = adView;
                element.parent = adParent;
            }

            return adView;
        }
        else
            return null;
    }

    public void remove(View adView) {
        if (BuildConfig.HAVE_ADS) {
            if (adView == null)
                return;

            Element element;
            element = find (adView);
            if (element == null)
                return;
            element.view = null;

            element.parent.removeView(adView);
        }
    }

    public void pause(View adView) {
        if (BuildConfig.HAVE_ADS) {
            if (adView == null)
                return;
            ((AdView)adView).pause();
        }
    }

    public void resume(View adView) {
        if (BuildConfig.HAVE_ADS) {
            if (adView == null)
                return;
            ((AdView)adView).resume();
        }
    }

    public void destroy(View adView) {
        if (BuildConfig.HAVE_ADS) {
            if (adView == null)
                return;

            Element element;
            element = find (adView);
            if (element != null)
                element.view = null;

            ((AdView)adView).destroy();
        }
    }

}
