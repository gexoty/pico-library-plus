package com.gexoty.picolibrary;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import android.util.Log;
import android.util.SparseArray;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

public final class LauncherSettings {
    private static final String PREFIX="gexoty_group_";
    private LauncherSettings() {}
    public static SharedPreferences prefs(Context c){return c.getSharedPreferences("gexoty_library",0);}
    public static int resource(Context c,String type,String name){return c.getResources().getIdentifier(name,type,c.getPackageName());}
    public static int dim(Context c,String name,int fallback){int id=resource(c,"dimen",name);return id==0?fallback:c.getResources().getDimensionPixelSize(id);}
    public static int color(Context c,String name,int fallback){int id=resource(c,"color",name);return id==0?fallback:c.getColor(id);}
    public static void open(Activity a){a.startActivity(new Intent(a,LauncherSettingsActivity.class));}
    public static LinkedHashMap<String,String> groups(Context c) {
        LinkedHashMap<String,String> result=new LinkedHashMap<>();
        try {JSONArray rows=new JSONArray(prefs(c).getString("groups_json","[]"));for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);result.put(row.getString("id"),row.getString("name"));}}
        catch(JSONException e){Log.e("PicoLibrary","Read groups",e);}return result;
    }
    public static void saveGroups(Context c,Map<String,String> groups) {
        JSONArray rows=new JSONArray();
        try {for(Map.Entry<String,String> entry:groups.entrySet())rows.put(new JSONObject().put("id",entry.getKey()).put("name",entry.getValue()));}
        catch(JSONException e){throw new IllegalArgumentException(e);}
        prefs(c).edit().putString("groups_json",rows.toString()).putBoolean("reload_launcher",true).apply();
    }
    public static void dirty(Context c){prefs(c).edit().putBoolean("reload_launcher",true).apply();}
    public static void resumed(Activity a) {
        if(RootAccess.isChecking())return;
        if(prefs(a).getBoolean("reload_launcher",false)) {
            prefs(a).edit().putBoolean("reload_launcher",false).apply();
            a.getWindow().getDecorView().post(()->{if(!a.isFinishing())a.recreate();});
        }
    }
    public static String groupName(String tag) {
        if("update".equals(tag))return "Обновления";
        if(tag==null || !tag.startsWith(PREFIX))return null;
        try{return groups(LibraryIntegrations.context()).get(tag);}catch(Exception e){return null;}
    }
    public static String filterType(String tag){return tag!=null && tag.startsWith(PREFIX)?"all":tag;}
    public static int groupIndex(String tag) {
        try {int i=4;for(String key:groups(LibraryIntegrations.context()).keySet()){if(key.equals(tag))return i;i++;}}catch(Exception ignored){}
        return 0;
    }
    public static List filterCatalog(String tag,List input) {
        try {
            Context c=LibraryIntegrations.context();Set<String> hidden=prefs(c).getStringSet("hidden_apps",Collections.emptySet());
            Set<String> members=tag!=null && tag.startsWith(PREFIX)?prefs(c).getStringSet(tag,Collections.emptySet()):null;
            List result=new ArrayList();
            for(Object data:input) {
                Object item=data.getClass().getMethod("getApp").invoke(data);
                String pkg=(String)item.getClass().getMethod("v").invoke(item);
                if(CatalogRules.visible(pkg,hidden,members))result.add(data);
            }
            return result;
        }catch(Exception e){Log.e("PicoLibrary","Local catalog filter",e);return input;}
    }
    public static List visibleCatalog(List input){return filterCatalog(null,input);}
    public static boolean includeApp(Object data) {
        try {Context c=LibraryIntegrations.context();String tag=c.getSharedPreferences("sp_file",0).getString("source_key","all");return !filterCatalog(tag,Collections.singletonList(data)).isEmpty();}
        catch(Exception e){Log.e("PicoLibrary","App presentation policy",e);return false;}
    }
    /** Add native navigation items before the stock builder binds listeners and indexes. */
    public static void extendNavigation(Object builder,View navigation) {
        try {
            Context c=navigation.getContext();int icon=resource(c,"drawable","ic_icon_navi_application");
            Class<?> type=builder.getClass();Field itemsField=null;
            while(type!=null && itemsField==null){try{itemsField=type.getDeclaredField("d");}catch(NoSuchFieldException e){type=type.getSuperclass();}}
            if(itemsField==null)throw new NoSuchFieldException("navigation items");itemsField.setAccessible(true);
            SparseArray items=(SparseArray)itemsField.get(builder);List<View> kept=new ArrayList<>();
            for(int i=0;i<items.size();i++){View item=(View)items.valueAt(i);if(!"not_installed".equals(item.getTag())&&!"unknown".equals(item.getTag()))kept.add(item);}
            items.clear();for(int i=0;i<kept.size();i++)items.put(i,kept.get(i));
            Method create=navigation.getClass().getMethod("n",Drawable.class,CharSequence.class);
            Method add=builder.getClass().getMethod("d",Class.forName("com.bytedance.osui.grouplist.a"));
            for(Map.Entry<String,String> entry:groups(c).entrySet()) {
                View item=(View)create.invoke(navigation,c.getDrawable(icon),entry.getValue());
                item.setTag(entry.getKey());add.invoke(builder,item);
            }
            View settings=(View)create.invoke(navigation,c.getDrawable(resource(c,"drawable","gexoty_ic_settings")),"Настройки");
            settings.setTag("gexoty_settings");add.invoke(builder,settings);
        }catch(Exception e){Log.e("PicoLibrary","Custom navigation",e);}
    }
    public static void attachNavigation(Activity a) throws Exception {
        ViewGroup nav=a.findViewById(resource(a,"id","mAppManiMenuTab"));
        ((TextView)nav.getClass().getMethod("getHeaderTextView").invoke(nav)).setText("Библиотека");
        View updates=nav.findViewWithTag("update");
        if(updates!=null)((TextView)updates.getClass().getMethod("getTextView").invoke(updates)).setText("Обновления");
        View settings=nav.findViewWithTag("gexoty_settings");if(settings!=null)settings.setOnClickListener(v->open(a));
        // Keep any number of custom groups reachable without compressing native rows.
        ViewGroup parent=(ViewGroup)nav.getParent();
        if(!(parent instanceof ScrollView)) {
            int index=parent.indexOfChild(nav);ViewGroup.LayoutParams original=nav.getLayoutParams();
            parent.removeView(nav);ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);scroll.setHorizontalScrollBarEnabled(false);
            scroll.addView(nav,new ScrollView.LayoutParams(-1,-2));parent.addView(scroll,index,original);
        }
    }
    public static TextView nativeButton(Context c,String text) {
        TextView view=(TextView)LayoutInflater.from(c).inflate(resource(c,"layout","gexoty_button"),null,false);
        view.setText(text);return view;
    }
    public static TextView nativeText(Context c,String text) {
        try {TextView view=(TextView)Class.forName("com.bytedance.osui.common.OSUITextView").getConstructor(Context.class).newInstance(c);view.setText(text);return view;}
        catch(Exception e){TextView view=new TextView(c);view.setText(text);return view;}
    }
    public static void hide(Activity a,String pkg) {
        new AlertDialog.Builder(a).setTitle("Скрыть значок?").setMessage("Приложение останется установленным. Вернуть значок можно в настройках библиотеки.")
            .setPositiveButton("Скрыть",(d,w)->{Set<String> set=new HashSet<>(prefs(a).getStringSet("hidden_apps",Collections.emptySet()));set.add(pkg);prefs(a).edit().putStringSet("hidden_apps",set).apply();a.recreate();})
            .setNegativeButton("Отмена",null).show();
    }
    public static void assignGroups(Activity a,String pkg) {
        LinkedHashMap<String,String> groups=groups(a);
        if(groups.isEmpty()){new AlertDialog.Builder(a).setMessage("Создайте группу в настройках библиотеки.").setPositiveButton("Настройки",(d,w)->open(a)).setNegativeButton("Отмена",null).show();return;}
        String[] ids=groups.keySet().toArray(new String[0]);String[] names=groups.values().toArray(new String[0]);boolean[] selected=new boolean[ids.length];
        for(int i=0;i<ids.length;i++)selected[i]=prefs(a).getStringSet(ids[i],Collections.emptySet()).contains(pkg);
        new AlertDialog.Builder(a).setTitle("Группы приложения").setMultiChoiceItems(names,selected,(d,i,on)->selected[i]=on)
            .setPositiveButton("Сохранить",(d,w)->{SharedPreferences.Editor editor=prefs(a).edit();for(int i=0;i<ids.length;i++){Set<String> set=new HashSet<>(prefs(a).getStringSet(ids[i],Collections.emptySet()));if(selected[i])set.add(pkg);else set.remove(pkg);editor.putStringSet(ids[i],set);}editor.apply();a.recreate();})
            .setNegativeButton("Отмена",null).show();
    }
    public static String newGroupId(){return PREFIX+UUID.randomUUID().toString();}
}
