package com.gexoty.picolibrary.tests;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.view.inspector.WindowInspector;
import com.gexoty.picolibrary.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Explicit device tests. Never presses update/install/force-stop or changes Android permissions. */
public final class LauncherInstrumentation extends Instrumentation {
    private final StringBuilder report=new StringBuilder();
    private Activity main;
    private Runnable cleanup;
    private boolean captureOnly;
    @Override public void finish(int code,Bundle result){if(cleanup!=null){cleanup.run();cleanup=null;}super.finish(code,result);}
    private void check(boolean ok,String text){if(!ok)throw new AssertionError(text);report.append("PASS ").append(text).append('\n');}
    @Override public void onCreate(Bundle args){super.onCreate(args);captureOnly=args!=null&&"capture".equals(args.getString("mode"));start();}
    private Object call(Object o,String name) throws Exception{Class<?> type=o.getClass();while(type!=null){try{Method method=type.getDeclaredMethod(name);method.setAccessible(true);return method.invoke(o);}catch(NoSuchMethodException e){type=type.getSuperclass();}}throw new NoSuchMethodException(name);}
    private Object field(Object o,String name) throws Exception {Class<?> type=o.getClass();while(type!=null){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException e){type=type.getSuperclass();}}throw new NoSuchFieldException(name);}
    private List items() throws Exception{return (List)call(field(main,"mAdapter"),"E");}
    private String pkg(Object data) throws Exception{return (String)call(call(data,"getApp"),"v");}
    private TextView text(View root,String value){if(root instanceof TextView && value.contentEquals(((TextView)root).getText()))return (TextView)root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){TextView found=text(((ViewGroup)root).getChildAt(i),value);if(found!=null)return found;}return null;}
    private void screenshot(Activity a,String name) throws Exception {for(int i=0;i<30&&a.getWindow().getDecorView().getWidth()==0;i++)Thread.sleep(100);Exception[] error={null};runOnMainSync(()->{try{View view=a.getWindow().getDecorView();Bitmap b=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(b));try(FileOutputStream out=new FileOutputStream(new File(a.getExternalFilesDir(null),name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}catch(Exception e){error[0]=e;}});if(error[0]!=null)throw error[0];}
    private View globalText(String label){List<View> roots=WindowInspector.getGlobalWindowViews();for(int i=roots.size()-1;i>=0;i--){View found=text(roots.get(i),label);if(found!=null)return found;}return null;}
    private void click(String label) throws Exception {View view=globalText(label);check(view!=null,"UI control present: "+label);runOnMainSync(()->{if(view.hasOnClickListeners()){view.performClick();return;}ViewParent parent=view.getParent();while(parent instanceof View){if(parent instanceof AdapterView){AdapterView adapter=(AdapterView)parent;int index=adapter.getPositionForView(view);adapter.performItemClick(view,index,adapter.getItemIdAtPosition(index));return;}parent=parent.getParent();}view.performClick();});Thread.sleep(300);}
    private EditText edit(View view){if(view instanceof EditText)return (EditText)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){EditText found=edit(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;}
    private void input(String value) throws Exception {List<View> roots=WindowInspector.getGlobalWindowViews();EditText found=null;for(int i=roots.size()-1;i>=0&&found==null;i--)found=edit(roots.get(i));check(found!=null,"group name editor present");EditText editor=found;runOnMainSync(()->editor.setText(value));}
    private void iconViews(View view,List<View> icons,int id){if(view.getId()==id&&view.getWidth()>0&&view.getHeight()>0&&view.getVisibility()==View.VISIBLE)icons.add(view);if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)iconViews(((ViewGroup)view).getChildAt(i),icons,id);}
    private void masks(){List<View> icons=new ArrayList<>();iconViews(main.getWindow().getDecorView(),icons,LauncherSettings.resource(main,"id","app_icon_logo"));int[] clipped={0};runOnMainSync(()->{for(View view:icons){Bitmap image=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));if(Color.alpha(image.getPixel(0,0))==0&&Color.alpha(image.getPixel(image.getWidth()/2,image.getHeight()/2))>0)clipped[0]++;image.recycle();}});check(icons.size()>5&&clipped[0]==icons.size(),"all visible local icons have transparent rounded corners: "+clipped[0]);}
    @Override public void onStart(){
        if(captureOnly){try{Context c=getTargetContext();LauncherSettings.prefs(c).edit().putBoolean("reload_launcher",false).commit();main=startActivitySync(new Intent().setClassName(c.getPackageName(),"com.pvr.appmanager.AllAppActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(5000);screenshot(main,"launcher-main.png");ActivityMonitor monitor=addMonitor("com.gexoty.picolibrary.LauncherSettingsActivity",null,false);TextView button=text(main.findViewById(LauncherSettings.resource(main,"id","titlebar_right_widgets")),"Настройки");runOnMainSync(button::performClick);Activity settings=waitForMonitorWithTimeout(monitor,5000);screenshot(settings,"launcher-settings.png");runOnMainSync(settings::finish);finish(Activity.RESULT_OK,new Bundle());}catch(Exception e){Bundle result=new Bundle();result.putString("failure",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}return;}
        Context context=getTargetContext();android.content.SharedPreferences prefs=LauncherSettings.prefs(context);
        LinkedHashMap<String,String> original=LauncherSettings.groups(context);
        // Remove only residue from a previous interrupted test run.
        boolean residue=false;
        for(String key:new ArrayList<>(original.keySet()))if("Проверка групп".equals(original.get(key))){original.remove(key);prefs.edit().remove(key).commit();residue=true;}
        if(residue){Set<String> reset=new HashSet<>(prefs.getStringSet("hidden_apps",Collections.emptySet()));reset.remove("VirtualDesktop.Android");prefs.edit().putStringSet("hidden_apps",reset).commit();}
        LauncherSettings.saveGroups(context,original);
        Set<String> hidden=new HashSet<>(prefs.getStringSet("hidden_apps",Collections.emptySet()));
        String originalTab=context.getSharedPreferences("sp_file",0).getString("source_key","all");
        String id=LauncherSettings.newGroupId();
        cleanup=()->{LauncherSettings.saveGroups(context,original);prefs.edit().remove(id).putStringSet("hidden_apps",hidden).putBoolean("reload_launcher",true).commit();context.getSharedPreferences("sp_file",0).edit().putString("source_key",originalTab.startsWith("gexoty_group_")&&!original.containsKey(originalTab)?"all":originalTab).commit();};
        try {
            // Explicitly select and report the read-only Root integration under test.
            prefs.edit().putString("catalog_source","root").putBoolean("reload_launcher",false).commit();
            LinkedHashMap<String,String> groups=new LinkedHashMap<>(original);groups.put(id,"Проверка групп");LauncherSettings.saveGroups(context,groups);
            Set<String> members=new HashSet<>(Arrays.asList("com.vrchat.android","VirtualDesktop.Android"));
            Set<String> testHidden=new HashSet<>(hidden);testHidden.removeAll(members);
            prefs.edit().putStringSet(id,members).putStringSet("hidden_apps",testHidden).putBoolean("reload_launcher",false).commit();
            context.getSharedPreferences("sp_file",0).edit().putString("source_key","all").commit();
            Intent launch=new Intent().setClassName(context.getPackageName(),"com.pvr.appmanager.AllAppActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            main=startActivitySync(launch);Thread.sleep(12000);
            check(main!=null && !main.isFinishing(),"launcher started");
            check(prefs.getBoolean("root_onboarding_v1",false)&&prefs.getBoolean("root_confirmed",false),"first-launch Root request completed successfully");
            check(prefs.getString("catalog_status","").startsWith("Каталог PICO Store:"),"read-only PICO Store source succeeded: "+prefs.getString("catalog_status",""));
            List all=(List)call(main,"getMAllApp");int found=0;
            for(Object data:all)if(members.contains(pkg(data))) {
                long installed=((Number)call(data,"getVersion")).longValue(),available=((Number)call(data,"getStoreVersionCode")).longValue();
                check(available>installed,pkg(data)+" update metadata: installed="+installed+", store="+available);found++;
            }
            check(found==2,"both reference applications found; no update requested");
            masks();screenshot(main,"launcher-main.png");
            View updateNav=main.findViewById(LauncherSettings.resource(main,"id","mAppManiMenuTab"));View updates=updateNav.findViewWithTag("update");runOnMainSync(updates::performClick);Thread.sleep(700);
            Object updateAdapter=call(call(main,"getMRecyclerView"),"getAdapter");check(updateAdapter.getClass().getName().endsWith(".k0")&&((Number)call(updateAdapter,"getItemCount")).intValue()>2,"updates section displays available updates");screenshot(main,"launcher-updates.png");
            View nav=main.findViewById(LauncherSettings.resource(main,"id","mAppManiMenuTab"));check(nav.findViewWithTag("not_installed")==null,"not-installed section removed");View group=nav.findViewWithTag(id);check(group!=null,"native group row present");runOnMainSync(group::performClick);
            Thread.sleep(1200);check(items().size()==2,"custom group contains exactly its two members");
            screenshot(main,"launcher-group.png");
            testHidden.add("VirtualDesktop.Android");prefs.edit().putStringSet("hidden_apps",testHidden).commit();
            runOnMainSync(()->{try{Method method=main.getClass().getDeclaredMethod("changeRecyclerViewType",String.class);method.setAccessible(true);method.invoke(main,id);}catch(Exception e){throw new RuntimeException(e);}});
            Thread.sleep(500);check(items().size()==1 && "com.vrchat.android".equals(pkg(items().get(0))),"hidden icon excluded from custom group");
            check(((List)call(main,"getMAllApp")).size()==all.size(),"hiding preserves inventory and installed applications");
            prefs.edit().putStringSet("hidden_apps",hidden).commit();
            ActivityMonitor monitor=addMonitor("com.gexoty.picolibrary.LauncherSettingsActivity",null,false);
            TextView button=text(main.findViewById(LauncherSettings.resource(main,"id","titlebar_right_widgets")),"Настройки");check(button!=null && button.getClass().getName().contains("OSUIButton"),"settings button uses native OSUI widget");runOnMainSync(button::performClick);
            Activity settings=waitForMonitorWithTimeout(monitor,5000);check(settings!=null,"separate settings page opens");
            check(text(settings.getWindow().getDecorView(),"Настройки библиотеки")!=null,"settings page title present");
            screenshot(settings,"launcher-settings.png");
            click("Создать группу");input("Тест интерфейса");click("Сохранить");check(LauncherSettings.groups(context).containsValue("Тест интерфейса"),"group created through settings UI");
            click("Тест интерфейса · 0");click("Переименовать");input("Переименованная группа");click("Сохранить");check(LauncherSettings.groups(context).containsValue("Переименованная группа"),"group renamed through settings UI");
            click("Переименованная группа · 0");click("Удалить группу");click("Удалить");check(!LauncherSettings.groups(context).containsValue("Переименованная группа"),"group deleted through settings UI");
            runOnMainSync(settings::finish);Thread.sleep(1000);
            // Compare real menu beans with stock delete styling without invoking either action.
            Object sample=null;for(Object data:all)if("com.vrchat.android".equals(pkg(data)))sample=data;
            List menus=LibraryExtensions.extendMenus(Collections.emptyList(),sample,0);Object stop=null;
            for(Object bean:menus)if("gexoty_force_stop".equals(call(bean,"getKey")))stop=bean;
            check(stop!=null,"stop menu item present");
            check(((Number)call(stop,"getColor")).intValue()==LauncherSettings.color(main,"red_btn_normal",0),"stop uses exact stock delete color");
            check("Стоп".equals(main.getString(((Number)call(stop,"getValue")).intValue())),"short stop label");
            prefs.edit().putBoolean("root_confirmed",false).commit();
            check(LibraryIntegrations.selectedCatalog().length()==0&&"root".equals(prefs.getString("catalog_source","")),"simulated Root denial does not fall back to another source");
            prefs.edit().putBoolean("root_confirmed",true).commit();LibraryIntegrations.selectedCatalog();
            report.append("Permission screen request follows; host must verify resumed component.\n");
            runOnMainSync(()->LibraryIntegrations.openPermissions(context,"com.vrchat.android","VRChat"));Thread.sleep(2000);
            Bundle result=new Bundle();result.putString("report",report.toString());finish(Activity.RESULT_OK,result);
        }catch(Throwable e){Bundle result=new Bundle();result.putString("report",report.toString());result.putString("failure",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
        finally {
            LauncherSettings.saveGroups(context,original);prefs.edit().remove(id).putStringSet("hidden_apps",hidden).putBoolean("reload_launcher",true).commit();
            context.getSharedPreferences("sp_file",0).edit().putString("source_key",originalTab.startsWith("gexoty_group_")&&!original.containsKey(originalTab)?"all":originalTab).commit();
        }
    }
}
