package com.gexoty.picolibrary;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
import java.lang.ref.WeakReference;

/** Root is requested explicitly once on first launch; denial never changes identity. */
public final class RootAccess {
    private static boolean checking;
    private static WeakReference<Activity> launcher=new WeakReference<>(null);
    private RootAccess() {}
    public static void onLaunch(Activity a) {
        launcher=new WeakReference<>(a);
        if(!LauncherSettings.prefs(a).getBoolean("root_onboarding_v1",false))request(a);
    }
    public static boolean isChecking(){return checking;}
    public static void request(Activity a) {
        if(checking)return;checking=true;
        SharedPreferences prefs=LauncherSettings.prefs(a);
        prefs.edit().putBoolean("root_onboarding_v1",true).putString("backend","root").putString("catalog_source","root").apply();
        new Thread(()->{
            boolean granted=false;
            try {
                Process process=new ProcessBuilder("su","-c","id").redirectErrorStream(true).start();
                if(process.waitFor(30,TimeUnit.SECONDS)) {
                    byte[] bytes=new byte[2048];int n=process.getInputStream().read(bytes);
                    granted=process.exitValue()==0 && n>0 && new String(bytes,0,n,StandardCharsets.UTF_8).contains("uid=0(");
                } else process.destroyForcibly();
            }catch(Exception ignored){}
            boolean success=granted;
            prefs.edit().putBoolean("root_confirmed",success).putString("root_status",success?"Root предоставлен":"Root не предоставлен").apply();
            new Handler(Looper.getMainLooper()).post(()->{
                checking=false;
                Toast.makeText(a,success?"Root предоставлен. Остановка и чтение каталога PICO Store включены.":"Root не предоставлен. Часть функций, включая остановку и сведения об обновлениях, недоступна.",Toast.LENGTH_LONG).show();
                if(a instanceof LauncherSettingsActivity&&!a.isFinishing()&&!a.isDestroyed()) {
                    LauncherSettings.dirty(a);((LauncherSettingsActivity)a).refresh();
                } else {
                    Activity target=launcher.get();
                    if(target!=null&&!target.isFinishing()&&!target.isDestroyed()){prefs.edit().putBoolean("reload_launcher",false).apply();target.recreate();}
                }
            });
        },"PicoLibrary-RootRequest").start();
    }
}
