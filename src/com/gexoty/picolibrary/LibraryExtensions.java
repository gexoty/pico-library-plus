package com.gexoty.picolibrary;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import rikka.shizuku.Shizuku;

/** Version-pinned adapters for AppManager 2.9.5. No Xposed dependency. */
public final class LibraryExtensions {
    private static final String TAG="PicoLibrary", FORCE_STOP="gexoty_force_stop";
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static volatile boolean trackerDenied;
    private LibraryExtensions() {}
    /** Vendor telemetry is optional for a separately signed library. */
    public static boolean bindTracker(Context context,Intent intent,ServiceConnection connection,int flags) {
        if(trackerDenied)return false;
        try { return context.bindService(intent,connection,flags); }
        catch(SecurityException e) {
            trackerDenied=true;
            Log.w(TAG,"TeaTracker unavailable to this package; continuing without telemetry",e);
            return false;
        }
    }
    private static Object call(Object o,String name) throws Exception {
        return o.getClass().getMethod(name).invoke(o);
    }
    private static Context app() throws Exception {
        return (Context)Class.forName("com.pvr.appmanager.AppApplication").getMethod("d").invoke(null);
    }
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("gexoty_library",0); }
    private static void failed(String operation,Throwable e) { Log.e(TAG,operation,e); }
    private static void toast(Context c,String text) { MAIN.post(()->Toast.makeText(c,text,Toast.LENGTH_LONG).show()); }
    public static String normalTab(String tag) { return "unknown".equals(tag)?"app":"not_installed".equals(tag)?"all":tag; }
    public static int categoryIndex(String tag) {
        tag=normalTab(tag);
        if(tag!=null && tag.startsWith("gexoty_group_"))return LauncherSettings.groupIndex(tag);
        if("app".equals(tag))return 1;if("game".equals(tag))return 2;
        if("update".equals(tag))return 3;return 0;
    }
    public static void hideUnknown(View view) {
        if("unknown".equals(view.getTag())) { view.setVisibility(View.GONE);return; }
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++)
            hideUnknown(((ViewGroup)view).getChildAt(i));
    }
    public static void normalize(Object data) {
        try {
            if("other".equals(call(data,"getSource")) && "sdk_app".equals(call(data,"getSubSource")))
                data.getClass().getMethod("setSubSource",String.class).invoke(data,"2d_app");
        } catch(Exception e) { failed("normalize",e); }
    }
    public static List extendMenus(List original,Object data,int pos) {
        try {
            Object item=call(data,"getApp");
            if(Boolean.TRUE.equals(call(item,"C")) || Boolean.TRUE.equals(call(item,"z"))) return original;
            String pkg=(String)call(item,"v"); Context c=app();
            c.getPackageManager().getApplicationInfo(pkg,0);
            int label=c.getResources().getIdentifier("gexoty_force_stop","string",c.getPackageName());
            if(label==0) throw new IllegalStateException("Missing force-stop label");
            List copy=new ArrayList(original);
            Constructor<?> ctor=Class.forName("com.pvr.appmanager.utils.menu.MenuBean").getConstructor(String.class,int.class,int.class,Object.class,int.class);
            if(StopPolicy.mayStop(pkg,c.getPackageName()))copy.add(ctor.newInstance(FORCE_STOP,label,LauncherSettings.color(c,"red_btn_normal",0xffdd434d),data,pos));
            int normal=LauncherSettings.color(c,"menu_text_color",0xffdddddd);
            copy.add(ctor.newInstance("gexoty_groups",LauncherSettings.resource(c,"string","gexoty_groups"),normal,data,pos));
            copy.add(ctor.newInstance("gexoty_hide",LauncherSettings.resource(c,"string","gexoty_hide"),normal,data,pos));
            return copy;
        } catch(Exception e) { failed("extendMenus",e);return original; }
    }
    public static void bindMenu(View view,Object bean,View.OnClickListener original) {
        view.setOnClickListener(v->{
            try {
                String key=(String)call(bean,"getKey");
                if(FORCE_STOP.equals(key) || "gexoty_groups".equals(key) || "gexoty_hide".equals(key)) {
                    Object target=call(call(bean,"getAppData"),"getApp");
                    String pkg=(String)call(target,"v");
                    dismissMenu(original);
                    if(FORCE_STOP.equals(key))forceStop(v.getContext(),pkg);
                    else if(v.getContext() instanceof Activity){if("gexoty_groups".equals(key))LauncherSettings.assignGroups((Activity)v.getContext(),pkg);else LauncherSettings.hide((Activity)v.getContext(),pkg);}
                    return;
                }
            } catch(Exception e) { failed("menu",e);toast(v.getContext(),"Не удалось определить выбранное приложение");return; }
            original.onClick(v);
        });
    }
    private static void dismissMenu(Object listener) {
        for(Class<?> type=listener.getClass();type!=null;type=type.getSuperclass())for(Field field:type.getDeclaredFields())
            if(PopupWindow.class.isAssignableFrom(field.getType()))try{field.setAccessible(true);PopupWindow popup=(PopupWindow)field.get(listener);if(popup!=null)popup.dismiss();}catch(Exception e){failed("dismiss menu",e);}
    }
    private static Object newData(ResolveInfo ri) throws Exception {
        Class<?> item=Class.forName("com.pvr.appmanager.b0.b.a");
        Object appItem=null;
        for(Constructor<?> c:item.getConstructors()) if(c.getParameterCount()==3)
            appItem=c.newInstance(ri,null,"");
        if(appItem==null) throw new NoSuchMethodException("AppResolveInfo constructor");
        Class<?> data=Class.forName("com.pvr.appmanager.entity.AppData");
        for(Constructor<?> ctor:data.getConstructors()) if(ctor.getParameterCount()==21) {
            Class<?>[] types=ctor.getParameterTypes();Object[] args=new Object[types.length];args[0]=appItem;
            for(int i=1;i<types.length;i++) args[i]=types[i]==String.class?"":types[i]==long.class?Long.valueOf(0):types[i]==boolean.class?Boolean.TRUE:Integer.valueOf(0);
            Object result=ctor.newInstance(args);
            Class.forName("com.pvr.appmanager.utils.Utils").getMethod("initAppData",data).invoke(null,result);
            Object filter=Class.forName("com.pvr.appmanager.Filter").getConstructor().newInstance();
            filter.getClass().getMethod("judgeSourceType",data).invoke(filter,result);
            return result;
        }
        throw new NoSuchMethodException("AppData constructor");
    }
    /** Union MAIN/LAUNCHER, LEANBACK and INFO, plus explicitly saved launchable packages. */
    public static List mergeInventory(List original) {
        try {
            Context c=app();PackageManager pm=c.getPackageManager();
            Set<String> ban=new HashSet<>();
            // Keep parental restrictions separate from business presentation filters.
            Class<?> utils=Class.forName("com.pvr.appmanager.utils.Utils");
            Object instance=utils.getField("INSTANCE").get(null);
            if(Boolean.TRUE.equals(utils.getMethod("isParentalCtrlEnabled").invoke(instance)))
                ban.addAll((List<String>)utils.getMethod("getBanAppList").invoke(instance));
            List result=new ArrayList();Set<String> seen=new HashSet<>();
            for(Object data:original) {
                String pkg=(String)call(call(data,"getApp"),"v");
                if(!pkg.equals(c.getPackageName()) && !ban.contains(pkg)) { result.add(data);seen.add(pkg); }
            }
            List<ResolveInfo> all=new ArrayList<>();
            for(String category:new String[]{Intent.CATEGORY_LAUNCHER,Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_INFO})
                all.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),PackageManager.MATCH_ALL));
            for(String pkg:prefs(c).getStringSet("shortcuts",Collections.emptySet())) {
                Intent intent=pm.getLeanbackLaunchIntentForPackage(pkg);
                if(intent==null) intent=pm.getLaunchIntentForPackage(pkg);
                if(intent!=null) { ResolveInfo ri=pm.resolveActivity(intent,0);if(ri!=null)all.add(ri); }
            }
            for(ResolveInfo ri:all) {
                ActivityInfo ai=ri.activityInfo;
                if(ai==null || !ai.exported || !ai.enabled || !ai.applicationInfo.enabled || ai.packageName.equals(c.getPackageName()) || ban.contains(ai.packageName) || seen.contains(ai.packageName)) continue;
                try { result.add(newData(ri));seen.add(ai.packageName); }
                catch(Exception e) { failed("inventory: "+ai.packageName,e); }
            }
            return result;
        } catch(Exception e) { failed("mergeInventory",e);return original; }
    }
    public static void attach(Activity a) {
        try {
            SharedPreferences old=a.getSharedPreferences("sp_file",0);
            if("unknown".equals(old.getString("source_key","all")))old.edit().putString("source_key","app").apply();
            if("not_installed".equals(old.getString("source_key","all")))old.edit().putString("source_key","all").apply();
            int id=a.getResources().getIdentifier("titlebar_right_widgets","id",a.getPackageName());
            ViewGroup bar=a.findViewById(id);if(bar==null)throw new IllegalStateException("No title bar");
            TextView b=LauncherSettings.nativeButton(a,"Настройки");b.setOnClickListener(v->LauncherSettings.open(a));
            LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-2,-2);layout.setMarginEnd(LauncherSettings.dim(a,"margin_8",10));
            bar.addView(b,0,layout);LauncherSettings.attachNavigation(a);RootAccess.onLaunch(a);
        } catch(Exception e) { failed("attach",e); }
    }
    public static void chooseBackend(Activity a) {
        String[] keys={"system","root","shizuku"};String selected=prefs(a).getString("backend","system");
        int index=Arrays.asList(keys).indexOf(selected);
        new AlertDialog.Builder(a).setTitle("Принудительная остановка")
            .setSingleChoiceItems(new String[]{"Системные права (FORCE_STOP_PACKAGES)","Root (su)","Shizuku"},index,(d,which)->{
                prefs(a).edit().putString("backend",keys[which]).apply();d.dismiss();
                if(a instanceof LauncherSettingsActivity)((LauncherSettingsActivity)a).refresh();
                if(which==2) {try {if(!Shizuku.pingBinder())toast(a,"Сначала запустите Shizuku");
                    else if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED)Shizuku.requestPermission(491);
                }catch(Exception e){toast(a,"Shizuku недоступен: "+e.getMessage());}}
            }).setNegativeButton("Закрыть",null).show();
    }
    public static void addShortcut(Activity a) {
        EditText input=new EditText(a);input.setSingleLine(true);input.setHint("com.example.app");
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Добавить установленное приложение")
            .setView(input).setPositiveButton("Добавить",null).setNegativeButton("Отмена",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            String pkg=input.getText().toString().trim();
            if(!StopPolicy.validPackage(pkg)){input.setError("Некорректное имя пакета");return;}
            try {
                a.getPackageManager().getApplicationInfo(pkg,0);
                Intent launch=a.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                if(launch==null)launch=a.getPackageManager().getLaunchIntentForPackage(pkg);
                if(launch==null)throw new IllegalArgumentException("У пакета нет доступной запускаемой Activity");
                ResolveInfo ri=a.getPackageManager().resolveActivity(launch,0);
                if(ri==null || !ri.activityInfo.exported || !ri.activityInfo.enabled)throw new IllegalArgumentException("Activity недоступна");
                Set<String> pins=new HashSet<>(prefs(a).getStringSet("shortcuts",Collections.emptySet()));pins.add(pkg);
                if(!prefs(a).edit().putStringSet("shortcuts",pins).commit())throw new IllegalStateException("Не удалось сохранить ярлык");
                LauncherSettings.dirty(a);dialog.dismiss();a.recreate();
            } catch(Exception e) {input.setError(e instanceof PackageManager.NameNotFoundException?"Приложение не установлено":e.getMessage());}
        }));dialog.show();
    }
    public static void manageShortcuts(Activity a) {
        List<String> pins=new ArrayList<>(prefs(a).getStringSet("shortcuts",Collections.emptySet()));Collections.sort(pins);
        if(pins.isEmpty()){toast(a,"Добавленных ярлыков пока нет");return;}
        new AlertDialog.Builder(a).setTitle("Удалить сохранённый ярлык (не приложение)")
            .setItems(pins.toArray(new String[0]),(d,index)->{
                Set<String> set=new HashSet<>(pins);set.remove(pins.get(index));prefs(a).edit().putStringSet("shortcuts",set).apply();
                LauncherSettings.dirty(a);toast(a,"Ярлык удалён. Автоматически обнаруженное приложение остаётся в общем списке.");a.recreate();
            }).setNegativeButton("Закрыть",null).show();
    }
    private static void forceStop(Context c,String pkg) {
        if(!StopPolicy.mayStop(pkg,c.getPackageName())){toast(c,"Остановка этого пакета запрещена");return;}
        int user=android.os.Process.myUid()/100000;String backend=prefs(c).getString("backend","system");
        if("shizuku".equals(backend)){shizukuStop(c,pkg,user);return;}
        WORK.execute(()->{
            try {
                c.getPackageManager().getApplicationInfo(pkg,0);
                if("system".equals(backend)) {
                    if(c.checkSelfPermission("android.permission.FORCE_STOP_PACKAGES")!=PackageManager.PERMISSION_GRANTED)
                        throw new SecurityException("FORCE_STOP_PACKAGES не выдано. Выберите Root или Shizuku.");
                    Object am=c.getSystemService(Context.ACTIVITY_SERVICE);
                    am.getClass().getMethod("forceStopPackage",String.class).invoke(am,pkg);
                } else if("root".equals(backend)) {
                    if(!prefs(c).getBoolean("root_confirmed",false))throw new SecurityException("Предоставьте Root в настройках библиотеки");
                    java.lang.Process p=new ProcessBuilder("su","-c","/system/bin/am force-stop --user "+user+" "+pkg).redirectErrorStream(true).start();
                    if(!p.waitFor(15,TimeUnit.SECONDS)){p.destroyForcibly();throw new Exception("Истекло время ожидания su");}
                    byte[] buf=new byte[8192];int n=p.getInputStream().read(buf);
                    String output=n>0?new String(buf,0,n,java.nio.charset.StandardCharsets.UTF_8):"";
                    if(p.exitValue()!=0 || !output.trim().isEmpty())throw new Exception("su/am: "+output);
                } else throw new IllegalStateException("Неизвестный способ остановки");
                toast(c,"Команда force-stop выполнена: "+pkg);
            }catch(Exception e){failed("force-stop: "+pkg,e);toast(c,"Остановить не удалось: "+e.getMessage());}
        });
    }
    private static void shizukuStop(Context c,String pkg,int user) {
        try {
            if(!Shizuku.pingBinder())throw new IllegalStateException("Shizuku не запущен");
            if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){Shizuku.requestPermission(491);toast(c,"Разрешите доступ и повторите остановку");return;}
            Shizuku.UserServiceArgs args=new Shizuku.UserServiceArgs(new ComponentName(c,StopUserService.class))
                .daemon(false).processNameSuffix("force_stop").version(1);
            ServiceConnection connection=new ServiceConnection(){
                private boolean done;
                private final Runnable timeout=()->{if(!done){done=true;toast(c,"Shizuku: время ожидания сервиса истекло");try{Shizuku.unbindUserService(args,this,true);}catch(Exception ignored){}}};
                {MAIN.postDelayed(timeout,15000);}
                @Override public void onServiceConnected(ComponentName name,IBinder binder){
                    if(done)return;done=true;MAIN.removeCallbacks(timeout);
                    WORK.execute(()->{Parcel data=Parcel.obtain(),reply=Parcel.obtain();try{
                        data.writeInterfaceToken(StopUserService.DESCRIPTOR);data.writeString(pkg);data.writeInt(user);
                        if(!binder.transact(1,data,reply,0))throw new Exception("Неизвестный Binder протокол");
                        reply.readException();toast(c,reply.readString());
                    }catch(Exception e){failed("Shizuku",e);toast(c,"Shizuku: "+e.getMessage());}
                    finally{data.recycle();reply.recycle();MAIN.post(()->{try{Shizuku.unbindUserService(args,this,true);}catch(Exception e){failed("unbind",e);}});}});
                }
                @Override public void onServiceDisconnected(ComponentName name){if(!done){done=true;MAIN.removeCallbacks(timeout);toast(c,"Shizuku отключён");}}
            };
            Shizuku.bindUserService(args,connection);
        }catch(Exception e){failed("Shizuku bind",e);toast(c,"Shizuku: "+e.getMessage());}
    }
}
