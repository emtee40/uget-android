package com.ugetdm.uget;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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
import android.widget.PopupMenu;

import com.ugetdm.uget.lib.Core;
import com.ugetdm.uget.lib.Node;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    // MainApp data
    public static MainApp app = null;
    // --- avoid menu popup twice ---
    public PopupMenu    popupMenuDownload = null;
    // View
    public Toolbar      toolbar;
    public DrawerLayout drawer;

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
            updateToolbar();
        } else {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------------------
    // save & restore

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
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item;
        item = menu.findItem(R.id.action_offline);
        if (item != null)
            item.setChecked(app.setting.offlineMode);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        switch(id) {
            case R.id.action_offline:
                if (app.setting.offlineMode)
                    app.setting.offlineMode = false;
                else
                    app.setting.offlineMode = true;
                decideTitle();
            break;

            case R.id.action_settings:
                Intent intent = new Intent();
                intent.setClass(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------------------------
    // Toolbar (and it's title)

    public void updateToolbar() {
        // --- setup Toolbar after  setSupportActionBar()  and  toggle.syncState()  ---
        if (app.nDownloadSelected == 0) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            toolbar.setNavigationIcon(R.mipmap.ic_launcher_round);
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
            toolbar.setSubtitle(null);
        }
        toolbar.setTitle(title);
    }

    public void decideMenuVisible() {
        boolean selectionMode = app.nDownloadSelected != 0;
        Menu menu = toolbar.getMenu();

        menu.findItem(R.id.action_file).setVisible(selectionMode == false);
        menu.findItem(R.id.action_sequence_batch).setVisible(selectionMode == false);
        menu.findItem(R.id.action_resume_all).setVisible(selectionMode == false);
        menu.findItem(R.id.action_pause_all).setVisible(selectionMode == false);
        menu.findItem(R.id.action_offline).setVisible(selectionMode == false);
        menu.findItem(R.id.action_settings).setVisible(selectionMode == false);

        menu.findItem(R.id.action_start).setVisible(selectionMode);
        menu.findItem(R.id.action_pause).setVisible(selectionMode);
        menu.findItem(R.id.action_select_all).setVisible(selectionMode);
        menu.findItem(R.id.action_remove).setVisible(selectionMode);
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

    // ------------------------------------------------------------------------
    // Download Menu

    private void popupDownloadMenu(View view) {
        // --- avoid menu popup twice ---
        if (popupMenuDownload != null)
            return;

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
                app.downloadAdapter.clearChoices();
            }
        });

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                long  dNodePointer;

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
                        dNodePointer = app.getNthDownloadNode(app.nthDownload);
                        app.recycleNthDownload(app.nthDownload);
                        app.setSelectedDownload(dNodePointer);
                        app.downloadAdapter.setItemChecked(app.nthDownload, true);
                        downloadListView.smoothScrollToPosition(app.nthDownload);
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
                        dNodePointer = app.getNthDownloadNode(app.nthDownload);
                        app.activateNthDownload(app.nthDownload);
                        app.setSelectedDownload(dNodePointer);
                        app.downloadAdapter.setItemChecked(app.nthDownload, true);
                        downloadListView.smoothScrollToPosition(app.nthDownload);
                        break;

                    case R.id.menu_download_start:
                        dNodePointer = app.getNthDownloadNode(app.nthDownload);
                        if (app.setNthDownloadRunnable(app.nthDownload) == false)
                            break;
                        app.setSelectedDownload(dNodePointer);
                        app.downloadAdapter.setItemChecked(app.nthDownload, true);
                        downloadListView.smoothScrollToPosition(app.nthDownload);
                        break;

                    case R.id.menu_download_pause:
                        dNodePointer = app.getNthDownloadNode(app.nthDownload);
                        app.pauseNthDownload(app.nthDownload);
                        app.setSelectedDownload(dNodePointer);
                        app.downloadAdapter.setItemChecked(app.nthDownload, true);
                        downloadListView.smoothScrollToPosition(app.nthDownload);
                        break;

                    case R.id.menu_download_move_up:
                        if (app.moveNthDownload(app.nthDownload, app.nthDownload -1)) {
                            app.nthDownload--;
                            app.downloadAdapter.setItemChecked(app.nthDownload, true);
                            downloadListView.smoothScrollToPosition(app.nthDownload);
                        }
                        break;

                    case R.id.menu_download_move_down:
                        if (app.moveNthDownload(app.nthDownload, app.nthDownload +1)) {
                            app.nthDownload++;
                            app.downloadAdapter.setItemChecked(app.nthDownload, true);
                            downloadListView.smoothScrollToPosition(app.nthDownload);
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
    }

    // --------------------------------
    // permission

    private static final int REQUEST_WRITE_STORAGE = 112;
    private static final int FILE_CHOOSER_CODE = 42;
    private static final int FILE_CREATOR_CODE = 43;

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

                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
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
            startActivityForResult(intent, FILE_CHOOSER_CODE);
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
            Snackbar.make(findViewById(R.id.fab), R.string.message_file_load_ok, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            // --- Toast ---
            //Toast.makeText(this, "load " + filename,
            //        Toast.LENGTH_SHORT).show();
        }
        else {
            // --- Snackbar ---
            Snackbar.make(findViewById(R.id.fab), R.string.message_file_load_fail, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            // --- Toast ---
            //Toast.makeText(this, "Failed to load " + filename,
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
            startActivityForResult(intent, FILE_CREATOR_CODE);
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

        // String filename = DocumentFile.fromSingleUri(this, treeUri).getName();
        if (parcelFD != null && app.saveNthCategory(app.nthCategory, parcelFD.detachFd()) != false) {
            // --- Snackbar ---
            Snackbar.make(findViewById(R.id.fab), R.string.message_file_save_ok, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            // --- Toast ---
            //Toast.makeText(this, "save " + filename,
            //        Toast.LENGTH_SHORT).show();
        }
        else {
            // --- Snackbar ---
            Snackbar.make(findViewById(R.id.fab), R.string.message_file_save_fail, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            // --- Toast ---
            //Toast.makeText(this, "Failed to save " + filename,
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
            case FILE_CHOOSER_CODE:
                onFileChooserResult(treeUri);
                break;

            case FILE_CREATOR_CODE:
                onFileCreatorResult(treeUri);
                break;

            default:
                break;
        }
    }

}
