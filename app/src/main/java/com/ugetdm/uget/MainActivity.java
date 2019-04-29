package com.ugetdm.uget;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.provider.DocumentFile;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.ugetdm.uget.lib.Core;
import com.ugetdm.uget.lib.Info;
import com.ugetdm.uget.lib.Node;
import com.ugetdm.uget.lib.Util;

import java.io.File;
import java.util.regex.PatternSyntaxException;

import ar.com.daidalos.afiledialog.FileChooserDialog;

public class MainActivity extends AppCompatActivity {
    // MainApp data
    public MainApp      app;
    // View
    public Toolbar      toolbar;
    public DrawerLayout drawer;
    public PopupMenu    downloadPopupMenu = null;   // decideSelectionMode()
    // RecyclerView
    LinearLayoutManager downloadLayoutManager;

    public ProgressJob  progressJob;
    public boolean      deviceRotated;

    // ------------------------------------------------------------------------
    // entire lifetime: ORIENTATION

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- init MainApp ---
        //app = (MainApp) getApplicationContext();    // throw RuntimeException
        app = (MainApp) getApplication();
        app.onCreateRunning();

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                Bundle bundle = new Bundle();
                bundle.putInt("mode", NodeActivity.Mode.download_creation);
                bundle.putInt("nthCategory", app.nthCategory);
                bundle.putLong("nodePointer", app.getNthCategory(app.nthCategory));
                intent.putExtras(bundle);
                intent.setClass(MainActivity.this, NodeActivity.class);
                startActivityForResult(intent, REQUEST_ADD_DOWNLOAD);
            }
        });

        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
