package com.gexoty.picolibrary;

import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.widget.Toast;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.json.*;
import java.util.Arrays;
import java.util.UUID;
import java.util.List;
import java.util.HashMap;

/** Narrow integrations: display permission UI and read stock catalog metadata. */
public final class LibraryIntegrations {
    private LibraryIntegrations() {}
    private static final String TAG="PicoLibrary";
    /** Local metadata import is independent of PICO account login and never grants entitlements. */
    public static List enrichInventory(List input) {
        JSONArray catalog=selectedCatalog();if(catalog==null)return input;
        try {
            HashMap<String,JSONObject> byPackage=new HashMap<>();
            for(int i=0;i<catalog.length();i++){JSONObject row=catalog.getJSONObject(i);byPackage.put(row.getString("packageName"),row);}
            for(Object data:input) {
                Object app=data.getClass().getMethod("getApp").invoke(data);
                String pkg=(String)app.getClass().getMethod("v").invoke(app);JSONObject row=byPackage.get(pkg);if(row==null)continue;
                Class<?> type=data.getClass();
                type.getMethod("setAppId",long.class).invoke(data,row.optLong("itemId"));
                type.getMethod("setStoreVersionCode",int.class).invoke(data,row.optInt("versionCode"));
                type.getMethod("setStoreVersionName",String.class).invoke(data,row.optString("version"));
                type.getMethod("setStoreName",String.class).invoke(data,row.optString("appName"));
                type.getMethod("setDeviceSupport",boolean.class).invoke(data,true);
                Log.i(TAG,"Store version metadata: "+pkg+" version="+row.optInt("versionCode"));
            }
        }catch(Exception e){Log.e(TAG,"Apply Store catalog metadata",e);}return input;
    }
    public static Context context() throws Exception {
        return (Context)Class.forName("com.pvr.appmanager.AppApplication").getMethod("d").invoke(null);
    }
    public static void openPermissions(Context c,String pkg,String name) {
        if(!StopPolicy.validPackage(pkg))return;
        Intent intent;
        if(c.checkSelfPermission("com.android.permissioncontroller.permision.APP_DIALOG_PERMISSION_DETAILS")==PackageManager.PERMISSION_GRANTED) {
            intent=new Intent("android.intent.pico.app.dialog.PERMISSION_DETAILS").setPackage("com.android.permissioncontroller");
        } else {
            // Stock's documented alternate route is exported to ordinary apps on PICO OS 5.13.7.
            intent=new Intent("pui.settings.action.PERMISSION_DETAIL").addCategory(Intent.CATEGORY_DEFAULT);
            intent.putExtra("pico_permission_app_name",name);
        }
        intent.putExtra(Intent.EXTRA_PACKAGE_NAME,pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {c.startActivity(intent);Log.i(TAG,"Opened PICO permissions for "+pkg);}
        catch(ActivityNotFoundException|SecurityException e) {
            Log.w(TAG,"PICO permission screen unavailable",e);
            try {c.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
            catch(ActivityNotFoundException|SecurityException fallback) {Toast.makeText(c,"Не удалось открыть сведения о доступе",Toast.LENGTH_LONG).show();}
        }
    }
    /** Called on the existing network worker, never on the UI thread. Null selects the normal API. */
    public static JSONArray selectedCatalog() {
        try {
            Context c=context();SharedPreferences prefs=LauncherSettings.prefs(c);
            if(!"root".equals(prefs.getString("catalog_source","pico")))return null;
            try {
                if(prefs.getBoolean("root_onboarding_v1",false)&&!prefs.getBoolean("root_confirmed",false))throw new IOException("Root не предоставлен. Повторный запрос — в настройках библиотеки");
                if(android.os.Process.myUid()/100000!=0)throw new IOException("Чтение каталога поддерживается только для Android user 0");
                JSONArray result=readStoreCatalog(c);
                prefs.edit().putString("catalog_status","Каталог PICO Store: "+result.length()+" установленных приложений").putLong("catalog_read_time",System.currentTimeMillis()).apply();
                Log.i(TAG,"PICO Store catalog metadata read: "+result.length()+" rows");
                return result;
            }catch(Exception e) {
                prefs.edit().putString("catalog_status","Не удалось прочитать каталог: "+e.getMessage()).apply();
                Log.w(TAG,"Stock catalog read failed",e);
                // Explicit Root selection never silently switches to another identity or source.
                return new JSONArray();
            }
        }catch(Exception e){Log.e(TAG,"Catalog source selection",e);return null;}
    }
    private static final String STORE_DB="/data/user/0/com.picovr.store/databases/store_database.db";
    private static byte[] readStoreFile(boolean wal) throws Exception {
        String path=STORE_DB+(wal?"-wal":"");
        String command=wal?"if [ -f "+path+" ]; then cat "+path+"; fi":"cat "+path;
        Process process=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
        ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> timeout=timer.schedule(()->process.destroyForcibly(),8,TimeUnit.SECONDS);
        try(InputStream in=process.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] block=new byte[4096];int n;
            while((n=in.read(block))!=-1) {if(out.size()+n>16777216)throw new IOException("Каталог слишком большой");out.write(block,0,n);}
            if(!process.waitFor(1,TimeUnit.SECONDS) || process.exitValue()!=0)throw new IOException("Root не разрешён или истекло время ожидания");
            return out.toByteArray();
        }finally {timeout.cancel(false);timer.shutdownNow();process.destroy();}
    }
    private static JSONArray readStoreCatalog(Context c) throws Exception {
        byte[] main=null,wal=null;boolean stable=false;
        for(int attempt=0;attempt<2;attempt++) {
            main=readStoreFile(false);wal=readStoreFile(true);
            if(Arrays.equals(main,readStoreFile(false)) && Arrays.equals(wal,readStoreFile(true))){stable=true;break;}
        }
        if(!stable)throw new IOException("PICO Store обновляет каталог. Повторите чтение");
        File directory=new File(c.getCacheDir(),"store-catalog-"+UUID.randomUUID());
        if(!directory.mkdir())throw new IOException("Не удалось создать временную копию каталога");
        try {
            File file=new File(directory,"catalog.db");write(file,main);if(wal.length>0)write(new File(directory,"catalog.db-wal"),wal);
            JSONArray result=new JSONArray();
            // SQLite sees only a private temporary snapshot. Original Store files are never opened for writing.
            try(SQLiteDatabase database=SQLiteDatabase.openDatabase(file.getPath(),null,SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS);
                Cursor cursor=database.rawQuery("SELECT packageName,name,appId,type,icon,coverLandscape,version,versionCode FROM app_install_table",null)) {
                while(cursor.moveToNext()) {
                    String pkg=cursor.getString(0);if(!StopPolicy.validPackage(pkg))continue;
                    try{c.getPackageManager().getPackageInfo(pkg,0);}catch(PackageManager.NameNotFoundException e){continue;}
                    result.put(new JSONObject().put("packageName",pkg).put("appName",cursor.getString(1)).put("itemId",cursor.getLong(2))
                        .put("appType",cursor.getInt(3)).put("appIcon",cursor.getString(4)).put("coverLandscape",cursor.getString(5))
                        .put("version",cursor.getString(6)).put("versionCode",cursor.getLong(7)).put("deviceSupport",true));
                }
            }
            return result;
        }finally {File[] files=directory.listFiles();if(files!=null)for(File file:files)file.delete();directory.delete();}
    }
    private static void write(File file,byte[] bytes) throws IOException {try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}}
}
