package com.dar.nclientv2;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import com.dar.nclientv2.api.Inspector;
import com.dar.nclientv2.api.components.Tag;
import com.dar.nclientv2.api.enums.ApiRequestType;
import com.dar.nclientv2.api.enums.Language;
import com.dar.nclientv2.api.enums.TagStatus;
import com.dar.nclientv2.api.enums.TagType;
import com.dar.nclientv2.async.VersionChecker;
import com.dar.nclientv2.async.scrape.BulkScraper;
import com.dar.nclientv2.components.BaseActivity;
import com.dar.nclientv2.loginapi.Login;
import com.dar.nclientv2.settings.DefaultDialogs;
import com.dar.nclientv2.settings.Global;
import com.dar.nclientv2.settings.TagV2;
import com.google.android.material.navigation.NavigationView;

import org.acra.ACRA;

import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class MainActivity extends BaseActivity
        implements NavigationView.OnNavigationItemSelectedListener {
    private Inspector inspector=null;
    private NavigationView navigationView;
    private Tag tag;
    private int related=-1;
    public void setInspector(Inspector inspector) {
        this.inspector = inspector;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Global.loadTheme(this);
        Global.initHttpClient(this);
        Global.initRemoveIgnoredGalleries(this);
        Global.initHighRes(this);
        Global.initOnlyTag(this);
        Global.initByPopular(this);
        Global.initLoadImages(this);
        Global.initOnlyLanguage(this);
        Global.initMaxId(this);
        Global.initInfiniteScroll(this);
        com.dar.nclientv2.settings.Login.initUseAccountTag(this);
        setContentView(R.layout.activity_main);
        BulkScraper.bulkAll(this);
        //getSharedPreferences("Settings",0).edit().remove("first_run").apply();
        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setTitle(R.string.app_name);
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        Uri data=getIntent().getData();
        String q=null;
        TagType dataType=null;
        if(data!=null) {
            List<String>datas=data.getPathSegments();
            Log.d(Global.LOGTAG,datas.size()+"COUNTTTTT");
            for(String s:datas)Log.d(Global.LOGTAG,"PARAMM: "+s);
            if(datas.size()>0){
                switch (datas.get(0)){
                    case "parody":dataType=TagType.PARODY; break;
                    case "character":dataType=TagType.CHARACTER; break;
                    case "tag":dataType=TagType.TAG; break;
                    case "artist":dataType=TagType.ARTIST; break;
                    case "group":dataType=TagType.GROUP; break;
                    case "language":dataType=TagType.LANGUAGE; break;
                    case "category":dataType=TagType.CATEGORY; break;
                    case "search": q=data.getQueryParameter("q");break;
                }
                if(q==null&&datas.size()>1)q=datas.get(1);
            }
            Log.d(Global.LOGTAG, "Q: " + data.getQueryParameter("q"));
        }
        navigationView = findViewById(R.id.nav_view);
        changeNavigationImage(navigationView);
        navigationView.setNavigationItemSelectedListener(this);

        navigationView.getMenu().findItem(R.id.online_favorite_manager).setVisible(com.dar.nclientv2.settings.Login.isLogged());
        navigationView.getMenu().findItem(R.id.action_login).setTitle(com.dar.nclientv2.settings.Login.isLogged()?R.string.logout:R.string.login);
        recycler=findViewById(R.id.recycler);
        refresher=findViewById(R.id.refresher);
        prepareUpdateIcon();
        recycler.setHasFixedSize(true);
        recycler.setItemViewCacheSize(24);
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener(){
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy){
                if(Global.isInfiniteScroll()&&!refresher.isRefreshing()){
                    GridLayoutManager manager = (GridLayoutManager)recycler.getLayoutManager();
                    if(actualPage < totalPage && manager.findLastVisibleItemPosition() >= (recycler.getAdapter().getItemCount()-1-manager.getSpanCount()))
                        new Inspector(MainActivity.this, actualPage + 1, Inspector.getActualQuery(), Inspector.getActualRequestType(),true);
                }

            }
        });
        refresher.setOnRefreshListener(() -> new Inspector(MainActivity.this,Inspector.getActualPage(),Inspector.getActualQuery(),Inspector.getActualRequestType()));
        findViewById(R.id.prev).setOnClickListener(v -> {
            if (actualPage > 1)
                new Inspector(MainActivity.this, actualPage - 1, Inspector.getActualQuery(), Inspector.getActualRequestType());
        });
        findViewById(R.id.next).setOnClickListener(v -> {
            if (actualPage < totalPage)
                new Inspector(MainActivity.this, actualPage + 1, Inspector.getActualQuery(), Inspector.getActualRequestType());

        });
        findViewById(R.id.page_index).setOnClickListener(v -> loadDialog());
        changeLayout(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE);
        Bundle bundle=getIntent().getExtras();
        if(bundle!=null){
            related = getIntent().getExtras().getInt(getPackageName()+".RELATED", -1);
            tag = getIntent().getExtras().getParcelable(getPackageName()+".TAG");
        }
        if(dataType!=null&&q!=null)tag=new Tag(q,0,0,dataType,TagStatus.DEFAULT);
        if(q!=null&&dataType==null){
            toolbar.setTitle(q);
            new Inspector(this,1,q,ApiRequestType.BYSEARCH);
        }else if(related!=-1){
            new Inspector(this,1,""+related,ApiRequestType.RELATED);
            toolbar.setTitle(R.string.related);
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            toggle.setDrawerIndicatorEnabled(false);
        }else if(tag!=null) {
            new Inspector(this,1,tag.toQueryTag(TagStatus.DEFAULT),ApiRequestType.BYTAG);
            toolbar.setTitle(tag.getName());
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            toggle.setDrawerIndicatorEnabled(false);
        } else new Inspector(this,1,"",ApiRequestType.BYALL);
        if(Global.shouldCheckForUpdates(this))new VersionChecker(this,true);
    }

    private void changeNavigationImage(NavigationView navigationView) {
        switch (Global.getTheme()){
            case BLACK:Global.loadImage(R.drawable.ic_logo,navigationView.getHeaderView(0).findViewById(R.id.imageView));navigationView.getHeaderView(0).findViewById(R.id.layout_header).setBackgroundResource(android.R.color.black);break;
            default:Global.loadImage(R.mipmap.ic_launcher,navigationView.getHeaderView(0).findViewById(R.id.imageView));navigationView.getHeaderView(0).findViewById(R.id.layout_header).setBackgroundResource(R.drawable.side_nav_bar);break;
        }
    }

    @Override
    protected void onDestroy(){
        BulkScraper.setActivity(null);
        super.onDestroy();
    }

    private void prepareUpdateIcon(){
        if(Global.getOnlyLanguage()==null)Global.updateOnlyLanguage(this,Language.UNKNOWN);
        else{
            switch (Global.getOnlyLanguage()){
                case ENGLISH:Global.updateOnlyLanguage(this,null);break;
                case JAPANESE:Global.updateOnlyLanguage(this,Language.ENGLISH);break;
                case CHINESE:Global.updateOnlyLanguage(this,Language.JAPANESE);break;
                case UNKNOWN:Global.updateOnlyLanguage(this,Language.CHINESE);break;
            }
        }
    }
    private void updateLanguageIcon(MenuItem item,boolean update){
        //ALL,ENGLISH;JAPANESE;CHINESE;OTHER

        if(Global.getOnlyLanguage()==null){
            Global.updateOnlyLanguage(this,Language.ENGLISH);
            item.setTitle(R.string.only_english);
            item.setIcon(R.drawable.ic_gbbw);
        }
        else
            switch (Global.getOnlyLanguage()){
                case ENGLISH:Global.updateOnlyLanguage(this, Language.JAPANESE);item.setTitle(R.string.only_japanese);item.setIcon(R.drawable.ic_jpbw);break;
                case JAPANESE:Global.updateOnlyLanguage(this, Language.CHINESE);item.setTitle(R.string.only_chinese);item.setIcon(R.drawable.ic_cnbw);break;
                case CHINESE:Global.updateOnlyLanguage(this, Language.UNKNOWN);item.setTitle(R.string.only_other);item.setIcon(R.drawable.ic_help);break;
                case UNKNOWN:Global.updateOnlyLanguage(this, null);item.setTitle(R.string.all_languages);item.setIcon(R.drawable.ic_world);break;
            }
            if(update)new Inspector(MainActivity.this,Inspector.getActualPage(),Inspector.getActualQuery(),Inspector.getActualRequestType());
        Global.setTint(item.getIcon());
    }
    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        }else if(inspector!=null&&inspector.getRequestType()==ApiRequestType.BYSEARCH){
            removeQuery();
        } else {
            super.onBackPressed();
        }
    }
    public void hidePageSwitcher(){
        findViewById(R.id.page_switcher).setVisibility(View.GONE);
    }
    public void showPageSwitcher(final int actualPage,final int totalPage){
        this.actualPage=actualPage;
        this.totalPage=totalPage;
        if(Global.isInfiniteScroll())return;
        findViewById(R.id.page_switcher).setVisibility(totalPage<=1?View.GONE:View.VISIBLE);
        findViewById(R.id.prev).setAlpha(actualPage>1?1f:.5f);
        findViewById(R.id.prev).setEnabled(actualPage>1);
        findViewById(R.id.next).setAlpha(actualPage<totalPage?1f:.5f);
        findViewById(R.id.next).setEnabled(actualPage<totalPage);
        EditText text=findViewById(R.id.page_index);
        text.setText(String.format(Locale.US, "%d/%d", actualPage, totalPage));
    }

    private int actualPage=1,totalPage;
    private void loadDialog(){
        DefaultDialogs.pageChangerDialog(
                new DefaultDialogs.Builder(this).setActual(actualPage).setMax(totalPage).setDialogs(new DefaultDialogs.DialogResults() {
                    @Override
                    public void positive(int actual) {
                        new Inspector(MainActivity.this,actual,Inspector.getActualQuery(),Inspector.getActualRequestType());
                    }
                    @Override
                    public void negative() {}
                }).setTitle(R.string.change_page).setDrawable(R.drawable.ic_find_in_page)
        );
    }

    private Setting setting=null;
    private class Setting{
        final Global.ThemeScheme theme;
        final boolean loadImages,logged,infinite,remove;
        Setting() {
            this.theme = Global.getTheme();
            this.loadImages = Global.isLoadImages();
            this.logged= com.dar.nclientv2.settings.Login.isLogged();
            this.infinite= Global.isInfiniteScroll();
            this.remove= Global.getRemoveIgnoredGalleries();
        }
    }
    private void removeQuery(){
        searchView.setQuery("",false);
        getSupportActionBar().setTitle(R.string.app_name);
        if(related!=-1){
            new Inspector(this,1,""+related,ApiRequestType.RELATED);
            getSupportActionBar().setTitle(R.string.related);
        }
        else if(tag!=null){
            new Inspector(this,1,tag.toQueryTag(TagStatus.DEFAULT),ApiRequestType.BYTAG);
            getSupportActionBar().setTitle(tag.getName());
        }
        else {
            new Inspector(this,1,"",ApiRequestType.BYALL);
            getSupportActionBar().setTitle(R.string.app_name);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ACRA.getErrorReporter().setEnabled(getSharedPreferences("Settings",0).getBoolean(getString(R.string.key_send_report),true));
        com.dar.nclientv2.settings.Login.initUseAccountTag(this);
        navigationView.getMenu().findItem(R.id.action_login).setTitle(com.dar.nclientv2.settings.Login.isLogged()?R.string.logout:R.string.login);
        if(com.dar.nclientv2.settings.Login.isLogged())navigationView.getMenu().findItem(R.id.online_favorite_manager).setVisible(true);
        if(setting!=null){
            Global.initHighRes(this);Global.initOnlyTag(this);Global.initInfiniteScroll(this);Global.initRemoveIgnoredGalleries(this);
            if(setting.remove!=Global.getRemoveIgnoredGalleries()){
                new Inspector(this,1,inspector.getQuery(),inspector.getRequestType());
            }else if(setting.infinite!=Global.isInfiniteScroll()){
                if(Global.isInfiniteScroll()){
                    hidePageSwitcher();
                    if(actualPage != 1) new Inspector(this, 1, inspector.getQuery(), inspector.getRequestType());
                }else new Inspector(this, actualPage, inspector.getQuery(), inspector.getRequestType());
            }
            if(Global.initLoadImages(this)!=setting.loadImages) recycler.getAdapter().notifyItemRangeChanged(0,recycler.getAdapter().getItemCount());
            if(Global.getTheme()!=setting.theme)recreate();
            setting=null;
        }
        invalidateOptionsMenu();
    }
    private SearchView searchView;
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        menu.findItem(R.id.by_popular).setIcon(Global.isByPopular()?R.drawable.ic_star_border:R.drawable.ic_access_time);
        menu.findItem(R.id.by_popular).setTitle(Global.isByPopular()?R.string.sort_by_popular:R.string.sort_by_latest);
        updateLanguageIcon(menu.findItem(R.id.only_language),false);

        if(tag!=null||related!=-1){
            if(tag!=null){
                MenuItem item=menu.findItem(R.id.tag_manager).setVisible(true);
                TagStatus ts=tag.getStatus();
                switch (ts){
                    case DEFAULT:item.setIcon(R.drawable.ic_help);break;
                    case AVOIDED:item.setIcon(R.drawable.ic_close);break;
                    case ACCEPTED:item.setIcon(R.drawable.ic_check);break;
                }
                Global.setTint(menu.findItem(R.id.tag_manager).getIcon());
            }
        }else {
            Global.setTint(menu.findItem(R.id.search).getIcon());
        }
        Global.setTint(menu.findItem(R.id.open_browser).getIcon());
        Global.setTint(menu.findItem(R.id.by_popular).getIcon());
        searchView =(SearchView)menu.findItem(R.id.search).getActionView();
        if(related!=-1){
            menu.findItem(R.id.search).setVisible(false);
            return true;
        }
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if(query.length()==0)return true;
                try {
                    if (tag == null && related == -1) {
                        int id=Integer.parseInt(query);
                        if(id<=Global.getMaxId()){
                            new Inspector(MainActivity.this, -1, "" + id, ApiRequestType.BYSINGLE);
                            return true;
                        }
                    }
                }catch (NumberFormatException ignore){}
                query=query.trim();
                getSupportActionBar().setTitle(query+(tag!=null?' '+tag.getName():""));
                new Inspector(MainActivity.this,1,query+(tag!=null?(' '+tag.toQueryTag(TagStatus.DEFAULT)):""),ApiRequestType.BYSEARCH);
                searchView.setIconified(true);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return true;
            }
        });
        ImageView closeButton = searchView.findViewById(R.id.search_close_btn);
        closeButton.setOnClickListener(v -> removeQuery());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        Intent i;
        switch (id){
            case R.id.by_popular:
                item.setIcon(Global.updateByPopular(this,!Global.isByPopular())?R.drawable.ic_star_border:R.drawable.ic_access_time);
                item.setTitle(Global.isByPopular()?R.string.sort_by_popular:R.string.sort_by_latest);
                Global.setTint(item.getIcon());
                new Inspector(this,1,Inspector.getActualQuery(),Inspector.getActualRequestType());break;
            case R.id.only_language:updateLanguageIcon(item,true);break;
            case R.id.open_browser:
                if(inspector!=null) {
                    i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(inspector.getUsableURL()));
                    startActivity(i);
                }
                break;
            case R.id.tag_manager:
                TagStatus ts=TagV2.updateStatus(tag);
                switch (ts){
                    case DEFAULT:item.setIcon(R.drawable.ic_help);break;
                    case AVOIDED:item.setIcon(R.drawable.ic_close);break;
                    case ACCEPTED:item.setIcon(R.drawable.ic_check);break;
                }
                Global.setTint(item.getIcon());
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showLogoutForm() {
        AlertDialog.Builder builder=new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.ic_exit_to_app).setTitle(R.string.logout).setMessage(R.string.are_you_sure);
        builder.setPositiveButton(android.R.string.yes, (dialogInterface, i) -> {
            Login.logout(MainActivity.this);
            navigationView.getMenu().findItem(R.id.online_favorite_manager).setVisible(false);
            navigationView.getMenu().findItem(R.id.action_login).setTitle(R.string.login);

        }).setNegativeButton(android.R.string.no,null).show();
    }
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        Intent intent;
        int id = item.getItemId();
        switch (id){

            case R.id.downloaded:if(Global.hasStoragePermission(this))startLocalActivity();else requestStorage();break;
            case R.id.action_login:
                if(item.getTitle().equals(getString(R.string.logout))){
                    showLogoutForm();

                }else {
                    intent = new Intent(this, LoginActivity.class);
                    startActivity(intent);
                }
                break;
            case R.id.favorite_manager:
                intent=new Intent(this,FavoriteActivity.class);
                startActivity(intent);
                break;
            case R.id.action_settings:
                setting=new Setting();
                intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                break;
            case R.id.online_favorite_manager:
                intent=new Intent(this,FavoriteActivity.class);
                intent.putExtra(getPackageName()+".ONLINE",true);
                startActivity(intent);
                break;
            case R.id.random:
                intent = new Intent(this, RandomActivity.class);
                startActivity(intent);
                break;
            case R.id.tag_manager:
                intent=new Intent(this,TagFilter.class);
                startActivity(intent);
                break;
        }
        //DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        //drawer.closeDrawer(GravityCompat.START);
        return true;
    }
    @TargetApi(23)
    private void requestStorage(){
        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE},1);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode==1&&grantResults.length >0&&grantResults[0]== PackageManager.PERMISSION_GRANTED)
            startLocalActivity();
    }

    private void startLocalActivity(){
        Intent i=new Intent(this,LocalActivity.class);
        startActivity(i);
    }
}