/*
        // --- set listener in decideToolbarStatus()
        if (drawer != null) {
            ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                    this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
            drawer.addDrawerListener(toggle);
            toggle.syncState();
        }
*/
        decideToolbarStatus();
        initTraveler();

        Runnable readyRunnable = new Runnable() {
            @Override
            public void run() {
                downloadListView.setAdapter(app.downloadAdapter);
                categoryListView.setAdapter(app.categoryAdapter);
                stateListView.setAdapter(app.stateAdapter);
                decideContent();
                initHandler();
                // --- handle URI from "Share Link" ---
                processUriFromIntent();
                // --- MainActivity is ready ---
                app.mainActivity = MainActivity.this;
            }
        };

        app.logAppend("MainActivity.onCreate() - Job.queuedTotal = " + Job.queuedTotal);
        progressJob = new ProgressJob(handler);
        if (Job.queued[Job.LOAD_ALL] > 0)
            progressJob.waitForReady(R.string.message_loading, readyRunnable);
        else {
            readyRunnable.run();
            if (Job.queuedTotal > 0)
                progressJob.waitForReady(R.string.message_loading, null);
        }
    }

    @Override
    protected void onDestroy() {
        app.mainActivity = null;
        // --- notification ---
        app.cancelNotification();
        // --- dialog ---
        if (downloadPopupMenu != null)
            downloadPopupMenu.dismiss();
        progressJob.destroy();
        // --- ad ---
        if (BuildConfig.HAVE_ADS) {
            if (adView != null)
                ((AdView)adView).destroy();
            adView = null;
        }

        super.onDestroy();
    }

    // ------------------------------------------------------------------------
    // visible lifetime

    // onStart() Called when the activity is becoming visible to the user.
    @Override
    protected void onStart() {
        super.onStart();

        checkPermission();
    }

    @Override
    protected void onStop() {
        super.onStop();

        // --- save all data & offline status ---
        if (Job.queued[Job.SAVE_ALL] == 0 && deviceRotated == false)
            Job.saveAll();
        app.saveFolderHistory();
        app.saveStatus();
        deviceRotated = false;
    }

    // ------------------------------------------------------------------------
    // Activity Lifecycle when you rotate screen
    // onPause -> onSaveInstanceState -> onStop -> onDestroy
    // onCreate -> onStart -> onRestoreInstanceState -> onResume

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the current state
        if (app.downloadAdapter.singleSelection) {
            int position = app.downloadAdapter.getCheckedItemPosition();
            if (isDownloadPositionVisible(position))
                savedInstanceState.putBoolean("isCheckedVisible", true);
        }

        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onRestoreInstanceState (Bundle savedInstanceState) {
        // Always call the superclass so it can restore the view hierarchy
        super.onRestoreInstanceState(savedInstanceState);

        // Restore state members from saved instance
        boolean isCheckedVisible = savedInstanceState.getBoolean("isCheckedVisible", false);
        if (app.downloadAdapter.singleSelection && isCheckedVisible) {
            int position = app.downloadAdapter.getCheckedItemPosition();
            if (position >= 0)
                scrollToDownloadPosition(position);
        }
    }

    // ------------------------------------------------------------------------
    // foreground lifetime
    // e.g. show/hide dialog above this activity

    @Override
    protected void onResume() {
        super.onResume();

        // --- show message if no download ---
        decideContent();
        // --- test clipboard type pattern ---
        try {
            String string = new String("test string");
            string.matches(app.setting.clipboard.types);
        }
        catch (PatternSyntaxException e) {
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
            dialogBuilder.setTitle(getString(R.string.pref_clipboard_type_error_title));
            dialogBuilder.setMessage(getString(R.string.pref_clipboard_type_error_message));

            DialogInterface.OnClickListener OkClick = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {}
            };
            dialogBuilder.setNeutralButton(getResources().getString(android.R.string.ok), OkClick);
            dialogBuilder.show();
        }
        // --- ad ---
        if (BuildConfig.HAVE_ADS) {
            if (adView != null)
                ((AdView)adView).resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // --- ad ---
        if (BuildConfig.HAVE_ADS) {
            if (adView != null)
                ((AdView)adView).pause();
        }
    }

    // --------------------------------
    // <uses-permission android:name="android.permission.CHANGE_CONFIGURATION" />
    // <Activity android:configChanges="orientation|screenSize|keyboard">
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            newConfig.orientation == Configuration.ORIENTATION_PORTRAIT)
        {
            deviceRotated = true;
            recreate();
        }
    }

    // ------------------------------------------------------------------------
    // other

    public void processUriFromIntent() {
        Intent intent = getIntent();
        String action = intent.getAction();
        // if this is from the share menu
        if (Intent.ACTION_SEND.equals(action) && intent.hasExtra(Intent.EXTRA_TEXT)) {
            String uri = intent.getStringExtra(Intent.EXTRA_TEXT);
            // clear processed intent
            intent.removeExtra(Intent.EXTRA_TEXT);
            intent.setAction("");
            intent.replaceExtras(new Bundle());    // remove it completely

            if (uri != null) {
                if (app.setting.ui.skipExistingUri && app.core.isUriExist(uri.toString())) {
                    Snackbar.make(findViewById(R.id.fab),
                            getString(R.string.pref_ui_skip_existing_uri),Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                    return;
                }
                // match
                long cnode = app.core.matchCategory(uri.toString(), null);
                if (cnode == 0)
                    cnode = Node.getNthChild(app.core.nodeReal, 0);
                if (cnode == 0)
                    return;

                long checkedNodes[] = app.downloadAdapter.getCheckedNodes();
                app.core.addDownloadByUri(uri.toString(), cnode, true);
                app.downloadAdapter.setCheckedNodes(checkedNodes);
                // --- notify ---
                app.stateAdapter.notifyDataSetChanged();
                app.categoryAdapter.notifyDataSetChanged();
                app.downloadAdapter.notifyDataSetChanged();
                // --- start timer handler ---
                app.timerHandler.startQueuing();

                // moveTaskToBack(true);
            }
        }
    }

    // if  android:launchMode="singleTask"  , function onCreate() will not call when activity instance still exist.
    // onNewIntent -> onRestart -> onStart ->onResume
    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // store the new intent unless getIntent() will return the old one
        setIntent(intent);
        processUriFromIntent();
    }

    @Override
    public void onBackPressed() {
        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        }
        else if (app.downloadAdapter.getCheckedItemCount() > 0 || isToolbarHomeAsUp()) {
            // --- selection mode ---
            app.downloadAdapter.clearChoices(true);
            decideSelectionMode();
        }
        else if (app.setting.ui.exitOnBack) {
            if (app.setting.ui.confirmExit) {
                confirmExit();
                return;
            }
        }
        else {
            // --- do nothing ---
            moveTaskToBack(true);
            // super.onBackPressed();
        }
    }

    // ------------------------------------------------------------------------
    // option menu (Toolbar / ActionBar)

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);

        // --- selection mode --- menu in single and multiple mode are difference.
        decideMenuVisible();
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item;
        // --- offline ---
        item = menu.findItem(R.id.action_offline);
        item.setChecked(app.setting.offlineMode);
        if (app.setting.ui.noWifiGoOffline)
            item.setEnabled(false);
        else
            item.setEnabled(true);
        // --- category menu ---
        item = menu.findItem((R.id.action_category_delete));
        item.setEnabled(app.nthCategory > 0);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (downloadPopupMenu != null)
            downloadPopupMenu.dismiss();

        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        Intent intent;
        Bundle bundle;
        long   selection[];

        switch(id) {
            case R.id.action_category_new:
                intent = new Intent();
                bundle = new Bundle();
                bundle.putInt("mode", NodeActivity.Mode.category_creation);
                bundle.putLong("nodePointer", app.getNthCategory(app.nthCategory));
                intent.putExtras(bundle);
                intent.setClass(MainActivity.this, NodeActivity.class);
                startActivity(intent);
                break;

            case R.id.action_category_import:
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    startFileChooser();
                else {
                    FileChooserDialog fcDialog = new FileChooserDialog(MainActivity.this);
                    fcDialog.addListener(new FileChooserDialog.OnFileSelectedListener() {
                        public void onFileSelected(Dialog source, File file) {
                            source.hide();
                            // --- load category on thread ---
                            Job.load(file.getAbsolutePath());
                            progressJob.filename = file.getName();
                            progressJob.waitForReady(R.string.message_loading, new Runnable() {
                                @Override
                                public void run() {
                                    boolean isOK = Job.result[Job.LOAD] == 0;
                                    handleFileChooserResult(progressJob.filename, isOK);
                                }
                            });
                        }
                        // this is called when a file is created
                        public void onFileSelected(Dialog source, File folder, String name) {
                            source.hide();
                        }
                    });
                    fcDialog.setFilter(".*json|.*JSON");
                    fcDialog.show();
                }
                break;

            case R.id.action_category_edit:
                intent = new Intent();
                bundle = new Bundle();
                bundle.putInt("mode", NodeActivity.Mode.category_setting);
                bundle.putLong("nodePointer", app.getNthCategory(app.nthCategory));
                intent.putExtras(bundle);
                intent.setClass(MainActivity.this, NodeActivity.class);
                startActivity(intent);
                break;

            case R.id.action_category_export:
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    startFileCreator();
                else {
                    FileChooserDialog fcDialog = new FileChooserDialog(MainActivity.this);
                    fcDialog.addListener(new FileChooserDialog.OnFileSelectedListener() {
                        public void onFileSelected(Dialog source, File file) {
                            source.hide();
                            // --- save category on thread ---
                            Job.save(app.getNthCategory(app.nthCategory), file.getAbsolutePath());
                            progressJob.filename = file.getName();
                            progressJob.waitForReady(R.string.message_saving, new Runnable() {
                                @Override
                                public void run() {
                                    boolean isOK = Job.result[Job.SAVE] == 0;
                                    showFileCreatorResult(progressJob.filename, isOK);
                                }
                            });
                        }
                        // this is called when a file is created
                        public void onFileSelected(Dialog source, File folder, String name) {
                            source.hide();
                            if (name.endsWith(".json") == false && name.endsWith(".JSON") == false)
                                name = name + ".json";
                            // --- save category on thread ---
                            Job.save(app.getNthCategory(app.nthCategory), folder.getAbsolutePath() + '/' + name);
                            progressJob.filename = name;
                            progressJob.waitForReady(R.string.message_saving, new Runnable() {
                                @Override
                                public void run() {
                                    boolean isOK = Job.result[Job.SAVE] == 0;
                                    showFileCreatorResult(progressJob.filename, isOK);
                                }
                            });
                        }
                    });
                    fcDialog.setCanCreateFiles(true);
                    fcDialog.setFilter(".*json|.*JSON");
                    fcDialog.show();
                }
                break;

            case R.id.action_save_all:
                app.saveFolderHistory();
                app.saveStatus();
                Job.saveAll();
                progressJob.waitForReady(R.string.message_saving, null);
                break;

            case R.id.action_category_delete:
                confirmDeleteCategory();
                break;

            case R.id.action_batch_sequence:
                intent = new Intent();
                bundle = new Bundle();
                bundle.putInt("mode", NodeActivity.Mode.batch_sequence);
                bundle.putInt("nthCategory", app.nthCategory);
                bundle.putLong("nodePointer", app.getNthCategory(app.nthCategory));
                intent.putExtras(bundle);
                intent.setClass(MainActivity.this, NodeActivity.class);
                startActivityForResult(intent, REQUEST_ADD_DOWNLOAD);
                break;

            case R.id.action_resume_all:
                selection = app.downloadAdapter.getCheckedNodes();
                app.core.resumeCategories();
                app.downloadAdapter.setCheckedNodes(selection);
                app.stateAdapter.notifyDataSetChanged();
                // --- selection mode ---
                decideSelectionMode();
                // --- start timer handler ---
                app.timerHandler.startQueuing();
                break;

            case R.id.action_pause_all:
                selection = app.downloadAdapter.getCheckedNodes();
                app.core.pauseCategories();
                app.downloadAdapter.setCheckedNodes(selection);
                app.stateAdapter.notifyDataSetChanged();
                app.userAction = true;
                // --- selection mode ---
                decideSelectionMode();
                // --- start timer handler ---
                app.timerHandler.startQueuing();
                break;

            case R.id.action_offline:
                if (app.setting.offlineMode)
                    app.setting.offlineMode = false;
                else
                    app.setting.offlineMode = true;
                decideTitle();
                // --- start timer handler ---
                app.timerHandler.startQueuing();
                break;

            case R.id.action_settings:
                intent = new Intent();
                intent.setClass(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
                return true;

            case R.id.action_exit:
                if (app.setting.ui.confirmExit)
                    confirmExit();
                else
                    exit();
                break;

            case R.id.action_start:
                selection = app.downloadAdapter.getCheckedNodes();
                if (selection != null) {
                    for (int i=0;  i < selection.length;  i++) {
                        if (selection[i] == 0)
                            continue;
                        long infoPointer = Node.info(selection[i]);
                        if ((Info.getGroup(infoPointer) & Info.Group.active) > 0)
                            continue;
                        app.core.queueDownload(selection[i]);
                    }
                    app.downloadAdapter.setCheckedNodes(selection);
                    app.stateAdapter.notifyDataSetChanged();
                    // app.userAction = true;
                    // --- selection mode ---
                    decideSelectionMode();
                    // --- start timer handler ---
                    app.timerHandler.startQueuing();
                }
                break;

            case R.id.action_pause:
                selection = app.downloadAdapter.getCheckedNodes();
                if (selection != null) {
                    for (int i=0;  i < selection.length;  i++) {
                        if (selection[i] == 0)
                            continue;
                        app.core.pauseDownload(selection[i]);
                    }
                    app.downloadAdapter.setCheckedNodes(selection);
                    app.stateAdapter.notifyDataSetChanged();
                    app.userAction = true;
                    // --- selection mode ---
                    decideSelectionMode();
                    // --- start timer handler ---
                    app.timerHandler.startQueuing();
                }
                break;

            case R.id.action_select_all:
                int  size = app.downloadAdapter.getItemCount();
                for (int i = 0;  i < size;  i++)
                    app.downloadAdapter.setItemChecked(i, true);
                // --- selection mode ---
                decideSelectionMode();
                break;

            case R.id.action_delete_recycle:
                selection = app.downloadAdapter.getCheckedNodes();
                if (selection != null) {
                    for (int i=0;  i < selection.length;  i++) {
                        if (selection[i] == 0)
                            continue;
                        // app.core.recycleDownload() return false if it removed.
                        if (app.core.recycleDownload(selection[i]) == false)
                            selection[i] = 0;
                    }
                    app.downloadAdapter.setCheckedNodes(selection);
                    app.categoryAdapter.notifyDataSetChanged();
                    app.stateAdapter.notifyDataSetChanged();
                    app.userAction = true;
                    // --- selection mode ---
                    decideSelectionMode();
                    // --- start timer handler ---
                    app.timerHandler.startQueuing();
                }
                break;

            case R.id.action_delete_data:
                selection = app.downloadAdapter.getCheckedNodes();
                if (selection != null) {
                    for (int i=0;  i < selection.length;  i++) {
                        if (selection[i] == 0)
                            continue;
                        app.core.deleteDownload(selection[i], false);
                    }
                    app.downloadAdapter.clearChoices(false);
                    app.downloadAdapter.notifyDataSetChanged();
                    app.categoryAdapter.notifyDataSetChanged();
                    app.stateAdapter.notifyDataSetChanged();
                    app.userAction = true;
                    // --- selection mode ---
                    decideSelectionMode();
                    // --- start timer handler ---
                    app.timerHandler.startQueuing();
                }
                break;

            case R.id.action_delete_file:
                if (app.setting.ui.confirmDelete)
                    confirmDeleteDownloadFile();
                else
                    deleteSelectedDownloadFile();
                // --- start timer handler ---
                app.timerHandler.startQueuing();
                break;

            default:
                break;
        }

        // --- show message if no download ---
        decideContent();

        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------------------------
    // Toolbar (and it's title)

    public boolean isToolbarHomeAsUp() {
        return (getSupportActionBar().getDisplayOptions() & ActionBar.DISPLAY_HOME_AS_UP) != 0;
    }

    public void decideToolbarStatus() {
        int nDownloadSelected = app.downloadAdapter.getCheckedItemCount();
        // --- setup Toolbar after  setSupportActionBar()  and  toggle.syncState()  ---
        if (nDownloadSelected == 0 || app.downloadAdapter.singleSelection) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            toolbar.setNavigationIcon(R.mipmap.ic_notification);
            // --- left side to title space (if NavigationIcon exists)
            toolbar.setContentInsetStartWithNavigation(0);
            // reset Listener when icon changed
            if (drawer == null)
                getSupportActionBar().setHomeButtonEnabled(false);
            else {
                toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (downloadPopupMenu != null)
                            downloadPopupMenu.dismiss();
                        if (drawer.isDrawerOpen(GravityCompat.START))
                            drawer.closeDrawer(GravityCompat.START);
                        else
                            drawer.openDrawer(GravityCompat.START);
                    }
                });
            }
        }
        else {
            // --- selection mode ---
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            // --- left side to title space (if NavigationIcon exists)
            toolbar.setContentInsetStartWithNavigation(72);    // default is 72dp
            // reset Listener when icon changed
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    app.downloadAdapter.clearChoices(true);
                    decideSelectionMode();
                }
            });
        }
        decideTitle();

        // getSupportActionBar().setDisplayShowTitleEnabled(false);
        // getSupportActionBar().setDisplayShowHomeEnabled(true);
        // getSupportActionBar().setHomeButtonEnabled(true);
        // toolbar.setTitle(R.string.app_name);
        // toolbar.setLogo(R.mipmap.ic_launcher_round);
    }

    public void decideTitle() {
        String  title;
        int nDownloadSelected = app.downloadAdapter.getCheckedItemCount();

        if (nDownloadSelected == 0 || app.downloadAdapter.singleSelection) {
            title = getString(R.string.app_name);
            if (app.setting.offlineMode)
                title += " " + getString(R.string.action_offline);
        }
        else {
            // --- selection mode ---
            title = Integer.toString(nDownloadSelected);
            //toolbar.setSubtitle(null);
        }
        // toolbar.setTitle(title);    // may not work
        getSupportActionBar().setTitle(title);
    }

    public void decideSelectionMode() {
        // --- selection mode ---
        if (app.downloadAdapter.singleSelection && app.downloadAdapter.getCheckedItemCount() == 0) {
            app.downloadAdapter.singleSelection = false;
            if (downloadPopupMenu != null)
                downloadPopupMenu.dismiss();
        }

        decideMenuVisible();
        decideToolbarStatus();
    }

    public void decideMenuVisible() {
        boolean selectionMode;
        if (app.downloadAdapter.singleSelection)
            selectionMode = false;
        else
            selectionMode = app.downloadAdapter.getCheckedItemCount() != 0;

        Menu menu = toolbar.getMenu();
        if (menu == null)
            return;
        if (menu.findItem(R.id.action_file) == null)
            return;
        if (menu.findItem(R.id.action_start) == null)
            return;

        menu.findItem(R.id.action_file).setVisible(selectionMode == false);
        menu.findItem(R.id.action_batch).setVisible(selectionMode == false);
        menu.findItem(R.id.action_resume_all).setVisible(selectionMode == false);
        menu.findItem(R.id.action_pause_all).setVisible(selectionMode == false);
        menu.findItem(R.id.action_offline).setVisible(selectionMode == false);
        menu.findItem(R.id.action_settings).setVisible(selectionMode == false);
        menu.findItem(R.id.action_exit).setVisible(selectionMode == false);

        menu.findItem(R.id.action_start).setVisible(selectionMode);
        menu.findItem(R.id.action_pause).setVisible(selectionMode);
        menu.findItem(R.id.action_select_all).setVisible(selectionMode);
        menu.findItem(R.id.action_delete).setVisible(selectionMode);
    }

    public void decideContent() {
        if (app.downloadAdapter.getItemCount() > 0) {
            downloadListView.setVisibility(View.VISIBLE);
            findViewById(R.id.message_no_download).setVisibility(View.GONE);
        }
        else {
            downloadListView.setVisibility(View.GONE);
            findViewById(R.id.message_no_download).setVisibility(View.VISIBLE);
        }
    }

    // ------------------------------------------------------------------------
    // Traveler

    RecyclerView downloadListView;
    RecyclerView categoryListView;
    RecyclerView stateListView;

    public void initTraveler() {
        downloadLayoutManager = new NeLinearLayoutManager(this);
        downloadListView = findViewById(R.id.download_listview);
        downloadListView.setLayoutManager(downloadLayoutManager);
        downloadListView.setHasFixedSize(true);
        // avoid that RecyclerView's views are blinking when notifyDataSetChanged()
        downloadListView.getItemAnimator().setChangeDuration(0);
        // add divider for downloadListView
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(downloadListView.getContext(),
                downloadLayoutManager.getOrientation());
        downloadListView.addItemDecoration(dividerItemDecoration);

        categoryListView = findViewById(R.id.category_listview);
        categoryListView.setLayoutManager(new NeLinearLayoutManager(this));
        categoryListView.setHasFixedSize(true);
        // avoid that RecyclerView's views are blinking when notifyDataSetChanged()
        categoryListView.getItemAnimator().setChangeDuration(0);

        stateListView = findViewById(R.id.state_listview);
        stateListView.setLayoutManager(new NeLinearLayoutManager(this));
        stateListView.setHasFixedSize(true);
        // avoid that RecyclerView's views are blinking when notifyDataSetChanged()
        stateListView.getItemAnimator().setChangeDuration(0);

        DownloadItemListener downloadItemListener = new DownloadItemListener();
        app.downloadAdapter.setOnItemClickListener(downloadItemListener);
        app.downloadAdapter.setOnItemLongClickListener(downloadItemListener);

        CategoryItemListener categoryItemListener = new CategoryItemListener();
        app.categoryAdapter.setOnItemClickListener(categoryItemListener);
        app.categoryAdapter.setOnItemLongClickListener(categoryItemListener);

        app.stateAdapter.setOnItemClickListener(new StateAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                app.nthStatus = position;
                // --- avoid IndexOutOfBoundsException --- call getRecycledViewPool().clear()
                downloadListView.getRecycledViewPool().clear();
                // --- This will call app.downloadAdapter.notifyDataSetChanged()
                app.switchDownloadAdapter();
                // --- selection mode ---
                decideSelectionMode();
                // --- show message if no download ---
                decideContent();
            }
        });

        ImageView imageView;
        imageView = findViewById(R.id.category_move_up);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                app.moveNthCategory(app.nthCategory, app.nthCategory -1);
                app.nthCategory--;
                app.categoryAdapter.setItemChecked(app.nthCategory, true);
                // --- category button up/down ---
                decideCategoryButtonEnable();
            }
        });
        imageView = findViewById(R.id.category_move_down);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                app.moveNthCategory(app.nthCategory, app.nthCategory +1);
                app.nthCategory++;
                app.categoryAdapter.setItemChecked(app.nthCategory, true);
                // --- category button up/down ---
                decideCategoryButtonEnable();
            }
        });

        // --- category button up/down ---
        decideCategoryButtonEnable();
    }

    public class DownloadItemListener implements DownloadAdapter.OnItemClickListener,
                                                 DownloadAdapter.OnItemLongClickListener
    {
        @Override
        public void onItemClick(View view, int position) {
            int  nDownloadSelected = app.downloadAdapter.getCheckedItemCount();

            if (app.downloadAdapter.singleSelection) {
                if (showDownloadPopupMenu(null, position) == false)
                    app.downloadAdapter.setItemChecked(position, false);
            }
            else {
                // from 1 to 0.
                if (nDownloadSelected == 0)
                    decideMenuVisible();
                decideToolbarStatus();
            }
        }

        @Override
        public boolean onItemLongClick(View view, int position) {
            int nDownloadSelected = app.downloadAdapter.getCheckedItemCount();
            // --- selection mode ---
            // from 0 to 1  or  1 to 0.
            if (nDownloadSelected <= 1)
                decideMenuVisible();
            decideToolbarStatus();
            return true;
        }
    }

    public class CategoryItemListener implements CategoryAdapter.OnItemClickListener,
                                                 CategoryAdapter.OnItemLongClickListener
    {
        @Override
        public void onItemClick(View view, int position) {
            app.nthCategory = position;
            // --- avoid IndexOutOfBoundsException --- call getRecycledViewPool().clear()
            downloadListView.getRecycledViewPool().clear();
            // --- This will call app.downloadAdapter.notifyDataSetChanged()
            app.switchDownloadAdapter();
            // --- selection mode ---
            decideSelectionMode();
            // --- category menu ---
            invalidateOptionsMenu();    // this will call onPrepareOptionsMenu()
            // --- category button up/down ---
            decideCategoryButtonEnable();
            // --- show message if no download ---
            decideContent();
        }

        @Override
        public boolean onItemLongClick(View view, int position) {
            onItemClick(view, position);
            app.categoryAdapter.setItemChecked(position, true);

            PopupMenu popupMenu = new PopupMenu(MainActivity.this, findViewById(R.id.traveler_top));
            popupMenu.inflate(R.menu.main_category);
            //popupMenu.getMenu().findItem(R.id.action_category_delete).setEnabled(app.nthCategory > 0);
            popupMenu.getMenu().findItem(R.id.action_category_delete).setVisible(app.nthCategory > 0);
            popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    onOptionsItemSelected(item);
                    return true;
                }
            });
            popupMenu.show();
            return true;
        }
    }

    public void scrollToDownloadPosition(int position) {
        if (isDownloadPositionVisible(position) == false)
            downloadListView.smoothScrollToPosition(position);
    }

    public boolean isDownloadPositionVisible(int position) {
        int first, last;
        first = downloadLayoutManager.findFirstCompletelyVisibleItemPosition();
        last = downloadLayoutManager.findLastCompletelyVisibleItemPosition();
        return (position >= first && position <= last);
    }

    // --- category button up/down ---
    public void decideCategoryButtonEnable() {
        ImageView imageView;
        imageView = findViewById(R.id.category_move_down);
        if (app.nthCategory == 0 || app.nthCategory == Node.nChildren(app.core.nodeReal)) {
            imageView.setEnabled(false);
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                imageView.setImageAlpha(64);    // 0 - 255
            else
                imageView.setAlpha(64);
        }
        else {
            imageView.setEnabled(true);
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                imageView.setImageAlpha(255);    // 0 - 255
            else
                imageView.setAlpha(255);
        }
        imageView = findViewById(R.id.category_move_up);
        if (app.nthCategory < 2) {
            imageView.setEnabled(false);
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                imageView.setImageAlpha(64);    // 0 - 255
            else
                imageView.setAlpha(64);
        }
        else {
            imageView.setEnabled(true);
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                imageView.setImageAlpha(255);    // 0 - 255
            else
                imageView.setAlpha(255);
        }
    }

    // ------------------------------------------------------------------------
    // Download Popup Menu

    private boolean showDownloadPopupMenu(View view, int nthDownload) {
        if (view == null)
            view = findViewById(R.id.action_batch);
        if (view == null)
            view = findViewById(R.id.action_file);
        if (view == null)
            return false;

        if (downloadPopupMenu != null)
            downloadPopupMenu.dismiss();
        downloadPopupMenu = new PopupMenu(this, view);
        downloadPopupMenu.inflate(R.menu.main_popup);

        Menu menu = downloadPopupMenu.getMenu();
        // Any Category/Status can't move download position if they were sorted.
        if (app.setting.sortBy > 0)
            menu.findItem(R.id.menu_download_move).setEnabled(false);
        else {
            if (nthDownload == 0)
                menu.findItem(R.id.menu_download_move_up).setEnabled(false);
            if (nthDownload == Node.nChildren(app.downloadAdapter.pointer) -1)
                menu.findItem(R.id.menu_download_move_down).setEnabled(false);
        }
        // priority
        int  priority = app.getNthDownloadPriority(nthDownload);
        switch (priority) {
            case Core.Priority.high:
                menu.findItem(R.id.menu_download_priority_high).setChecked(true);
                break;

            case Core.Priority.normal:
                menu.findItem(R.id.menu_download_priority_normal).setChecked(true);
                break;

            case Core.Priority.low:
                menu.findItem(R.id.menu_download_priority_low).setChecked(true);
                break;
        }

        // if file doesn't exist, disable menu item: "open" and "delete file".
        if (app.getDownloadedFile(nthDownload) == null) {
            menu.findItem(R.id.menu_download_open).setEnabled(false);
            menu.findItem(R.id.menu_download_delete_file).setEnabled(false);
        }

        if (app.nthStatus == 1) {
            menu.findItem(R.id.menu_download_force_start).setEnabled(false);
            menu.findItem(R.id.menu_download_start).setEnabled(false);
            menu.findItem(R.id.menu_download_properties).setEnabled(false);
        }
        if (app.nthStatus > 2)
            menu.findItem(R.id.menu_download_pause).setEnabled(false);

        downloadPopupMenuListener listener = new downloadPopupMenuListener();
        downloadPopupMenu.setOnDismissListener(listener);
        downloadPopupMenu.setOnMenuItemClickListener(listener);
        downloadPopupMenu.show();
        return true;
    }

    public class downloadPopupMenuListener implements PopupMenu.OnDismissListener,
                                                      PopupMenu.OnMenuItemClickListener
    {
        // --- for Android < 7.0 (API 24) --- click menu item will call onDismiss() even if this item has submenu.
        boolean submenuClicked = false;

        @Override
        public void onDismiss(PopupMenu popupMenu) {
            // --- for Android < 7.0 (API 24) --- click menu item will call onDismiss() even if this item has submenu.
            if (submenuClicked) {
                submenuClicked = false;
                return;
            }
            // --- show message if no download ---
            decideContent();
            // --- popup menu closed ---
            downloadPopupMenu = null;
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            // --- selection mode ---
            int nthDownload = app.downloadAdapter.getCheckedItemPosition();
            int nthDownloadAfter = -0x1E;
            if (nthDownload < 0)
                return true;

            switch (item.getItemId()) {
                // --- for Android < 7.0 (API 24) --- click menu item will call onDismiss() even if this item has submenu.
                case R.id.menu_download_move:
                case R.id.menu_download_delete:
                case R.id.menu_download_priority:
                    // --- these items have submenu
                    if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
                        submenuClicked = true;
                    return false;

                case R.id.menu_download_open:
                    File file = app.getDownloadedFile(nthDownload);
                    if (file != null) {
                        // create Intent for Activity
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            Uri uri = Uri.fromFile(file);
                            String url = uri.toString();

                            // grab mime type
                            String newMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                                    MimeTypeMap.getFileExtensionFromUrl(url));

                            intent.setDataAndType(uri, newMimeType);
                            startActivity (intent);
                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                    else {
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        // builder.setTitle(R.string.menu_download_open);
                        builder.setIcon(android.R.drawable.ic_dialog_alert);
                        builder.setMessage(R.string.message_file_not_exist);
                        builder.setPositiveButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                        builder.show();
                    }
                    break;

                case R.id.menu_download_delete_recycle:
                    app.downloadAdapter.notifyItemChanged(nthDownload);
                    nthDownloadAfter = app.recycleNthDownload(nthDownload);
                    break;

                case R.id.menu_download_delete_data:
                    app.deleteNthDownload(nthDownload, false);
                    return true;

                case R.id.menu_download_delete_file:
                    if (app.setting.ui.confirmDelete)
                        confirmDeleteDownloadFile();
                    else
                        deleteSelectedDownloadFile();
                    return true;

                case R.id.menu_download_force_start:
                    app.downloadAdapter.notifyItemChanged(nthDownload);
                    nthDownloadAfter = app.activateNthDownload(nthDownload);
                    // --- start timer handler ---
                    app.timerHandler.startQueuing();
                    break;

                case R.id.menu_download_start:
                    app.downloadAdapter.notifyItemChanged(nthDownload);
                    nthDownloadAfter = app.tryQueueNthDownload(nthDownload);
                    // --- start timer handler ---
                    app.timerHandler.startQueuing();
                    break;

                case R.id.menu_download_pause:
                    app.downloadAdapter.notifyItemChanged(nthDownload);
                    nthDownloadAfter = app.pauseNthDownload(nthDownload);
                    break;

                case R.id.menu_download_move_up:
                    app.downloadAdapter.notifyItemChanged(nthDownload);
                    nthDownloadAfter = app.moveNthDownload(nthDownload, nthDownload -1);
                    break;

                case R.id.menu_download_move_down:
                    app.downloadAdapter.notifyItemChanged(nthDownload);
                    nthDownloadAfter = app.moveNthDownload(nthDownload, nthDownload +1);
                    break;

                case R.id.menu_download_priority_high:
                    app.setNthDownloadPriority(nthDownload, Core.Priority.high);
                    break;

                case R.id.menu_download_priority_normal:
                    app.setNthDownloadPriority(nthDownload, Core.Priority.normal);
                    break;

                case R.id.menu_download_priority_low:
                    app.setNthDownloadPriority(nthDownload, Core.Priority.low);
                    break;

                case R.id.menu_download_properties:
                    long cnodePointer = app.downloadAdapter.pointer;
                    Intent intent = new Intent();
                    Bundle bundle = new Bundle();
                    bundle.putInt("mode", NodeActivity.Mode.download_setting);
                    bundle.putLong("nodePointer", Node.getNthChild(cnodePointer, nthDownload));
                    intent.putExtras(bundle);
                    intent.setClass(MainActivity.this, NodeActivity.class);
                    startActivityForResult(intent, REQUEST_ADD_DOWNLOAD);
                    break;
            }
            // end of switch (item.getItemId())

            if (nthDownloadAfter != -0x1E) {
                app.downloadAdapter.setItemChecked(nthDownload, false);
                if (nthDownloadAfter >= 0) {
                    app.downloadAdapter.setItemChecked(nthDownloadAfter, true);
                    scrollToDownloadPosition(nthDownloadAfter);
                }
            }

            return true;
        }
    }

    public void deleteSelectedDownloadFile() {
        long[] selection = app.downloadAdapter.getCheckedNodes();
        if (selection == null)
            return;

        for (int i=0;  i < selection.length;  i++) {
            if (selection[i] == 0)
                continue;
            app.core.deleteDownload(selection[i], true);
        }
        app.downloadAdapter.clearChoices(false);
        app.downloadAdapter.notifyDataSetChanged();
        app.categoryAdapter.notifyDataSetChanged();
        app.stateAdapter.notifyDataSetChanged();
        app.userAction = true;
        // --- selection mode ---
        decideSelectionMode();
        // --- show message if no download item ---
        decideContent();
    }

    public void confirmDeleteDownloadFile() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage(getResources().getString(R.string.message_delete_file));
        builder.setTitle(getResources().getString(R.string.message_delete_title));
        builder.setPositiveButton(getResources().getString(android.R.string.yes), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                deleteSelectedDownloadFile();
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(getResources().getString(android.R.string.no), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.create().show();
    }

    public void confirmDeleteCategory() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage(getResources().getString(R.string.message_delete_category));
        builder.setTitle(getResources().getString(R.string.message_delete_category_title));
        builder.setPositiveButton(getResources().getString(android.R.string.yes), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                app.deleteNthCategory(app.nthCategory);
                app.categoryAdapter.setItemChecked(app.nthCategory, true);
                app.stateAdapter.setItemChecked(app.nthStatus, true);
                app.categoryAdapter.notifyItemClicked(categoryListView);
                app.stateAdapter.notifyItemClicked(stateListView);
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(getResources().getString(android.R.string.no), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.create().show();
    }

    public void exit() {
        // finish()
        if (Job.queued[Job.SAVE_ALL] == 0)
            Job.saveAll();
        progressJob.waitForReady(R.string.message_saving, new Runnable() {
            @Override
            public void run() {
                app.destroy(false);
            }
        });
    }

    public void confirmExit() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage(getResources().getString(R.string.message_exit));
        builder.setTitle(getResources().getString(R.string.message_exit_title));
        builder.setPositiveButton(getResources().getString(android.R.string.yes), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                exit();
            }
        });
        builder.setNegativeButton(getResources().getString(android.R.string.no), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.create().show();
    }

    // --------------------------------
    // permission

    private static final int REQUEST_WRITE_STORAGE = 112;
    private static final int REQUEST_ADD_DOWNLOAD = 41;
    private static final int REQUEST_FILE_CHOOSER = 42;
    private static final int REQUEST_FILE_CREATOR = 43;

    protected void checkPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return;

        int permission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.message_permission_sd_card)
                        .setTitle(R.string.message_permission_required);
                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        makeRequest();
                    }
                });
                builder.show();
            }
            else {
                makeRequest();
            }
        }
    }

    protected void makeRequest() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_WRITE_STORAGE);
    }

    protected void startFileChooser() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.putExtra(Intent.EXTRA_TITLE, "Pick category json file");
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/*");
            // Android doesn't support 'json', getMimeTypeFromExtension("json") return null
            // intent.setType(MimeTypeMap.getSingleton().getMimeTypeFromExtension("zip"));
            startActivityForResult(intent, REQUEST_FILE_CHOOSER);
        }
    }

    protected void onFileChooserResult(Uri treeUri) {
        ParcelFileDescriptor parcelFD;

        grantUriPermission(getPackageName(), treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            parcelFD = getContentResolver().openFileDescriptor(treeUri, "r");
        }
        catch (Exception e) {
            parcelFD = null;
        }

        if (parcelFD == null) {
            revokeUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return;
        }
        // --- load category on thread ---
        Job.loadFd(parcelFD.detachFd());
        progressJob.filename = DocumentFile.fromSingleUri(this, treeUri).getName();
        progressJob.treeUri = treeUri;
        progressJob.waitForReady(R.string.message_loading, new Runnable() {
            @Override
            public void run() {
                boolean isOK = Job.result[Job.LOAD_FD] == 0;
                handleFileChooserResult(progressJob.filename, isOK);
                revokeUriPermission(progressJob.treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        });
    }

    protected void handleFileChooserResult(String filename, boolean ok) {
        if (ok) {
            app.categoryAdapter.notifyDataSetChanged();
            app.stateAdapter.notifyDataSetChanged();
            // --- Snackbar ---
            Snackbar.make(findViewById(R.id.fab), getString(R.string.message_file_load_ok, filename),
                    Snackbar.LENGTH_LONG).setAction("Action", null).show();
            // --- Toast ---
            //Toast.makeText(this, getString(R.string.message_file_load_ok, filename),
            //        Toast.LENGTH_SHORT).show();

            // --- select category that just loaded.
            app.categoryAdapter.setItemChecked(app.categoryAdapter.getItemCount()-1, true);
            app.categoryAdapter.notifyItemClicked(categoryListView);
            // --- start timer handler ---
            app.timerHandler.startQueuing();
        }
        else {
            // --- Snackbar ---
            Snackbar.make(findViewById(R.id.fab),getString(R.string.message_file_load_fail, filename),
                    Snackbar.LENGTH_LONG).setAction("Action", null).show();
            // --- Toast ---
            //Toast.makeText(this, getString(R.string.message_file_load_fail, filename),
            //        Toast.LENGTH_SHORT).show();
        }
    }

    protected void startFileCreator() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/*");
            // Android doesn't support 'json', getMimeTypeFromExtension("json") return null
            startActivityForResult(intent, REQUEST_FILE_CREATOR);
        }
    }

    protected void onFileCreatorResult(Uri treeUri) {
        ParcelFileDescriptor parcelFD;

        grantUriPermission(getPackageName(), treeUri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        try {
            parcelFD = getContentResolver().openFileDescriptor(treeUri, "w");
        }
        catch (Exception e) {
            parcelFD = null;
        }

        if (parcelFD == null) {
            revokeUriPermission(treeUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            return;
        }
        // --- save category on thread ---
        Job.saveFd(app.getNthCategory(app.nthCategory), parcelFD.detachFd());
        progressJob.filename = DocumentFile.fromSingleUri(this, treeUri).getName();
        progressJob.treeUri = treeUri;
        progressJob.waitForReady(R.string.message_saving, new Runnable() {
            @Override
            public void run() {
                boolean isOK = Job.result[Job.SAVE_FD] == 0;
                showFileCreatorResult(progressJob.filename, isOK);
                revokeUriPermission(progressJob.treeUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }
        });
    }

    protected void showFileCreatorResult(String filename, boolean ok) {
        if (ok) {
            // --- Snackbar ---
            Snackbar.make(findViewById(R.id.fab), getString(R.string.message_file_save_ok, filename),
                    Snackbar.LENGTH_LONG).setAction("Action", null).show();
            // --- Toast ---
            //Toast.makeText(this, getString(R.string.message_file_save_ok, filename),
            //        Toast.LENGTH_SHORT).show();
        }
        else {
            // --- Snackbar ---
            Snackbar.make(findViewById(R.id.fab), getString(R.string.message_file_save_fail, filename),
                    Snackbar.LENGTH_LONG).setAction("Action", null).show();
            // --- Toast ---
            //Toast.makeText(this, getString(R.string.message_file_save_fail, filename),
            //        Toast.LENGTH_SHORT).show();
        }
    }

    //  @Override
    protected void onActivityResult (int requestCode, int resultCode, Intent resultData) {
        Uri  treeUri;

        if (resultCode == RESULT_CANCELED)
            return;

        switch (requestCode) {
            case REQUEST_FILE_CHOOSER:
                treeUri = resultData.getData(); // you can't use Uri.fromFile() to get path
                onFileChooserResult(treeUri);
                break;

            case REQUEST_FILE_CREATOR:
                treeUri = resultData.getData(); // you can't use Uri.fromFile() to get path
                onFileCreatorResult(treeUri);
                break;

            case REQUEST_ADD_DOWNLOAD:
                // --- start timer handler ---
                app.timerHandler.startQueuing();
                break;

            default:
                break;
        }
    }

    // ------------------------------------------------------------------------
    // SensorManager
/*
    SensorManager sensorManager;
    SensorEventListener sensorEventListener;

    void registerSensorEvent() {
        if (sensorManager == null)
            sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);

        if (sensorEventListener == null) {
            sensorEventListener = new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                        // Orientation : PORTRAIT
                    }
                    else {
                        // "Orientation : LANDSCAPE"
                    }
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {}
            };
        }

        sensorManager.registerListener(sensorEventListener,
                sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_NORMAL);
    }

    void unregisterSensorEvent() {
        sensorManager.unregisterListener(sensorEventListener);
    }
*/

    // ------------------------------------------------------------------------
    // ProgressJob

    private class ProgressJob implements Runnable {
        public int      messageId;
        public Handler  handler;
        public Runnable runnable;
        ProgressDialog  progressDialog;
        // --- parameter ---
        public String   filename;
        public Uri      treeUri;

        public ProgressJob(Handler handler) {
            this.handler = handler;
        }

        public void destroy() {
            if (progressDialog != null)
                progressDialog.dismiss();
        }

        public void waitForReady(int messageId, Runnable runnable) {
            this.messageId = messageId;
            this.runnable = runnable;
            handler.post(this);
        }

        @Override
        public void run() {
            // --- wait MainApp ready
            if (Job.queuedTotal > 0) {
                // --- progress dialog ---
                if (progressDialog == null) {
                    // --- To disable the user interaction you just need to add the following code
                    getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                    // --- create progress dialog
                    progressDialog = new ProgressDialog(MainActivity.this);
                    progressDialog.setMessage(getString(messageId));
                    progressDialog.setIndeterminate(false);
                    progressDialog.setCancelable(false);
                    progressDialog.setCanceledOnTouchOutside(false);
                    progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                    // --- test progress dialog ---
                    // progressDialog.show();
                    // handler.postDelayed(this, 3500);
                    // return;
                }
                progressDialog.show();
                // --- recheck MainApp every 100 millisecond ---
                handler.postDelayed(this, 100);
                return;
            }

            if (runnable != null)
                runnable.run();
            filename = null;
            treeUri = null;

            if (progressDialog != null) {
                progressDialog.dismiss();
                progressDialog = null;
            }
            // --- To get user interaction back you just need to add the following code
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        }
    };

    // ------------------------------------------------------------------------
    // Handler, Runnable, and Timer Interval

    private Handler  handler = new Handler();
    private static final int speedInterval = 1000;

    public void initHandler() {
        handler.postDelayed(speedRunnable, speedInterval);
        // --- ad ---
        if (BuildConfig.HAVE_ADS) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    initAd();
                }
            }, 1000);
        }
    }

    private Runnable speedRunnable = new Runnable() {
        int downloadSpeedLast = 0;
        int uploadSpeedLast = 0;

        @Override
        public void run() {
            if (app.core.downloadSpeed == downloadSpeedLast && app.core.uploadSpeed == uploadSpeedLast) {
                handler.postDelayed(this, speedInterval);
                return;
            }

            // show speed in subtitle
            if (app.core.downloadSpeed == 0 && app.core.uploadSpeed == 0)
                toolbar.setSubtitle(null);
            else {
                String string = Util.stringFromIntUnit(app.core.downloadSpeed, 1) + " ↓ ";
                if (app.core.uploadSpeed > 0)
                    string +=   Util.stringFromIntUnit(app.core.uploadSpeed, 1)   + " ↑";
                toolbar.setSubtitle(string);
            }
            downloadSpeedLast = app.core.downloadSpeed;
            uploadSpeedLast = app.core.uploadSpeed;

            // call this function after the specified time interval
            handler.postDelayed(this, speedInterval);
        }
    };

    // ------------------------------------------------------------------------
    // Ad
    View  adView = null;

    public void initAd() {
        if (BuildConfig.HAVE_ADS) {
            AdView adView;
            AdSize adSize;
            LinearLayout adParent = (LinearLayout) findViewById(R.id.linearAd);

            // adSize = AdSize.BANNER;
            adSize = AdSize.SMART_BANNER;
            adView = new AdView(this);
            adView.setAdUnitId("ca-app-pub-2883534618110931/1238672609");
            adView.setAdSize(adSize);
            adParent.addView(adView);
            this.adView = adView;

            AdRequest adRequest = new AdRequest.Builder()
                    .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                    .addTestDevice("TEST_DEVICE_ID")
                    .build();
            adView.loadAd(adRequest);  // SIGSEGV (signal SIGSEGV: invalid address (fault address: 0x0))
        }
    }

}
