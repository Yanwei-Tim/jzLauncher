package cn.ingenic.launcher;

import java.util.ArrayList;
import java.util.List;

import android.app.Application;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;

public class AppsDeskManager {
	private static AppsDeskManager sInstance;
	
	public static boolean APPS_DESK_MODE = true; 
	
	public static int CELL_COUNT_X = 2;
	public static int CELL_COUNT_Y = 2;
	public static int CELL_COUNT = CELL_COUNT_X * CELL_COUNT_Y;//每屏放置的图标个数
	public static int DEFAULT_PAGE = 1;
	public static final int AppScreenStartX=2;//横向第三屏开始为app,前两屏为 Left,Home屏
	public static final int AppScreenStartY=0;//竖向第一屏开始为app
	public static final int ExtraX_Screen_size=0;// 0 is Left, 1 is Home page. :TODO
	
	private final static String PREF_APPS_DESK = "pref_apps_desk";
	private final static String KEY_HAS_INIT = "has_init";
	private final static String KEY_SCREEN_COUNT = "screen_count";
	
	private final static int ADDSCREEN=0x111;
	
	private Object lock = new Object();
	
	private Application mApp;
	private Launcher mLauncher;
	private Workspace mWorkSpace;
	
	private AppsDeskManager(Launcher launcher, Workspace workspace){
		mLauncher = launcher;
		mApp = mLauncher.getApplication();
		mWorkSpace = workspace;
		
//		loadConfig();
	}
	
	public static AppsDeskManager init(Launcher launcher, Workspace workspace){
		if(sInstance == null){
			sInstance = new AppsDeskManager(launcher, workspace);
		}
		return sInstance;
	}
	
//	public static void release(){
//		sInstance.releaseAll();
//		sInstance = null;
//	}
	
/*	private void releaseAll(){
		mWorkSpace = null;
		mLauncher = null;
		mApp = null;
	}*/
	
/*	private void loadConfig(){
		//load screen
		mWorkSpace.removeAllViews();
		int count = mLauncher.SCREEN_COUNT;
		for (int i = 0; i < count; i++) {
			LayoutInflater inflate = LayoutInflater.from(mLauncher);
			CellLayout cell = (CellLayout) inflate.inflate(R.layout.workspace_screen, null);
			if(i == EXTRAS_MAIN_SCREEN){
				LinearLayout weatherTime = new ClockWeatherWidget(mLauncher);
				CellLayout.LayoutParams params = new CellLayout.LayoutParams(0, 0, CELL_COUNT_X, CELL_COUNT_Y);
				int childId = LauncherModel.getCellLayoutChildId(LauncherSettings.Favorites.CONTAINER_DESKTOP,
						i, 0, 0, CELL_COUNT_X, CELL_COUNT_Y);
				cell.addViewToCellLayout(weatherTime, 0, childId, params, true);
				cell.lock();
			}
			mWorkSpace.addView(cell);
		}
	}*/
	
	synchronized private void addScreen(){
    	Runnable r = new Runnable(){
			public void run() {
				for(int i=0;i<mWorkSpace.mMaxPageY;i++){//依据行数添加屏数
			    	int screen_x=mWorkSpace.getScreenColumnCount();
			    	LayoutInflater inflate = LayoutInflater.from(mLauncher);
		    		CellLayout cell = (CellLayout)inflate.inflate(R.layout.celllayout, null);
		    		cell.x=screen_x;
		    		cell.y=i;
		    		mWorkSpace.addView(cell);
		    	}
			}};
		Launcher.runOnMainThreak(r);
    	Launcher.SCREEN_COUNT++;
		SharedPreferences pref = mApp.getSharedPreferences(PREF_APPS_DESK, 0);
		SharedPreferences.Editor edit = pref.edit();
		edit.putInt(KEY_SCREEN_COUNT, Launcher.SCREEN_COUNT);
		edit.commit();
	}

	ArrayList<ItemInfo> mItems=new ArrayList<ItemInfo>();

	public void initAppsIcon(){
		if(hasInited()){
			log(" has inited! return!");
			return;
		}
		
		final PackageManager packageManager = mApp.getPackageManager();
		final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = packageManager.queryIntentActivities(mainIntent, 0);
		log(" query apps size = "+apps.size());
//		mWorkSpace.removeAllViews();
		mLauncher.mDB.clearDB();
		/**计算应用图标需要的屏数目*/
		int need_screen_count = apps.size() / CELL_COUNT + ((apps.size() % CELL_COUNT == 0) ? 0 : 1);
		// add screen
		for (int i = 0; i < need_screen_count + ExtraX_Screen_size; i++) {
			addScreen();
		}
		//load first screen
//		mApp.getLauncherProvider().loadDefaultFavorites();
		
		//add icon
        for(int i = 0; i < apps.size(); i++){
			addAppsInfoFromRI(apps.get(i), i);
        }
        addItemsToUI();
		/*mWorkSpace.initAllAppInfo(apps);*/
		setInited();
	}

