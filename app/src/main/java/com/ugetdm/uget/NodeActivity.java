package com.ugetdm.uget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.ugetdm.uget.lib.*;

import java.util.List;

public class NodeActivity extends AppCompatActivity {
    protected static MainApp    app = null;
    protected CategoryAdapter   categoryAdapter;
    protected ViewPagerAdapter  pagerAdapter;
    protected DrawerLayout      drawer;
    protected View              categoryForm;
    protected View              downloadForm;
    protected SequenceForm      sequenceForm;
    protected CategoryProp      categoryProp;
    // bundle and related data
    protected int     mode;
    protected int     nthCategoryReal;    // nthCategory - 1
    protected long    nodePointerKeep;
    protected long    infoPointerKeep;    // Node.info(nodePointerKeep)

    // static
    public static final class Mode {
        public static final int  batch_sequence    = 0x00;
        public static final int  download_creation = 0x01;
        public static final int  download_setting  = 0x02;
        public static final int  category_creation = 0x04;
        public static final int  category_setting  = 0x08;

        public static final int  download_mode = download_creation | download_setting;
        public static final int  category_mode = category_creation | category_setting;
        public static final int  node_creation = download_creation | category_creation;
        public static final int  node_setting = download_setting | category_setting;
    }

    // ------------------------------------------------------------------------
    // initialize functions

    protected void initTabLayout() {
        // --- ViewPagerAdapter ---
        pagerAdapter = new ViewPagerAdapter();
        if ((mode & Mode.category_mode) > 0) {
            categoryForm = getLayoutInflater().inflate(R.layout.form_category,null);
            pagerAdapter.add(categoryForm, getString(R.string.category_setting));
            downloadForm = getLayoutInflater().inflate(R.layout.form_download,null);
            initDownloadForm(downloadForm, true);
            pagerAdapter.add(downloadForm, getString(R.string.download_default));
        }
        else if (mode == Mode.batch_sequence) {
            sequenceForm = new SequenceForm(this);
            pagerAdapter.add(sequenceForm.view, getString(R.string.action_batch));
            downloadForm = getLayoutInflater().inflate(R.layout.form_download,null);
            initDownloadForm(downloadForm, true);
            pagerAdapter.add(downloadForm, getString(R.string.download_setting));
        }
        else {
            downloadForm = getLayoutInflater().inflate(R.layout.form_download,null);
            initDownloadForm(downloadForm, false);
            pagerAdapter.add(downloadForm, getString(R.string.download_setting));
        }

        // --- ViewPager ---
        ViewPager viewPager = findViewById(R.id.viewpager);
        viewPager.setAdapter(pagerAdapter);
        // viewPager.setPageTransformer(true, new ZoomOutPageTransformer());

        // --- TabLayout ---
        TabLayout tabLayout = findViewById(R.id.tablayout);
        if ((mode & Mode.download_mode) > 0)
            tabLayout.setVisibility(View.GONE);    // remove TabLayout if only one page
        else
            tabLayout.setupWithViewPager(viewPager);
    }

