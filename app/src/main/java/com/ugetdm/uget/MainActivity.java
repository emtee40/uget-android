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
                builder.setMessage(R.string.message_permission_required);
                //        .setTitle("Permission required");

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