	public void loadItemsFromDB(){
		if(mItems.size()==0){
			log("loadItemsFromDB, but mItems is  empty..");
			mLauncher.mDB.queryFavs(mItems,mApp.getPackageManager());	
		}
		log("loadItemsFromDB,  mItems size="+mItems.size());
        addItemsToUI();
	}
	
	private void addItemsToUI(){

        Runnable r=new Runnable(){
			@Override
			public void run() {
				
				if(mWorkSpace.getChildCount()==0){
					int count = mItems.size() / CELL_COUNT + ((mItems.size() % CELL_COUNT == 0) ? 0 : 1);
//					addNeedScreen(count);
					for (int j = 0; j < count; j++)
						for (int i = 0; i < mWorkSpace.mMaxPageY; i++) {// 依据行数添加屏数
							int screen_x = mWorkSpace.getScreenColumnCount();
							LayoutInflater inflate = LayoutInflater
									.from(mLauncher);
							CellLayout cell = (CellLayout) inflate.inflate(
									R.layout.celllayout, null);
							cell.x = screen_x;
							cell.y = i;
							mWorkSpace.addView(cell);
						}
					log("addItemsToUI  child empty, inflate now");
				}else{
					log("addItemsToUI  child inited, remove cell form cellLayout now");
					int childcount=mWorkSpace.getChildCount();
					for(int i=0;i<childcount;i++)
						((CellLayout)mWorkSpace.getChildAt(i)).removeAllViews();
				}
				
				for(ItemInfo ai: mItems){
					mWorkSpace.addShortcutToScreen(ai);
				}
				mWorkSpace.invalidate();
				log("add app OVER");
			}};
		Launcher.runOnMainThreak(r);
	}
	
	private Intent getLaunchIntent(ResolveInfo reInfo,ItemInfo ii){
		if(reInfo == null){
			return null;
		}
		String packageName = reInfo.activityInfo.packageName;
		String name = reInfo.activityInfo.name;
		ComponentName cn = new ComponentName(packageName,name);
		Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.setComponent(cn);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		ii.activityName = name;
		return intent;
	}
	
	private void addAppsInfoFromRI(ResolveInfo info,int i){
		final PackageManager pm = mApp.getPackageManager();
	    ContentValues cv=new ContentValues(14);
	    ItemInfo ii = new ItemInfo();
	    String title = (String) info.loadLabel(pm);
	    log(" app title : "+title);
		Intent intent = getLaunchIntent(info, ii);
	    int screen = i / CELL_COUNT,cellX=i % CELL_COUNT % CELL_COUNT_X,cellY=i % CELL_COUNT / CELL_COUNT_X;
	    cv.put(DB.Favorites.TITLE, title);
	    cv.put(DB.Favorites.SCREEN, screen);//:TODO +ExtraX_Screen_size  以给HOME等屏留空间
	    cv.put(DB.Favorites.INTENT, intent.toString());
	    cv.put(DB.Favorites.CELLX, cellX);
	    cv.put(DB.Favorites.CELLY, cellY);
	    cv.put(DB.Favorites.PACKAGENAME, info.activityInfo.packageName);
	    cv.put(DB.Favorites.ACTIVITYNAME, info.activityInfo.name);
	    cv.put(DB.Favorites.URI, intent.toUri(0));
	    cv.put(DB.Favorites.ITEM_TYPE, DB.Favorites.ITEM_TYPE_APPLICATION);
        cv.put(DB.Favorites.ICON_TYPE, DB.Favorites.ICON_TYPE_RESOURCE);
		Drawable icon = info.loadIcon(pm);
//	    ii = new ItemInfo(i/CELL_COUNT, title, info.activityInfo.packageName, icon);
		ii.screen = screen;
		ii.title = title;
		ii.packageName = info.activityInfo.packageName;
		ii.icon = icon;
		ii.cellX = cellX;
		ii.cellY = cellY;
		ii.intent = intent;
        mItems.add(ii);
		addShortcutToDb(cv, i);
	}
	
	private void addShortcutToDb(ContentValues cv, int i){
        if (i < 0) {
/*            InstallShortcutReceiver.addShortcut(data);
            InstallShortcutReceiver.flushInstallQueue(mApp);*/
        } else {
            mLauncher.mDB.insertToDB(cv);
        }
	}
	
	private boolean hasInited(){
		boolean res = false;
		SharedPreferences pref = mApp.getSharedPreferences(PREF_APPS_DESK, 0);
		res = pref.getBoolean(KEY_HAS_INIT, false);
		return res;
	}
	
	private void setInited(){
		SharedPreferences pref = mApp.getSharedPreferences(PREF_APPS_DESK, 0);
		SharedPreferences.Editor edit = pref.edit();
		edit.putBoolean(KEY_HAS_INIT, true);
		edit.commit();
	}
	
	private void log(String log){
		Log.i("sw2df", "[AppsDeskManager]"+log);
	}
}