    protected void initTraveler() {
        if (mode > Mode.download_creation)
            return;

        categoryAdapter = new CategoryAdapter(app.core.nodeReal, 0);
        categoryAdapter.selectedPosition = nthCategoryReal;
        RecyclerView categoryListView;
        categoryListView = findViewById(R.id.category_listview);
        categoryListView.setLayoutManager(new LinearLayoutManager(this));
        categoryListView.setAdapter(categoryAdapter);
        categoryListView.setHasFixedSize(true);

        categoryAdapter.setOnItemClickListener(new CategoryAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (nthCategoryReal != position) {
                    nthCategoryReal = position;
                    long nodePointer = Node.getNthChild(app.core.nodeReal, position);
                    long infoPointer = Node.info(nodePointer);
                    Info.get(infoPointer, categoryProp);
                    setDownloadProp(downloadForm, categoryProp, true);
                }
                if (drawer != null)
                    drawer.closeDrawer(GravityCompat.START);
            }
        });
    }

    // ------------------------------------------------------------------------
    // Override functions

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_node);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        app = (MainApp)getApplicationContext();
        categoryProp = new CategoryProp();

        // --- bundle ---
        Bundle bundle = getIntent().getExtras();
        mode = bundle.getInt("mode");
        int  nthCategory = bundle.getInt("nthCategory", 0);
        long nodePointer = bundle.getLong("nodePointer");
        long infoPointer = Node.info(nodePointer);
        nthCategoryReal = nthCategory - 1;    // no first item - "All Category"
        if (nthCategoryReal < 0)
            nthCategoryReal = 0;
        if ((mode & Mode.node_setting) > 0) {
            nodePointerKeep = nodePointer;
            infoPointerKeep = infoPointer;
            Info.ref(infoPointerKeep);    // Info.refCount() + 1
        }

        // --- drawer ---
        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer != null) {
            // --- run NodeActivity in phone ---
            ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                    this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
            drawer.addDrawerListener(toggle);
            toggle.syncState();
            if (mode > Mode.download_creation)
                drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        }

        // --- Toolbar --- setup it after  setSupportActionBar()  and  toggle.syncState()
        if (mode > Mode.download_creation) {
            // toolbar.setNavigationIcon(null);
            // toolbar.setContentInsetsRelative(16+32+16, 0);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        else {
            // toolbar.setNavigationIcon(R.drawable.ic_category);
            // toolbar.setContentInsetStartWithNavigation(0);    // right side space of Navigation button
        }
        switch (mode) {
            case Mode.batch_sequence:
                getSupportActionBar().setTitle(getString(R.string.action_batch_sequence));
                break;
            case Mode.download_creation:
                getSupportActionBar().setTitle(getString(R.string.download_creation));
                break;
            case Mode.download_setting:
                getSupportActionBar().setTitle(getString(R.string.download_setting));
                break;
            case Mode.category_creation:
                getSupportActionBar().setTitle(getString(R.string.category_creation));
                break;
            case Mode.category_setting:
                getSupportActionBar().setTitle(getString(R.string.category_setting));
                break;
        }

        initTabLayout();
        initTraveler();

        // --- properties ---
        Info.get(infoPointer, categoryProp);
        if ((mode & Mode.category_mode) > 0) {
            if (mode == Mode.category_creation)
                categoryProp.name = getString(R.string.cnode_copy) + " - " + categoryProp.name;
            setCategoryProp(categoryForm, categoryProp, false);
            setDownloadProp(downloadForm, categoryProp, true);
        }
        else {
            if (mode == Mode.download_creation) {
                Uri uri = app.getUriFromClipboard(false);
                if (uri != null)
                    categoryProp.uri = uri.toString();
            }
            setDownloadProp(downloadForm, categoryProp, false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (infoPointerKeep != 0)
            Info.unref(infoPointerKeep);
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
    // Override - option menu (Toolbar / ActionBar)

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.node, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch(id) {
            case R.id.action_ok:
                onSelectOk();
                break;

            case R.id.action_cancel:
                finish();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onSelectOk() {
        DownloadProp downloadProp;
        long    nodePointer, infoPointer;

        downloadProp = (DownloadProp) categoryProp;
        getDownloadProp(downloadForm, downloadProp, categoryForm != null);
        if (isFolderWritable(downloadProp.folder) == false) {
            startFolderRequest();
            return;
        }

        switch(mode) {
            case Mode.batch_sequence:
                if (sequenceForm.showPreview() == false) {
                    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
                    dialogBuilder.setIcon(android.R.drawable.ic_dialog_alert);
                    dialogBuilder.setTitle(R.string.action_batch_sequence);
                    dialogBuilder.setMessage(sequenceForm.errorMessage);
                    dialogBuilder.show();
                }
                else {
                    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
                    dialogBuilder.setTitle(R.string.action_batch);
                    dialogBuilder.setMessage(getString(R.string.batch_hints) + "\n\n" +
                            getString(R.string.batch_total_counts) + " : " + sequenceForm.count());
                    dialogBuilder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    });
                    dialogBuilder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            batchAdd();
                        }
                    });
                    dialogBuilder.show();
                }
                return;

            case Mode.download_creation:
                if (downloadProp.uri == null || downloadProp.uri.equals("")) {
                    // --- show message : No Download URI ---
                    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
                    dialogBuilder.setIcon(android.R.drawable.ic_dialog_alert);
                    dialogBuilder.setMessage(R.string.message_no_uri);
                    dialogBuilder.show();
                    return;
                }
                nodePointer = Node.create();
                infoPointer = Node.info(nodePointer);
                Info.set(infoPointer, downloadProp);
                app.addDownloadNode(nodePointer, nthCategoryReal + 1);
                break;

            case Mode.download_setting:
                Info.set(infoPointerKeep, downloadProp);
                // if info->ref_count == 1, It's UgetNode is freed by App.
                if (Info.refCount(infoPointerKeep) > 1)
                    app.core.resetDownloadName(nodePointerKeep);
                app.downloadAdapter.notifyDataSetChanged();
                break;

            case Mode.category_creation:
                getCategoryProp(categoryForm, categoryProp, false);
                if (categoryProp.uri == null || categoryProp.name.equals("")) {
                    // --- show message : No Category Name ---
                    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
                    dialogBuilder.setIcon(android.R.drawable.ic_dialog_alert);
                    dialogBuilder.setMessage(R.string.message_no_name);
                    dialogBuilder.show();
                    return;
                }
                nodePointer = Node.create();
                infoPointer = Node.info(nodePointer);
                Info.set(infoPointer, categoryProp);
                app.addCategoryNode(nodePointer);
                break;

            case Mode.category_setting:
                getCategoryProp(categoryForm, categoryProp, false);
                Info.set(infoPointerKeep, categoryProp);
                app.categoryAdapter.notifyDataSetChanged();
                break;
        }

        app.addFolderHistory(categoryProp.folder);
        finish();
    }

    // ------------------------------------------------------------------------
    // Form - Category properties

    public void getCategoryProp(View categoryForm, CategoryProp categoryProp, boolean multiple) {
        EditText editText;
        String string;

        if (multiple == false) {
            editText = (EditText) categoryForm.findViewById(R.id.name_editor);
            categoryProp.name = editText.getText().toString();
        }
        // activeLimit
        editText = (EditText) categoryForm.findViewById(R.id.active_limit_editor);
        string = editText.getText().toString();
        if (string.length() > 0)
            categoryProp.activeLimit = Integer.parseInt(string);
        else
            categoryProp.activeLimit = 2;
        // finishedLimit
        editText = (EditText) categoryForm.findViewById(R.id.finished_limit_editor);
        string = editText.getText().toString();
        if (string.length() > 0)
            categoryProp.finishedLimit = Integer.parseInt(string);
        else
            categoryProp.finishedLimit = 100;
        // recycledLimit
        editText = (EditText) categoryForm.findViewById(R.id.recycled_limit_editor);
        string = editText.getText().toString();
        if (string.length() > 0)
            categoryProp.recycledLimit = Integer.parseInt(string);
        else
            categoryProp.recycledLimit = 100;

        editText = (EditText) categoryForm.findViewById(R.id.hosts_editor);
        categoryProp.hosts = editText.getText().toString();
        editText = (EditText) categoryForm.findViewById(R.id.schemes_editor);
        categoryProp.schemes = editText.getText().toString();
        editText = (EditText) categoryForm.findViewById(R.id.file_types_editor);
        categoryProp.fileTypes = editText.getText().toString();
    }

    public void setCategoryProp(View categoryForm, CategoryProp categoryProp, boolean multiple) {
        EditText editText;

        if (multiple == false) {
            editText = (EditText) categoryForm.findViewById(R.id.name_editor);
            editText.setText(categoryProp.name);
        }
        editText = (EditText) categoryForm.findViewById(R.id.active_limit_editor);
        editText.setText(Integer.toString(categoryProp.activeLimit));
        editText = (EditText) categoryForm.findViewById(R.id.finished_limit_editor);
        editText.setText(Integer.toString(categoryProp.finishedLimit));
        editText = (EditText) categoryForm.findViewById(R.id.recycled_limit_editor);
        editText.setText(Integer.toString(categoryProp.recycledLimit));
        editText = (EditText) categoryForm.findViewById(R.id.hosts_editor);
        editText.setText(categoryProp.hosts);
        editText = (EditText) categoryForm.findViewById(R.id.schemes_editor);
        editText.setText(categoryProp.schemes);
        editText = (EditText) categoryForm.findViewById(R.id.file_types_editor);
        editText.setText(categoryProp.fileTypes);
    }

    // ------------------------------------------------------------------------
    // Form - Download properties

    public void getDownloadProp(View downloadForm, DownloadProp downloadProp, boolean multiple) {
        EditText editText;
        Switch switchWidget;
        String string;

        switchWidget = downloadForm.findViewById(R.id.dnode_startup_switch);
        downloadProp.group = (switchWidget.isChecked()) ? Info.Group.queuing : Info.Group.pause;
        if (multiple == false) {
            editText = (EditText) downloadForm.findViewById(R.id.dnode_uri_editor);
            downloadProp.uri = editText.getText().toString();
        }
        editText = (EditText) downloadForm.findViewById(R.id.dnode_mirrors_editor);
        downloadProp.mirrors = editText.getText().toString();
        editText = (EditText) downloadForm.findViewById(R.id.dnode_file_editor);
        downloadProp.file = editText.getText().toString();

        editText = (EditText) downloadForm.findViewById(R.id.dnode_folder_editor);
        downloadProp.folder = editText.getText().toString();
        // check folder
        int folderLength = downloadProp.folder.length();
        if (folderLength > 1 && downloadProp.folder.charAt(folderLength - 1) == '/')
            downloadProp.folder = downloadProp.folder.substring(0, folderLength - 1);

        editText = (EditText) downloadForm.findViewById(R.id.dnode_referrer_editor);
        downloadProp.referrer = editText.getText().toString();
        editText = (EditText) downloadForm.findViewById(R.id.dnode_user_editor);
        downloadProp.user = editText.getText().toString();
        editText = (EditText) downloadForm.findViewById(R.id.dnode_password_editor);
        downloadProp.password = editText.getText().toString();
        // connections
        editText = (EditText) downloadForm.findViewById(R.id.dnode_connections_editor);
        string = editText.getText().toString();
        if (string.length() > 0)
            downloadProp.connections = Integer.parseInt(string);
        else
            downloadProp.connections = 1;
        // retryLimit
//		editText = (EditText) findViewById(R.id.dnode_retry_editor);
//		string = editText.getText().toString();
//		if (string.length() > 0)
//			downloadProp.retryLimit = Integer.parseInt(string);
//		else
//			downloadProp.retryLimit = 10;
        // proxy port
        editText = (EditText) downloadForm.findViewById(R.id.dnode_proxy_port_editor);
        string = editText.getText().toString();
        if (string.length() > 0)
            downloadProp.proxyPort = Integer.parseInt(string);
        else
            downloadProp.proxyPort = 80;
        // proxy others
        editText = (EditText) downloadForm.findViewById(R.id.dnode_proxy_host_editor);
        downloadProp.proxyHost = editText.getText().toString();
        editText = (EditText) downloadForm.findViewById(R.id.dnode_proxy_user_editor);
        downloadProp.proxyUser = editText.getText().toString();
        editText = (EditText) downloadForm.findViewById(R.id.dnode_proxy_password_editor);
        downloadProp.proxyPassword = editText.getText().toString();
        Spinner spinner = (Spinner) downloadForm.findViewById(R.id.dnode_proxy_type_spinner);
        downloadProp.proxyType = spinner.getSelectedItemPosition();
    }

    public void setDownloadProp(View downloadForm, DownloadProp downloadProp, boolean multiple) {
        EditText editText;
        Switch switchWidget;

        switchWidget = downloadForm.findViewById(R.id.dnode_startup_switch);
        switchWidget.setChecked(downloadProp.group == Info.Group.queuing);
        if (multiple == false) {
            editText = (EditText) downloadForm.findViewById(R.id.dnode_uri_editor);
            editText.setText(downloadProp.uri);
            editText = (EditText) downloadForm.findViewById(R.id.dnode_mirrors_editor);
            editText.setText(downloadProp.mirrors);
            editText = (EditText) downloadForm.findViewById(R.id.dnode_file_editor);
            editText.setText(downloadProp.file);
        }
        editText = (EditText) downloadForm.findViewById(R.id.dnode_folder_editor);
        editText.setText(downloadProp.folder);
        editText = (EditText) downloadForm.findViewById(R.id.dnode_referrer_editor);
        editText.setText(downloadProp.referrer);
        editText = (EditText) downloadForm.findViewById(R.id.dnode_user_editor);
        editText.setText(downloadProp.user);
        editText = (EditText) downloadForm.findViewById(R.id.dnode_password_editor);
        editText.setText(downloadProp.password);
        editText = (EditText) downloadForm.findViewById(R.id.dnode_connections_editor);
        editText.setText(Integer.toString(downloadProp.connections));
//		editText = (EditText) findViewById(R.id.dnode_retry_editor);
//		editText.setText(Integer.toString(downloadProp.retryLimit));
        // proxy
        editText = (EditText) downloadForm.findViewById(R.id.dnode_proxy_port_editor);
        editText.setText(Integer.toString(downloadProp.proxyPort));
        editText = (EditText) downloadForm.findViewById(R.id.dnode_proxy_host_editor);
        editText.setText(downloadProp.proxyHost);
        editText = (EditText) downloadForm.findViewById(R.id.dnode_proxy_user_editor);
        editText.setText(downloadProp.proxyUser);
        editText = (EditText) downloadForm.findViewById(R.id.dnode_proxy_password_editor);
        editText.setText(downloadProp.proxyPassword);
        Spinner spinner = (Spinner) downloadForm.findViewById(R.id.dnode_proxy_type_spinner);
        spinner.setSelection(downloadProp.proxyType);
    }

    protected void initDownloadForm(View downloadForm, boolean multiple) {
        initStartupModeSwitch(downloadForm);
        initProxyTypeSpinner(downloadForm);
        initFolderMenu(downloadForm);

        if (multiple) {
            downloadForm.findViewById(R.id.dnode_uri_row).setVisibility(View.GONE);
            // downloadForm.findViewById(R.id.dnode_uri).setVisibility(View.GONE);
            // downloadForm.findViewById(R.id.dnode_uri_editor).setVisibility(View.GONE);
            downloadForm.findViewById(R.id.dnode_mirrors_row).setVisibility(View.GONE);
            // downloadForm.findViewById(R.id.dnode_mirrors).setVisibility(View.GONE);
            // downloadForm.findViewById(R.id.dnode_mirrors_editor).setVisibility(View.GONE);
            downloadForm.findViewById(R.id.dnode_file_row).setVisibility(View.GONE);
            // downloadForm.findViewById(R.id.dnode_file).setVisibility(View.GONE);
            // downloadForm.findViewById(R.id.dnode_file_editor).setVisibility(View.GONE);
        }
    }

    // ------------------------------------------------------------------------
    // Startup mode switch

    protected void initStartupModeSwitch(View downloadForm) {
        Switch  switchWidget;

        switchWidget = downloadForm.findViewById(R.id.dnode_startup_switch);
        switchWidget.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                TextView  textView = NodeActivity.this.downloadForm.findViewById(R.id.dnode_startup_mode);
                if (isChecked)
                    textView.setText(R.string.dnode_startup_auto);
                else
                    textView.setText(R.string.dnode_startup_manually);
            }
        });
    }

    // ------------------------------------------------------------------------
    // ProxyType

    protected void initProxyTypeSpinner(View parent) {
        SpinnerItems proxyTypeAdapter = new SpinnerItems(this);
        proxyTypeAdapter.names = getResources().getStringArray(R.array.dnode_proxy_type);
        proxyTypeAdapter.imageIds = new int[]{android.R.drawable.presence_offline,
                android.R.drawable.presence_online};

        Spinner spinner = (Spinner)parent.findViewById(R.id.dnode_proxy_type_spinner);
        spinner.setAdapter(proxyTypeAdapter);
        spinner.setOnItemSelectedListener(
                new Spinner.OnItemSelectedListener() {
                    @Override
                    public void
                    onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                        if (position == 0)
                            setProxyEnable(false);
                        else
                            setProxyEnable(true);
                    }

                    @Override
                    public void
                    onNothingSelected(AdapterView<?> arg0) {}
                }
        );
    }

    protected void setProxyEnable(boolean enable) {
        View  view;
        view = findViewById(R.id.dnode_proxy_host);
        view.setEnabled(enable);
        view = findViewById(R.id.dnode_proxy_host_editor);
        view.setEnabled(enable);
        view = findViewById(R.id.dnode_proxy_port);
        view.setEnabled(enable);
        view = findViewById(R.id.dnode_proxy_port_editor);
        view.setEnabled(enable);
        view = findViewById(R.id.dnode_proxy_user);
        view.setEnabled(enable);
        view = findViewById(R.id.dnode_proxy_user_editor);
        view.setEnabled(enable);
        view = findViewById(R.id.dnode_proxy_password);
        view.setEnabled(enable);
        view = findViewById(R.id.dnode_proxy_password_editor);
        view.setEnabled(enable);
    }

    // ------------------------------------------------------------------------
    // FolderMenu

    private static final int ID_MENU_SELECT_FOLDER = 16;

    protected void initFolderMenu(View parent) {
        ImageView  imageView;
        imageView = (ImageView) parent.findViewById(R.id.dnode_folder_arrow);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFolderMenu(v);
            }
        });
    }

    protected void showFolderMenu(View view) {
        PopupMenu popupMenu;
        Menu       menu;
        View       folderEditor;

        folderEditor = downloadForm.findViewById(R.id.dnode_folder_editor);
        popupMenu = new PopupMenu(this, folderEditor);
        menu = popupMenu.getMenu();
        for (int count = 0;  count < app.folderHistory.length;  count++) {
            if (app.folderHistory[count] != null)
                menu.add(app.folderHistory[count]);
        }
        menu.add(Menu.NONE, ID_MENU_SELECT_FOLDER, Menu.NONE,
                getString(R.string.menu_select_folder));

        popupMenu.setOnMenuItemClickListener(
                new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        if (item.getItemId() == ID_MENU_SELECT_FOLDER)
                            startFolderChooser();
                        else {
                            EditText editText = (EditText) findViewById(R.id.dnode_folder_editor);
                            editText.setText(item.getTitle().toString());
                        }
                        return false;
                    }
                }
        );
        popupMenu.show();
    }

    // --------------------------------
    // folder chooser + permission

    private static final int RESULT_FOLDER_CHOOSER = 0xDF0C;
    private static final int RESULT_FOLDER_REQUEST = 0xDF0E;

    protected boolean isFolderWritable(String folder) {
        // for Android 5+
        for (int index = 0;  index < app.folderWritable.length;  index++) {
            if (folder.startsWith(app.folderWritable[index]))
                return true;
        }

        List<UriPermission> list = getContentResolver().getPersistedUriPermissions();
        for (int i = 0; i < list.size(); i++){
            String folderFromUri = FileUtil.getFullPathFromTreeUri(list.get(i).getUri(), this);
            if (folder.startsWith(folderFromUri))
                if (list.get(i).isWritePermission())
                    return true;
        }
        return false;
    }

    protected void startFolderChooser () {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        // intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, RESULT_FOLDER_CHOOSER);
    }

    protected void onFolderChooserResult (Uri  treeUri) {
        String folder = FileUtil.getFullPathFromTreeUri(treeUri, this);
        if (isFolderWritable(folder) == false) {
            onFolderRequestResult(treeUri);
            // --- Snackbar ---
            Snackbar.make(findViewById(R.id.fab), R.string.message_permission_folder_get, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            // --- Toast ---
            //Toast.makeText(this, "Get permission for " + folder,
            //        Toast.LENGTH_SHORT).show();
        }

        EditText editText;
        editText = (EditText) findViewById(R.id.dnode_folder_editor);
        editText.setText(folder);
    }

    protected void startFolderRequest () {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.message_permission_folder)
                .setTitle(R.string.message_permission_required);

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                // intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                        Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                startActivityForResult(intent, RESULT_FOLDER_REQUEST);
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    protected void onFolderRequestResult (Uri  treeUri) {
        grantUriPermission(getPackageName(), treeUri,
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                        Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        getContentResolver().takePersistableUriPermission(treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (resultCode == RESULT_CANCELED)
            return;
        Uri  treeUri = resultData.getData(); // you can't use Uri.fromFile() to get path

        switch (requestCode) {
            case RESULT_FOLDER_CHOOSER:
                onFolderChooserResult(treeUri);
                break;

            case RESULT_FOLDER_REQUEST:
                onFolderRequestResult(treeUri);
                // --- Snackbar ---
                Snackbar.make(findViewById(R.id.fab), FileUtil.getFullPathFromTreeUri(treeUri,this),
                        Snackbar.LENGTH_LONG).setAction("Action", null).show();
                // --- Toast ---
                //Toast.makeText(this, FileUtil.getFullPathFromTreeUri(treeUri,this),
                //        Toast.LENGTH_SHORT).show();
                break;

            default:
                break;
        }
    }

    // ------------------------------------------------------------------------
    // Batch Handler

    public void batchAdd() {
        // --- disable OK and CANCEL ---
        findViewById(R.id.action_ok).setEnabled(false);
        findViewById(R.id.action_cancel).setEnabled(false);
        // --- batch handler ---
        batchUriIndex = 0;
        batchUriArray = sequenceForm.getList();
        batchHandler = new Handler();
        batchHandler.postDelayed(batchRunnable, 0);
    }

    private AlertDialog batchDialog;
    private int      batchUriIndex;
    private String[] batchUriArray;
    private Handler  batchHandler;
    private Runnable batchRunnable = new Runnable() {
        @Override
        public void run() {
            long         timeMillis;
            DownloadProp downloadProp;

            if (batchDialog != null)
                batchDialog.dismiss();
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(NodeActivity.this);
            dialogBuilder.setIcon(android.R.drawable.ic_dialog_info);
            dialogBuilder.setTitle(R.string.action_batch);
            dialogBuilder.setMessage(
                    getString(R.string.batch_remaining) + " : " + (batchUriArray.length - batchUriIndex) + "\n\n"
                    + getString(R.string.batch_total_counts) + " : " + batchUriArray.length);
            batchDialog = dialogBuilder.show();

            timeMillis = System.currentTimeMillis();
            downloadProp = (DownloadProp) categoryProp;
            for (;  batchUriIndex < batchUriArray.length;  batchUriIndex++) {
                if (System.currentTimeMillis() - timeMillis > 300)
                    break;
                long nodePointer = Node.create();
                long infoPointer = Node.info(nodePointer);
                downloadProp.uri = batchUriArray[batchUriIndex];
                Info.set(infoPointer, downloadProp);
                app.addDownloadNode(nodePointer, nthCategoryReal + 1);
            }

            if (batchUriIndex == batchUriArray.length) {
                batchDialog.dismiss();
                finish();
                return;
            }
            batchHandler.postDelayed(this, 100);
        }
    };
}
