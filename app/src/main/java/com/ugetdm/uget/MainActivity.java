package com.ugetdm.uget;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.ugetdm.uget.lib.Core;
import com.ugetdm.uget.lib.Node;
import com.ugetdm.uget.lib.Util;

import java.io.File;
import java.util.regex.PatternSyntaxException;

public class MainActivity extends AppCompatActivity {
    // MainApp data
    public static MainApp app = null;
    // --- avoid menu popup twice ---
    public PopupMenu    popupMenuDownload = null;
    // View
    public Toolbar      toolbar;
    public DrawerLayout drawer;

    // ------------------------------------------------------------------------
    // entire lifetime: ORIENTATION

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- init MainApp start ---
        app = (MainApp)getApplicationContext();
        app.startRunning();
        // --- init MainApp end ---

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
                startActivity(intent);
            }
        });

        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        updateToolbar();
        initTraveler();
        initTimeoutHandler();
    }

    @Override
    protected void onDestroy() {
        app.mainActivity = null;
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

    @Override
    protected void onStart() {
        super.onStart();

        checkPermission();
    }

    @Override
    protected void onStop() {
        super.onStop();

        // offline status
        app.saveStatus();
    }

    // ------------------------------------------------------------------------
    // foreground lifetime

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
            AlertDialog.Builder MyAlertDialog = new AlertDialog.Builder(this);
            MyAlertDialog.setTitle(getString(R.string.pref_clipboard_type_error_title));
            MyAlertDialog.setMessage(getString(R.string.pref_clipboard_type_error_message));

            DialogInterface.OnClickListener OkClick = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {}
            };
            MyAlertDialog.setNeutralButton(getResources().getString(android.R.string.ok), OkClick);
            MyAlertDialog.show();
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

        app.saveAllData();
        // --- ad ---
        if (BuildConfig.HAVE_ADS) {
            if (adView != null)
                ((AdView)adView).pause();
        }
    }

    // ------------------------------------------------------------------------
    // other

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

    public void processUriFromIntent() {
        Intent intent = getIntent();
        String action = intent.getAction();
        // if this is from the share menu
        if (Intent.ACTION_SEND.equals(action) && intent.hasExtra(Intent.EXTRA_TEXT)) {
            String uri = intent.getStringExtra(Intent.EXTRA_TEXT);
            // clear processed intent
            intent.removeExtra(Intent.EXTRA_TEXT);

            if (uri != null) {
                if (app.setting.ui.skipExistingUri && app.core.isUriExist(uri.toString()) == true)
                    return;
                // match
                long cnode = app.core.matchCategory(uri.toString(), null);
                if (cnode == 0)
                    cnode = Node.getNthChild(app.core.nodeReal, 0);
                if (cnode != 0)
                    app.core.addDownloadByUri(uri.toString(), cnode, true);
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
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (app.nDownloadSelected > 0) {
            // --- selection mode ---
            app.downloadAdapter.clearChoices();
            app.nDownloadSelected = 0;
            decideMenuVisible();
            updateToolbar();
        }
        else if (app.setting.ui.exitOnBack) {
            if (app.setting.ui.confirmExit) {
                confirmExit();
                return;
            }
        }
        else {
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
        // --- category menu ---
        item = menu.findItem((R.id.action_category_delete));
        item.setEnabled(app.nthCategory > 0);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        Intent intent;
        Bundle bundle;
        long   selection[];
        int     position;

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
                runFileChooser();
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
                runFileCreator();
                break;

            case R.id.action_save_all:
                app.saveAllData();
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
                startActivity(intent);
                break;

            case R.id.action_resume_all:
                app.core.resumeCategories();
                app.downloadAdapter.notifyDataSetChanged();
                break;

            case R.id.action_pause_all:
                app.core.pauseCategories();
                app.downloadAdapter.notifyDataSetChanged();
                break;

            case R.id.action_offline:
                if (app.setting.offlineMode)
                    app.setting.offlineMode = false;
                else
                    app.setting.offlineMode = true;
                decideTitle();
                break;

            case R.id.action_settings:
                intent = new Intent();
                intent.setClass(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
                return true;

            case R.id.action_exit:
                if (app.setting.ui.confirmExit)
                    confirmExit();
                else {
                    finish();
                    app.onTerminate();
                }
                break;

            // --- selection mode ---
            case R.id.action_start:
                selection = app.downloadAdapter.getCheckedNode();
                for (int i=0;  i < selection.length;  i++) {
                    position = app.getDownloadNodePosition(selection[i]);
                    if (position != -1)
                        app.tryQueueNthDownload(position);
                }
                app.downloadAdapter.setCheckedNode(selection);
                break;

            case R.id.action_pause:
                selection = app.downloadAdapter.getCheckedNode();
                for (int i=0;  i < selection.length;  i++) {
                    position = app.getDownloadNodePosition(selection[i]);
                    if (position != -1)
                        app.pauseNthDownload(position);
                }
                app.downloadAdapter.setCheckedNode(selection);
                break;

            case R.id.action_select_all:
                int  size = app.downloadAdapter.getItemCount();
                for (int i = 0;  i < size;  i++)
                    app.downloadAdapter.setItemChecked(i, true);
                app.nDownloadSelected = size;
                decideTitle();    // updateToolbar()
                break;

            case R.id.action_delete_recycle:
                selection = app.downloadAdapter.getCheckedNode();
                for (int i=0;  i < selection.length;  i++) {
                    position = app.getDownloadNodePosition(selection[i]);
                    if (position != -1) {
                        if (app.recycleNthDownload(position) == -1) {
                            // current position was removed
                            selection[i] = 0;
                            app.nDownloadSelected--;
                        }
                    }
                }
                app.downloadAdapter.setCheckedNode(selection);
                if (app.nDownloadSelected == 0) {
                    decideMenuVisible();
                    updateToolbar();
                }
                break;

            case R.id.action_delete_data:
                selection = app.downloadAdapter.getCheckedNode();
                for (int i=0;  i < selection.length;  i++) {
                    position = app.getDownloadNodePosition(selection[i]);
                    if (position != -1) {
                        app.deleteNthDownload(position, false);
                        app.downloadAdapter.selections.delete(position);
                    }
                }
                app.nDownloadSelected = 0;
                decideMenuVisible();
                updateToolbar();
                break;

            case R.id.action_delete_file:
                if (app.setting.ui.confirmDelete)
                    confirmDeleteDownloads();
                else {
                    selection = app.downloadAdapter.getCheckedNode();
                    for (int i=0;  i < selection.length;  i++) {
                        position = app.getDownloadNodePosition(selection[i]);
                        if (position != -1) {
                            app.deleteNthDownload(position, true);
                            app.downloadAdapter.selections.delete(position);
                        }
                    }
                    app.nDownloadSelected = 0;
                    decideMenuVisible();
                    updateToolbar();
                }
                break;

            default:
                return false;
        }

        // --- show message if no download ---
        decideContent();

        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------------------------
    // Toolbar (and it's title)

    public void updateToolbar() {
        // --- setup Toolbar after  setSupportActionBar()  and  toggle.syncState()  ---
        if (app.nDownloadSelected == 0) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            toolbar.setNavigationIcon(R.mipmap.ic_notification);
            // --- left side to title space (if NavigationIcon exists)
            toolbar.setContentInsetStartWithNavigation(0);
            // reset Listener when icon changed
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (drawer.isDrawerOpen(GravityCompat.START))
                        drawer.closeDrawer(GravityCompat.START);
                    else
                        drawer.openDrawer(GravityCompat.START);
                }
            });
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
                    if (app.nDownloadSelected > 0)
                        onBackPressed();
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

        if (app.nDownloadSelected == 0) {
            title = getString(R.string.app_name);
            if (app.setting.offlineMode)
                title += " " + getString(R.string.action_offline);
        }
        else {
            // --- selection mode ---
            title = Integer.toString(app.nDownloadSelected);
            //toolbar.setSubtitle(null);
        }
        toolbar.setTitle(title);
    }

    public void decideMenuVisible() {
        boolean selectionMode = app.nDownloadSelected != 0;
        Menu menu = toolbar.getMenu();

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

        // --- selection mode ---
        app.downloadAdapter.setOnItemClickListener(new DownloadAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                int  nSelectedLast = app.nDownloadSelected;

                app.nthDownload = position;
                app.nDownloadSelected = app.downloadAdapter.getCheckedItemCount();
                if (app.nDownloadSelected != nSelectedLast) {
                    if (app.nDownloadSelected == 0 || nSelectedLast == 0)
                        decideMenuVisible();
                    updateToolbar();
                }
                // --- avoid popup menu when exiting selection mode ---
                if (app.nDownloadSelected == 0 && nSelectedLast != 1) {
                    app.downloadAdapter.setItemChecked(position, true);
                    popupDownloadMenu(null);
                }
            }
        });

        // --- selection mode ---
        app.downloadAdapter.setOnItemLongClickListener(new DownloadAdapter.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(View view, int position) {
                int  nSelectedLast = app.nDownloadSelected;

                app.nthDownload = position;
                app.nDownloadSelected = app.downloadAdapter.getCheckedItemCount();
                if (app.nDownloadSelected != nSelectedLast) {
                    if (app.nDownloadSelected == 0 || nSelectedLast == 0)
                        decideMenuVisible();
                    updateToolbar();
                }
                return true;
            }
        });

        app.categoryAdapter.setOnItemClickListener(new CategoryAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                onCategoryItemClick(position);
            }
        });

        app.categoryAdapter.setOnItemLongClickListener(new CategoryAdapter.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(View view, int position) {
                onCategoryItemClick(position);
                app.categoryAdapter.setItemChecked(position, true);
                // --- popupmenu ---
                PopupMenu popupMenu = new PopupMenu(MainActivity.this, findViewById(R.id.state_listview_label));
                popupMenu.inflate(R.menu.main_category);
                popupMenu.getMenu().findItem(R.id.action_category_delete).setEnabled(app.nthCategory > 0);
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
        });

        app.stateAdapter.setOnItemClickListener(new StateAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                app.nthStatus = position;
                app.switchDownloadAdapter();
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

    public void onCategoryItemClick(int position) {
        app.nthCategory = position;
        app.switchDownloadAdapter();
        // --- category menu ---
        invalidateOptionsMenu();    // this will call onPrepareOptionsMenu()
        // --- category button up/down ---
        decideCategoryButtonEnable();
        // --- show message if no download ---
        decideContent();
    }

    // --- category button up/down ---
    public void decideCategoryButtonEnable() {
        ImageView imageView;
        imageView = findViewById(R.id.category_move_down);
        if (app.nthCategory == 0 || app.nthCategory == Node.nChildren(app.core.nodeReal)) {
            imageView.setEnabled(false);
            imageView.setImageAlpha(64);    // 0 - 255
        }
        else {
            imageView.setEnabled(true);
            imageView.setImageAlpha(255);    // 0 - 255
        }
        imageView = findViewById(R.id.category_move_up);
        if (app.nthCategory < 2) {
            imageView.setEnabled(false);
            imageView.setImageAlpha(64);    // 0 - 255
        }
        else {
            imageView.setEnabled(true);
            imageView.setImageAlpha(255);    // 0 - 255
        }
    }

    // ------------------------------------------------------------------------
    // Download Menu

    private void popupDownloadMenu(View view) {
        // --- avoid menu popup twice ---
        if (popupMenuDownload != null)
            return;

        if (view == null)
            view = findViewById(R.id.action_batch);
        if (view == null)
            view = findViewById(R.id.action_file);
        PopupMenu popupMenu = new PopupMenu(this, view);
        popupMenu.inflate(R.menu.main_popup);

        // --- avoid menu popup twice ---
        popupMenuDownload = popupMenu;

        popupMenu.setOnDismissListener(new PopupMenu.OnDismissListener() {
            @Override
            public void onDismiss(PopupMenu popupMenu) {
                popupMenuDownload = null;
                // --- clear choice ---
                app.downloadAdapter.clearChoices();
                if (app.nthDownload >= 0)
                    app.downloadAdapter.notifyItemChanged(app.nthDownload);
                // --- show message if no download ---
                decideContent();
            }
        });

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                long  dNodePointer;
                int   nthDownloadLast;

                switch (item.getItemId()) {
                    case R.id.menu_download_open:
                        File file = app.getDownloadedFile(app.nthDownload);
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
                            AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this);
                            // dialog.setTitle(R.string.menu_open);
                            dialog.setIcon(android.R.drawable.ic_dialog_alert);
                            dialog.setMessage(R.string.message_file_not_exist);
                            dialog.setPositiveButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                            dialog.show();
                        }
                        break;

                    case R.id.menu_download_delete_recycle:
                        app.nthDownload = app.recycleNthDownload(app.nthDownload);
                        if (app.nthDownload > 0)
                            downloadListView.smoothScrollToPosition(app.nthDownload);
                        // app.downloadAdapter.setItemChecked(app.nthDownload, true);
                        break;

                    case R.id.menu_download_delete_data:
                        app.deleteNthDownload(app.nthDownload, false);
                        app.nthDownload = -1;
                        break;

                    case R.id.menu_download_delete_file:
                        if (app.setting.ui.confirmDelete)
                            confirmDeleteDownload();
                        else {
                            app.deleteNthDownload(app.nthDownload, true);
                            app.nthDownload = -1;
                        }
                        break;

                    case R.id.menu_download_force_start:
                        app.nthDownload = app.activateNthDownload(app.nthDownload);
                        if (app.nthDownload > 0)
                            downloadListView.smoothScrollToPosition(app.nthDownload);
                        // app.downloadAdapter.setItemChecked(app.nthDownload, true);
                        break;

                    case R.id.menu_download_start:
                        app.nthDownload = app.tryQueueNthDownload(app.nthDownload);
                        if (app.nthDownload > 0)
                            downloadListView.smoothScrollToPosition(app.nthDownload);
                        // app.downloadAdapter.setItemChecked(app.nthDownload, true);
                        break;

                    case R.id.menu_download_pause:
                        app.nthDownload = app.pauseNthDownload(app.nthDownload);
                        if (app.nthDownload >= 0)
                            downloadListView.smoothScrollToPosition(app.nthDownload);
                            // app.downloadAdapter.setItemChecked(app.nthDownload, true);
                        break;

                    case R.id.menu_download_move_up:
                        if (app.moveNthDownload(app.nthDownload, app.nthDownload -1)) {
                            app.nthDownload--;
                            downloadListView.smoothScrollToPosition(app.nthDownload);
                            // app.downloadAdapter.setItemChecked(app.nthDownload, true);
                        }
                        break;

                    case R.id.menu_download_move_down:
                        if (app.moveNthDownload(app.nthDownload, app.nthDownload +1)) {
                            app.nthDownload++;
                            downloadListView.smoothScrollToPosition(app.nthDownload);
                            // app.downloadAdapter.setItemChecked(app.nthDownload, true);
                        }
                        break;

                    case R.id.menu_download_priority_high:
                        app.setNthDownloadPriority(app.nthDownload, Core.Priority.high);
                        break;

                    case R.id.menu_download_priority_normal:
                        app.setNthDownloadPriority(app.nthDownload, Core.Priority.normal);
                        break;

                    case R.id.menu_download_priority_low:
                        app.setNthDownloadPriority(app.nthDownload, Core.Priority.low);
                        break;

                    case R.id.menu_download_properties:
                        long cnodePointer = app.downloadAdapter.pointer;
                        Intent intent = new Intent();
                        Bundle bundle = new Bundle();
                        bundle.putInt("mode", NodeActivity.Mode.download_setting);
                        bundle.putLong("nodePointer", Node.getNthChild(cnodePointer, app.nthDownload));
                        intent.putExtras(bundle);
                        intent.setClass(MainActivity.this, NodeActivity.class);
                        startActivity(intent);
                        break;
                }
                // end of switch (item.getItemId())
                return true;
            }
        });

        Menu menu = popupMenu.getMenu();

        if (app.nthDownload == -1) {
            menu.findItem(R.id.menu_download_open).setEnabled(false);
            menu.findItem(R.id.menu_download_delete).setEnabled(false);
            menu.findItem(R.id.menu_download_force_start).setEnabled(false);
            menu.findItem(R.id.menu_download_start).setEnabled(false);
            menu.findItem(R.id.menu_download_pause).setEnabled(false);
            menu.findItem(R.id.menu_download_move).setEnabled(false);
            menu.findItem(R.id.menu_download_priority).setEnabled(false).setVisible(false);
            menu.findItem(R.id.menu_download_properties).setEnabled(false).setVisible(false);
        }
        else {
            // Any Category/Status can't move download position if they were sorted.
            if (app.setting.sortBy > 0)
                menu.findItem(R.id.menu_download_move).setEnabled(false);
            else {
                if (app.nthDownload == 0)
                    menu.findItem(R.id.menu_download_move_up).setEnabled(false);
                if (app.nthDownload == Node.nChildren(app.downloadAdapter.pointer) -1)
                    menu.findItem(R.id.menu_download_move_down).setEnabled(false);
            }
            // priority
            int  priority = app.getNthDownloadPriority(app.nthDownload);
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
        }

        // if file doesn't exist, disable menu item: "open" and "delete file".
        if (app.getDownloadedFile(app.nthDownload) == null) {
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

        popupMenu.show();
    }

    public void confirmDeleteDownload() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage(getResources().getString(R.string.message_delete_file));
        builder.setTitle(getResources().getString(R.string.message_delete_title));
        builder.setPositiveButton(getResources().getString(android.R.string.yes), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                app.deleteNthDownload(app.nthDownload, true);
                app.nthDownload = -1;
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

    public void confirmDeleteDownloads() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage(getResources().getString(R.string.message_delete_file));
        builder.setTitle(getResources().getString(R.string.message_delete_title));
        builder.setPositiveButton(getResources().getString(android.R.string.yes), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                long[] selection = app.downloadAdapter.getCheckedNode();
                for (int i=0;  i < selection.length;  i++) {
                    int position = app.getDownloadNodePosition(selection[i]);
                    if (position != -1) {
                        app.deleteNthDownload(position, true);
                        app.downloadAdapter.selections.delete(position);
                    }
                }
                app.nDownloadSelected = 0;
                decideMenuVisible();
                updateToolbar();
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

    public void confirmExit() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage(getResources().getString(R.string.message_exit));
        builder.setTitle(getResources().getString(R.string.message_exit_title));
        builder.setPositiveButton(getResources().getString(android.R.string.yes), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                finish();
                app.onTerminate();
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
    private static final int RESULT_FILE_CHOOSER = 42;
    private static final int RESULT_FILE_CREATOR = 43;

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

                AlertDialog dialog = builder.create();
                dialog.show();
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

    protected void runFileChooser() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.putExtra(Intent.EXTRA_TITLE, "Pick category json file");
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/*");
            // Android doesn't support 'json', getMimeTypeFromExtension("json") return null
            // intent.setType(MimeTypeMap.getSingleton().getMimeTypeFromExtension("zip"));
            startActivityForResult(intent, RESULT_FILE_CHOOSER);
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

        String filename = DocumentFile.fromSingleUri(this, treeUri).getName();
        if (parcelFD != null && app.core.loadCategory(parcelFD.detachFd()) != 0) {
            app.categoryAdapter.notifyDataSetChanged();
            app.stateAdapter.notifyDataSetChanged();
            // --- Snackbar ---
            Snackbar.make(findViewById(R.id.fab), getString(R.string.message_file_load_ok) +
                    " - " + filename, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            // --- Toast ---
            //Toast.makeText(this, getString(R.string.message_file_load_ok) + " - " + filename,
            //        Toast.LENGTH_SHORT).show();

            // --- select category that just loaded.
            app.categoryAdapter.setItemChecked(app.categoryAdapter.getItemCount()-1, true);
            app.categoryAdapter.notifyItemClicked(app.mainActivity.categoryListView);
        }
        else {
            // --- Snackbar ---
            Snackbar.make(findViewById(R.id.fab),getString(R.string.message_file_load_fail) +
                    " - " + filename, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            // --- Toast ---
            //Toast.makeText(this, getString(R.string.message_file_load_fail) + " - " + filename,
            //        Toast.LENGTH_SHORT).show();
        }

        revokeUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    protected void runFileCreator() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/*");
            // Android doesn't support 'json', getMimeTypeFromExtension("json") return null
            startActivityForResult(intent, RESULT_FILE_CREATOR);
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

        String filename = DocumentFile.fromSingleUri(this, treeUri).getName();
        if (parcelFD != null && app.saveNthCategory(app.nthCategory, parcelFD.detachFd()) != false) {
            // --- Snackbar ---
            Snackbar.make(findViewById(R.id.fab), getString(R.string.message_file_save_ok) +
                    " - " + filename, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            // --- Toast ---
            //Toast.makeText(this, getString(R.string.message_file_save_ok) + " - " + filename,
            //        Toast.LENGTH_SHORT).show();
        }
        else {
            // --- Snackbar ---
            Snackbar.make(findViewById(R.id.fab), getString(R.string.message_file_save_fail) +
                    " - " + filename, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            // --- Toast ---
            //Toast.makeText(this, getString(R.string.message_file_save_fail) + " - " + filename,
            //        Toast.LENGTH_SHORT).show();
        }

        revokeUriPermission(treeUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }

    //  @Override
    protected void onActivityResult (int requestCode, int resultCode, Intent resultData) {
        if (resultCode == RESULT_CANCELED)
            return;
        Uri  treeUri = resultData.getData(); // you can't use Uri.fromFile() to get path

        DocumentFile docFile = DocumentFile.fromSingleUri(this, treeUri);
        String name = docFile.getName();

        switch (requestCode) {
            case RESULT_FILE_CHOOSER:
                onFileChooserResult(treeUri);
                break;

            case RESULT_FILE_CREATOR:
                onFileCreatorResult(treeUri);
                break;

            default:
                break;
        }
    }

    // ------------------------------------------------------------------------
    // Timeout Interval & Handler

    private static final int speedInterval = 1000;
    private Handler  speedHandler  = new Handler();
    private Runnable speedRunnable = new Runnable() {
        @Override
        public void run() {
            // show speed in subtitle
            if (app.core.downloadSpeed == 0 && app.core.uploadSpeed == 0)
                toolbar.setSubtitle(null);
            else {
                String string = "";
                if (app.core.downloadSpeed > 0)
                    string += "↓ " + Util.stringFromIntUnit(app.core.downloadSpeed, 1);
                if (app.core.uploadSpeed > 0) {
                    if (app.core.downloadSpeed > 0)
                        string += " , ";
                    string += "↑ " + Util.stringFromIntUnit(app.core.uploadSpeed, 1);
                }
                toolbar.setSubtitle(string);
                string = null;
            }

            // show offline/selection in title
            decideTitle();

            // call this function after the specified time interval
            speedHandler.postDelayed(this, speedInterval);
        }
    };

    public void initTimeoutHandler() {
        speedHandler.postDelayed(speedRunnable, speedInterval);
        speedHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                initAd();
            }
        }, 1000);
    }

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
