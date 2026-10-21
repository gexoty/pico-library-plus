package com.gexoty.picolibrary;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.content.pm.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** A separate launcher preferences page; all state belongs to this package. */
public final class LauncherSettingsActivity extends Activity {
    private LinearLayout content,sectionBody;
    public void refresh(){page();}
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setFlags(1024,1024);page();}
    private void page() {
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(32,16,32,16);
        root.setBackgroundResource(LauncherSettings.resource(this,"drawable","layout_bg"));
        LinearLayout title=new LinearLayout(this);title.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading=LauncherSettings.nativeText(this,"Настройки библиотеки");heading.setTextSize(24);heading.setTextColor(0xffeeeeee);
        title.addView(heading,new LinearLayout.LayoutParams(0,72,1));
        TextView close=LauncherSettings.nativeButton(this,"Готово");close.setOnClickListener(v->finish());title.addView(close,new LinearLayout.LayoutParams(-2,64));root.addView(title);
        ScrollView scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(1);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        section("Доступ Root");boolean rootGranted=LauncherSettings.prefs(this).getBoolean("root_confirmed",false);
        note(rootGranted?"✓ Root предоставлен. Функции с повышенными правами используют Root.":"⚠ Root не предоставлен. Остановка приложений и чтение каталога обновлений могут быть недоступны.");
        row(rootGranted?"Проверить Root":"Предоставить Root",()->RootAccess.request(this));
        section("Приложения");row("Добавить ярлык по имени пакета",()->LibraryExtensions.addShortcut(this));row("Сохранённые ярлыки",()->LibraryExtensions.manageShortcuts(this));
        row("Скрытые значки · "+LauncherSettings.prefs(this).getStringSet("hidden_apps",Collections.emptySet()).size(),this::hidden);
        section("Мои группы");row("Создать группу",()->editName(null));
        for(Map.Entry<String,String> group:LauncherSettings.groups(this).entrySet())row(group.getValue()+" · "+LauncherSettings.prefs(this).getStringSet(group.getKey(),Collections.emptySet()).size(),()->groupActions(group.getKey()));
        section("Остановка приложений");row("Способ остановки: "+backendLabel(),()->LibraryExtensions.chooseBackend(this));
        section("Обновления PICO Store");String source=LauncherSettings.prefs(this).getString("catalog_source","pico");
        row("Источник: "+("root".equals(source)?"PICO Store (Root, только чтение)":"PICO API"),this::catalogSource);
        note("Root читает сведения о версиях непосредственно из локального каталога PICO Store. Штатная библиотека и токены аккаунта не нужны. Свежесть сведений зависит от обновления каталога магазином; лицензии и доступность обновления проверяет сам магазин.");
        row("Открыть PICO Store",()->{try{Intent intent=getPackageManager().getLaunchIntentForPackage("com.picovr.store");if(intent==null)throw new ActivityNotFoundException();startActivity(intent);}catch(Exception e){message("PICO Store недоступен");}});
        row("Перечитать сведения об обновлениях",()->{LauncherSettings.dirty(this);finish();});
        note(LauncherSettings.prefs(this).getString("catalog_status","Сведения ещё не запрашивались"));
        long read=LauncherSettings.prefs(this).getLong("catalog_read_time",0);if(read>0)note("Прочитано: "+java.text.DateFormat.getDateTimeInstance().format(new Date(read)));
        section("Отображение");row("Сортировка приложений",this::sort);row("Вид карточек",this::viewStyle);
    }
    private String backendLabel(){String key=LauncherSettings.prefs(this).getString("backend","system");return "root".equals(key)?"Root":"shizuku".equals(key)?"Shizuku":"Системные права";}
    private void section(String name){sectionBody=new LinearLayout(this);sectionBody.setOrientation(1);sectionBody.setPadding(20,16,20,16);sectionBody.setBackgroundResource(LauncherSettings.resource(this,"drawable","gexoty_settings_card"));LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.topMargin=16;content.addView(sectionBody,layout);TextView t=LauncherSettings.nativeText(this,name);t.setTextSize(20);t.setTypeface(t.getTypeface(),android.graphics.Typeface.BOLD);t.setTextColor(0xffeeeeee);t.setPadding(0,0,0,10);sectionBody.addView(t);}
    private void note(String text){TextView t=LauncherSettings.nativeText(this,text);t.setTextSize(14);t.setTextColor(text.startsWith("⚠")?0xffffbc66:0xffb4b4b4);t.setPadding(4,8,4,12);sectionBody.addView(t);}
    private void row(String text,Runnable action){TextView t=(TextView)LayoutInflater.from(this).inflate(LauncherSettings.resource(this,"layout","gexoty_settings_button"),sectionBody,false);t.setText(text);t.setOnClickListener(v->action.run());LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,64);layout.topMargin=8;sectionBody.addView(t,layout);}
    private void message(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private void catalogSource(){String[] keys={"pico","root"};int current="root".equals(LauncherSettings.prefs(this).getString("catalog_source","pico"))?1:0;
        new AlertDialog.Builder(this).setTitle("Источник сведений об обновлениях").setSingleChoiceItems(new String[]{"PICO API (права текущего приложения)","Root · локальный каталог PICO Store, только чтение"},current,(d,i)->{LauncherSettings.prefs(this).edit().putString("catalog_source",keys[i]).remove("catalog_status").apply();LauncherSettings.dirty(this);d.dismiss();page();}).setNegativeButton("Отмена",null).show();}
    private void editName(String id){EditText input=new EditText(this);input.setSingleLine(true);if(id!=null)input.setText(LauncherSettings.groups(this).get(id));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(id==null?"Новая группа":"Имя группы").setView(input).setPositiveButton("Сохранить",null).setNegativeButton("Отмена",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{String name=input.getText().toString().trim();if(!CatalogRules.validGroupName(name)){input.setError("Введите имя длиной от 1 до 40 символов");return;}LinkedHashMap<String,String> groups=LauncherSettings.groups(this);for(Map.Entry<String,String> e:groups.entrySet())if(!e.getKey().equals(id)&&e.getValue().equalsIgnoreCase(name)){input.setError("Такая группа уже есть");return;}groups.put(id==null?LauncherSettings.newGroupId():id,name);LauncherSettings.saveGroups(this,groups);dialog.dismiss();page();}));dialog.show();}
    private void groupActions(String id){new AlertDialog.Builder(this).setTitle(LauncherSettings.groups(this).get(id)).setItems(new String[]{"Выбрать приложения","Переименовать","Удалить группу"},(d,i)->{if(i==0)membership(id);else if(i==1)editName(id);else new AlertDialog.Builder(this).setTitle("Удалить группу?").setMessage("Установленные приложения останутся на месте.").setPositiveButton("Удалить",(x,w)->{LinkedHashMap<String,String> groups=LauncherSettings.groups(this);groups.remove(id);LauncherSettings.saveGroups(this,groups);LauncherSettings.prefs(this).edit().remove(id).apply();SharedPreferences old=getSharedPreferences("sp_file",0);if(id.equals(old.getString("source_key","all")))old.edit().putString("source_key","all").apply();page();}).setNegativeButton("Отмена",null).show();}).show();}
    private LinkedHashMap<String,String> launchable(){TreeMap<String,String> sorted=new TreeMap<>();PackageManager pm=getPackageManager();for(String category:new String[]{Intent.CATEGORY_LAUNCHER,Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_INFO})for(ResolveInfo ri:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),PackageManager.MATCH_ALL)){ActivityInfo ai=ri.activityInfo;if(ai!=null&&ai.exported&&ai.enabled&&ai.applicationInfo.enabled&&!ai.packageName.equals(getPackageName()))sorted.put(ai.packageName,ri.loadLabel(pm).toString());}List<Map.Entry<String,String>> rows=new ArrayList<>(sorted.entrySet());rows.sort((a,b)->a.getValue().compareToIgnoreCase(b.getValue()));LinkedHashMap<String,String> result=new LinkedHashMap<>();for(Map.Entry<String,String> row:rows)result.put(row.getKey(),row.getValue());return result;}
    private void membership(String id){LinkedHashMap<String,String> apps=launchable();String[] pkgs=apps.keySet().toArray(new String[0]);String[] labels=new String[pkgs.length];boolean[] checked=new boolean[pkgs.length];Set<String> original=new HashSet<>(LauncherSettings.prefs(this).getStringSet(id,Collections.emptySet()));for(int i=0;i<pkgs.length;i++){labels[i]=apps.get(pkgs[i])+"\n"+pkgs[i];checked[i]=original.contains(pkgs[i]);}
        new AlertDialog.Builder(this).setTitle("Приложения группы").setMultiChoiceItems(labels,checked,(d,i,on)->checked[i]=on).setPositiveButton("Сохранить",(d,w)->{Set<String> set=new HashSet<>(original);for(int i=0;i<pkgs.length;i++){if(checked[i])set.add(pkgs[i]);else set.remove(pkgs[i]);}LauncherSettings.prefs(this).edit().putStringSet(id,set).apply();LauncherSettings.dirty(this);page();}).setNegativeButton("Отмена",null).show();}
    private void hidden(){Set<String> hidden=new HashSet<>(LauncherSettings.prefs(this).getStringSet("hidden_apps",Collections.emptySet()));if(hidden.isEmpty()){message("Скрытых значков нет. Скрыть значок можно через меню карточки.");return;}List<String> pkgs=new ArrayList<>(hidden);Collections.sort(pkgs);String[] labels=new String[pkgs.size()];boolean[] checked=new boolean[pkgs.size()];Arrays.fill(checked,true);for(int i=0;i<pkgs.size();i++){String pkg=pkgs.get(i);try{labels[i]=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0))+"\n"+pkg;}catch(Exception e){labels[i]=pkg;}}
        new AlertDialog.Builder(this).setTitle("Скрытые значки · снимите отметку, чтобы вернуть").setMultiChoiceItems(labels,checked,(d,i,on)->checked[i]=on).setPositiveButton("Сохранить",(d,w)->{Set<String> set=new HashSet<>();for(int i=0;i<pkgs.size();i++)if(checked[i])set.add(pkgs.get(i));LauncherSettings.prefs(this).edit().putStringSet("hidden_apps",set).apply();LauncherSettings.dirty(this);page();}).setNegativeButton("Отмена",null).show();}
    private void sort(){String[] keys={"default","recent","name","name_reverse"};String key=getSharedPreferences("sp_file",0).getString("sort_key","recent");new AlertDialog.Builder(this).setTitle("Сортировка").setSingleChoiceItems(new String[]{"По умолчанию","Недавно запущенные","По имени А–Я","По имени Я–А"},Arrays.asList(keys).indexOf(key),(d,i)->{getSharedPreferences("sp_file",0).edit().putString("sort_key",keys[i]).apply();LauncherSettings.dirty(this);d.dismiss();}).setNegativeButton("Отмена",null).show();}
    private void viewStyle(){int current=getSharedPreferences("sp_file",0).getInt("style_key",1);new AlertDialog.Builder(this).setTitle("Вид карточек").setSingleChoiceItems(new String[]{"Значки","Обложки","Список"},current-1,(d,i)->{getSharedPreferences("sp_file",0).edit().putInt("style_key",i+1).apply();LauncherSettings.dirty(this);d.dismiss();}).setNegativeButton("Отмена",null).show();}
    @Override public void onResume(){super.onResume();if(content!=null)page();}
}
