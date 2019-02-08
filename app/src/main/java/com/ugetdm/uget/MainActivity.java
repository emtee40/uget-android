package com.ugetdm.uget;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

public class MainActivity extends AppCompatActivity {
    // data
    public static MainApp app = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- init MainApp start ---
        app = (MainApp)getApplicationContext();
        app.startRunning();
        // --- init MainApp end ---

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        // --- setup Toolbar after  setSupportActionBar()  and  toggle.syncState()  ---
        // getSupportActionBar().setDisplayShowTitleEnabled(false);
        // getSupportActionBar().setDisplayShowHomeEnabled(true);
        // getSupportActionBar().setHomeButtonEnabled(true);
        toolbar.setContentInsetStartWithNavigation(0);    // right side space of Navigation button
        toolbar.setNavigationIcon(R.mipmap.ic_launcher_round);
        // toolbar.setTitle(R.string.app_name);
        // toolbar.setLogo(R.mipmap.ic_launcher_round);

        initTraveler();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------------------
    // save & restore

    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onResume() {
        super.onResume();

    }
    /*
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the user's current game state

        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onRestoreInstanceState (Bundle savedInstanceState) {
        // Always call the superclass so it can restore the view hierarchy
        super.onRestoreInstanceState(savedInstanceState);

        // Restore state members from saved instance
        app.downloadAdapter.notifyDataSetChanged();
    }
    */

    // ------------------------------------------------------------------------
    // option menu (Toolbar / ActionBar)

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent();
            intent.setClass(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------------------------
    // Traveler

    RecyclerView downloadListView;
    RecyclerView categoryListView;
    RecyclerView stateListView;

    public void initTraveler() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        downloadListView = findViewById(R.id.download_listview);
        downloadListView.setLayoutManager(layoutManager);
        downloadListView.setAdapter(app.downloadAdapter);
        downloadListView.setHasFixedSize(true);
        // add divider for downloadListView
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(downloadListView.getContext(),
                layoutManager.getOrientation());
        downloadListView.addItemDecoration(dividerItemDecoration);

        categoryListView = findViewById(R.id.category_listview);
        categoryListView.setLayoutManager(new LinearLayoutManager(this));
        categoryListView.setAdapter(app.categoryAdapter);
        categoryListView.setHasFixedSize(true);

        stateListView = findViewById(R.id.state_listview);
        stateListView.setLayoutManager(new LinearLayoutManager(this));
        stateListView.setAdapter(app.stateAdapter);
        stateListView.setHasFixedSize(true);

        app.categoryAdapter.setOnItemClickListener(new CategoryAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                app.nthCategory = position;
                app.switchDownloadAdapter();
            }
        });

        app.stateAdapter.setOnItemClickListener(new StateAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                app.nthStatus = position;
                app.switchDownloadAdapter();
            }
        });
    }

}
