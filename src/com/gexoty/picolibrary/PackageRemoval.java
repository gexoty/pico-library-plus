package com.gexoty.picolibrary;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.util.Log;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

/** Installed APKs use Android's confirmation UI; no optimistic DB or card deletion. */
public final class PackageRemoval {
    private static final String TAG="PicoLibrary-Uninstall";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private PackageRemoval() {}
    public static boolean handle(Object dialog) {
        Activity activity=null;
        try {
            activity=(Activity)dialog.getClass().getMethod("getContext").invoke(dialog);
            Object data=dialog.getClass().getMethod("getAppData").invoke(dialog);
            Object app=data.getClass().getMethod("getApp").invoke(data);
            // Web shortcuts and store/download cards retain their existing non-APK behavior.
            if((Boolean)app.getClass().getMethod("C").invoke(app) || (Boolean)app.getClass().getMethod("z").invoke(app))return false;
            String pkg=(String)app.getClass().getMethod("v").invoke(app);
            if(!RemovalPolicy.allowed(pkg,activity.getPackageName()))throw new SecurityException("Удаление этого пакета запрещено");
            ApplicationInfo info=activity.getPackageManager().getApplicationInfo(pkg,0);
            if((info.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0)
                throw new SecurityException("Удаление системных приложений из библиотеки запрещено");
            Intent intent=new Intent(Intent.ACTION_DELETE,Uri.fromParts("package",pkg,null));
            if(intent.resolveActivity(activity.getPackageManager())==null)throw new ActivityNotFoundException("Системное окно удаления недоступно");
            RemovalWatch watch=new RemovalWatch(activity,pkg);
            watch.register();
            try{activity.startActivity(intent);}catch(Exception e){watch.close();throw e;}
            dialog.getClass().getMethod("dismiss").invoke(dialog);
            Log.i(TAG,"Opened system confirmation package="+pkg);
        }catch(Exception e){
            while(e instanceof java.lang.reflect.InvocationTargetException && e.getCause() instanceof Exception)e=(Exception)e.getCause();
            Log.e(TAG,"Cannot open uninstall confirmation",e);
            if(activity!=null)Toast.makeText(activity,"Удаление не начато: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
        // Never enter the original optimistic-delete coroutine after an error.
        return true;
    }
    private static final class RemovalWatch extends BroadcastReceiver {
        final Context context;final WeakReference<Activity> activity;final String pkg;
        final AtomicBoolean closed=new AtomicBoolean();
        final Runnable timeout=this::close;
        RemovalWatch(Activity a,String pkg){context=a.getApplicationContext();activity=new WeakReference<>(a);this.pkg=pkg;}
        void register(){
            IntentFilter filter=new IntentFilter(Intent.ACTION_PACKAGE_REMOVED);filter.addDataScheme("package");
            if(Build.VERSION.SDK_INT>=33)context.registerReceiver(this,filter,Context.RECEIVER_NOT_EXPORTED);
            else context.registerReceiver(this,filter);
            MAIN.postDelayed(timeout,300000);
        }
        void close(){if(closed.compareAndSet(false,true)){MAIN.removeCallbacks(timeout);try{context.unregisterReceiver(this);}catch(IllegalArgumentException ignored){}}}
        @Override public void onReceive(Context c,Intent intent){
            if(intent.getData()==null || !pkg.equals(intent.getData().getSchemeSpecificPart()) || intent.getBooleanExtra(Intent.EXTRA_REPLACING,false))return;
            try{context.getPackageManager().getApplicationInfo(pkg,0);return;}catch(PackageManager.NameNotFoundException confirmed){}
            close();
            new Thread(()->{
                try{
                    Class<?> repo=Class.forName("com.pvr.appmanager.dao.Repository");Object instance=repo.getField("INSTANCE").get(null);
                    Object dao=repo.getMethod("getAppDao").invoke(instance);dao.getClass().getMethod("deleteByPackageName",String.class).invoke(dao,pkg);
                }catch(Exception e){Log.w(TAG,"Confirmed removal: local metadata cleanup",e);}
                LauncherSettings.dirty(context);
                MAIN.post(()->{
                    Activity a=activity.get();if(a!=null&&!a.isFinishing()&&!a.isDestroyed())try{
                        a.getClass().getMethod("refreshAppList",String.class,String.class,Boolean.class).invoke(a,Intent.ACTION_PACKAGE_REMOVED,pkg,Boolean.FALSE);
                    }catch(Exception e){Log.w(TAG,"Confirmed removal: refresh",e);}
                });
                Log.i(TAG,"Confirmed removed package="+pkg);
            },"PicoLibrary-UninstallResult").start();
        }
    }
}
