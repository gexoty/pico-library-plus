"""Fail-closed transformations for the exact supplied APK; never rename DEX classes blindly."""
from pathlib import Path
import re,json,hashlib,sys,xml.etree.ElementTree as ET

OLD='com.pvr.appmanager';NEW='com.gexoty.picolibrary'
H='Lcom/gexoty/picolibrary/LibraryExtensions;'
S='Lcom/gexoty/picolibrary/LauncherSettings;'
I='Lcom/gexoty/picolibrary/LibraryIntegrations;'
ANDROID='{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android',ANDROID[1:-1])

def transform(decoded, variant="standalone", version_name=None, version_code=None):
    application_id = OLD if variant == "system" else NEW
    decoded=Path(decoded);changes=[]
    def file(rel):return decoded/'smali'/rel
    def edit(rel,func):
        p=file(rel);before=p.read_text(encoding='utf-8');after=func(before)
        assert after!=before, 'Patch did not change '+rel
        p.write_text(after,encoding='utf-8');changes.append(rel)
    def method(text,sig,func):
        pat=r'(?m)^\.method [^\n]* '+re.escape(sig)+r'\n.*?^\.end method'
        matches=list(re.finditer(pat,text,re.S|re.M));assert len(matches)==1,('method count',sig,len(matches))
        m=matches[0];return text[:m.start()]+func(m.group())+text[m.end():]
    def body(text,sig,body,locals=1):
        return method(text,sig,lambda m:m.split('\n',1)[0]+f'\n    .locals {locals}\n'+body+'\n.end method')
    def after_directives(m,code):
        # All insertions in this build target methods without leading try/debug directives.
        return re.sub(r'(    \.locals \d+\n)',lambda x:x.group()+code+'\n',m,count=1)
    def const_result(m,value):
        return re.sub(r'    invoke-virtual \{[^}]+\}, Lc/d/a/a/a/b;->d\(\)Z\s+move-result (\w+)',lambda x:f'    const/4 {x[1]}, {value}',m)
    # Keep original class descriptors and resource IDs. Set application ID, own authorities and permission names.
    p=decoded/'AndroidManifest.xml';tree=ET.parse(p);root=tree.getroot();root.set('package',application_id)
    root.attrib.pop(ANDROID+'sharedUserId',None);app=root.find('application')
    original_components={(e.tag,e.get(ANDROID+'name')) for e in app if e.tag in ('activity','activity-alias','service','receiver','provider')}
    mapping={}
    for provider in app.findall('provider'):
        authority=provider.get(ANDROID+'authorities','')
        if authority.startswith(OLD):mapping[authority]=application_id+authority[len(OLD):]
    for perm in root.findall('permission'):
        name=perm.get(ANDROID+'name');mapping[name]=application_id+'.permission.'+('WEBAPP' if 'webapp' in name else 'HIDELIST')
    for el in root.iter():
        for key,value in list(el.attrib.items()):
            if value in mapping:el.set(key,mapping[value])
        if el.tag=='action' and el.get(ANDROID+'name')=='pvr.intent.action.STORE_APP':el.set(ANDROID+'name',application_id+'.OPEN_LIBRARY')
    for name in ['android.permission.FORCE_STOP_PACKAGES','android.permission.QUERY_ALL_PACKAGES','android.permission.PACKAGE_USAGE_STATS','moe.shizuku.manager.permission.API_V23']:
        ET.SubElement(root,'uses-permission',{ANDROID+'name':name})
    ET.SubElement(app,'provider',{ANDROID+'name':'rikka.shizuku.ShizukuProvider',ANDROID+'authorities':application_id+'.shizuku',ANDROID+'multiprocess':'false',ANDROID+'enabled':'true',ANDROID+'exported':'true',ANDROID+'permission':'android.permission.INTERACT_ACROSS_USERS_FULL'})
    ET.SubElement(app,'meta-data',{ANDROID+'name':'moe.shizuku.client.V3_SUPPORT',ANDROID+'value':'true'})
    settings=ET.SubElement(app,'activity',{ANDROID+'name':NEW+'.LauncherSettingsActivity',ANDROID+'exported':'false',ANDROID+'screenOrientation':'landscape'})
    ET.SubElement(settings,'meta-data',{ANDROID+'name':'pico.vr.position',ANDROID+'value':'near'})
    # The two original providers are clone-internal, not a substitute for PICO's privileged providers.
    for el in app.findall('provider'):
        if el.get(ANDROID+'name') in [OLD+'.datacache.webapp.WebAppProvider',OLD+'.datacache.backlist.HideListProvider']:
            el.set(ANDROID+'exported','false')
    for activity in app.findall('activity'):
        if activity.get(ANDROID+'name')==OLD+'.AllAppActivity':activity.set(ANDROID+'exported','true')
    assert original_components <= {(e.tag,e.get(ANDROID+'name')) for e in app if e.tag in ('activity','activity-alias','service','receiver','provider')}, 'Original component removed'
    tree.write(p,encoding='utf-8',xml_declaration=True)
    if version_name is not None:
        yml=decoded/'apktool.yml'
        text=yml.read_text(encoding='utf-8')
        text=re.sub(r'(?m)^  versionCode:.*$', '  versionCode: '+str(version_code), text)
        text=re.sub(r'(?m)^  versionName:.*$', '  versionName: '+version_name, text)
        yml.write_text(text,encoding='utf-8')
    for p in (decoded/'smali').rglob('*.smali'):
        text=p.read_text(encoding='utf-8');old=text
        # Exact literal replacement, never descriptor replacement.
        for a,b in {**mapping,OLD:application_id}.items():text=text.replace('"'+a+'"','"'+b+'"')
        for a,b in mapping.items():text=text.replace('content://'+a+'/', 'content://'+b+'/')
        if text!=old:p.write_text(text,encoding='utf-8')
    # TeaTracker rejects a separately signed package. Preserve its normal bind-failure path.
    edit('os/teatracker/m.smali',lambda s:method(s,'c()Z',lambda m:m.replace(
        'invoke-virtual {v1, v0, p0, v4}, Landroid/content/Context;->bindService(Landroid/content/Intent;Landroid/content/ServiceConnection;I)Z',
        f'invoke-static {{v1, v0, p0, v4}}, {H}->bindTracker(Landroid/content/Context;Landroid/content/Intent;Landroid/content/ServiceConnection;I)Z')))
    # Application label in every locale; add one stable new resource via aapt2.
    for p in (decoded/'res').glob('values*/strings.xml'):
        s=p.read_text(encoding='utf-8');s=re.sub(r'(<string name="app_name"[^>]*>).*?(</string>)',r'\g<1>Библиотека Pico\2',s,flags=re.S)
        if p.parent.name=='values':s=s.replace('</resources>','    <string name="gexoty_force_stop">Стоп</string>\n    <string name="gexoty_groups">В группы</string>\n    <string name="gexoty_hide">Скрыть</string>\n</resources>')
        p.write_text(s,encoding='utf-8')
    (decoded/'res/layout/gexoty_button.xml').write_text('''<?xml version="1.0" encoding="utf-8"?>
<com.bytedance.osui.common.OSUIButton xmlns:android="http://schemas.android.com/apk/res/android"
 android:layout_width="wrap_content" android:layout_height="wrap_content" android:singleLine="true"
 style="@style/OSUIWidget.ButtonRegular.Transparent.BlueText" />
''',encoding='utf-8')
    (decoded/'res/layout/gexoty_settings_button.xml').write_text('''<com.bytedance.osui.common.OSUIButton xmlns:android="http://schemas.android.com/apk/res/android"
 android:layout_width="match_parent" android:layout_height="wrap_content" android:gravity="center_vertical|start"
 android:paddingLeft="16dp" android:paddingRight="16dp" android:textSize="16sp" android:textColor="#eeeeee"
 android:background="@drawable/gexoty_settings_action" android:singleLine="true" android:ellipsize="end"
 android:drawableRight="@drawable/osui_arrow" style="@style/OSUIWidget.ButtonRegular.Transparent" />''',encoding='utf-8')
    (decoded/'res/drawable/gexoty_settings_card.xml').write_text('''<shape xmlns:android="http://schemas.android.com/apk/res/android"><solid android:color="#303030"/><corners android:radius="12dp"/><stroke android:width="1dp" android:color="#484848"/></shape>''',encoding='utf-8')
    (decoded/'res/drawable/gexoty_settings_action.xml').write_text('''<selector xmlns:android="http://schemas.android.com/apk/res/android">
<item android:state_pressed="true"><shape><solid android:color="#224f78"/><corners android:radius="8dp"/><stroke android:width="1dp" android:color="#529be0"/></shape></item>
<item android:state_hovered="true"><shape><solid android:color="#454d56"/><corners android:radius="8dp"/><stroke android:width="1dp" android:color="#6b94b8"/></shape></item>
<item android:state_focused="true"><shape><solid android:color="#454d56"/><corners android:radius="8dp"/><stroke android:width="1dp" android:color="#6b94b8"/></shape></item>
<item><shape><solid android:color="#3c3c3c"/><corners android:radius="8dp"/><stroke android:width="1dp" android:color="#565656"/></shape></item>
</selector>''',encoding='utf-8')
    (decoded/'res/drawable/gexoty_ic_settings.xml').write_text('''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
<path android:fillColor="#CCFFFFFF" android:pathData="M10,2h4l1,3 3,1 3,2 -2,3v2l2,3 -3,2 -3,1 -1,3h-4l-1,-3 -3,-1 -3,-2 2,-3v-2L3,8l3,-2 3,-1zM12,8a4,4 0,1 0,0 8a4,4 0,1 0,0 -8" android:fillType="evenOdd" />
</vector>''',encoding='utf-8')
    # Ordinary-app permission UI route; no permission grants or security-policy changes.
    edit('com/pvr/appmanager/utils/Utils.smali',lambda s:body(s,'showAppPermission(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)V',f'    invoke-static {{p0, p1, p2}}, {I}->openPermissions(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)V\n    return-void',0))
    # Outline-only clipping is skipped by software view captures. Clip icons in draw as well.
    edit('androidx/constraintlayout/utils/widget/ImageFilterView.smali',lambda s:body(s,'draw(Landroid/graphics/Canvas;)V',
        '    invoke-static {p0, p1}, Lcom/gexoty/picolibrary/IconMask;->begin(Landroid/view/View;Landroid/graphics/Canvas;)I\n    move-result v0\n    invoke-super {p0, p1}, Landroidx/appcompat/widget/AppCompatImageView;->draw(Landroid/graphics/Canvas;)V\n    invoke-virtual {p1, v0}, Landroid/graphics/Canvas;->restoreToCount(I)V\n    return-void'))
    edit('com/pvr/appmanager/utils/Utils.smali',lambda s:method(s,'getAppMenuName(Ljava/lang/String;)Ljava/lang/String;',lambda m:after_directives(m,f'    invoke-static {{p1}}, {S}->groupName(Ljava/lang/String;)Ljava/lang/String;\n    move-result-object v0\n    if-eqz v0, :gexoty_original_title\n    return-object v0\n    :gexoty_original_title')))
    edit('com/pvr/appmanager/d0/a.smali',lambda s:method(s,'e(Z)Lorg/json/JSONArray;',lambda m:after_directives(m,f'    invoke-static {{}}, {I}->selectedCatalog()Lorg/json/JSONArray;\n    move-result-object v0\n    if-eqz v0, :gexoty_original_catalog\n    return-object v0\n    :gexoty_original_catalog')))
    edit('com/pvr/appmanager/Filter.smali',lambda s:body(s,'isStoreSource(Lcom/pvr/appmanager/entity/AppData;)Z','    const/4 v0, 0x1\n    return v0'))
    edit('com/pvr/appmanager/Filter.smali',lambda s:body(s,'initByDeviceType()V','    invoke-direct {p0}, Lcom/pvr/appmanager/Filter;->initToC()V\n    return-void'))
    edit('com/pvr/appmanager/Filter.smali',lambda s:method(s,'judgeSourceType(Lcom/pvr/appmanager/entity/AppData;)V',lambda m:m.replace('    return-void',f'    invoke-static {{p1}}, {H}->normalize(Ljava/lang/Object;)V\n    return-void')))
    edit('com/pvr/appmanager/TocAllApp.smali',lambda s:body(s,'c(Lcom/pvr/appmanager/entity/AppData;)Z','    const/4 v0, 0x1\n    return v0'))
    edit('com/pvr/appmanager/utils/menu/AppMenuTabHelper.smali',lambda s:method(s,'addMenuTabs(Landroid/content/Context;Lcom/bytedance/osui/grouplist/OSUISideNavigation;Lcom/pvr/appmanager/utils/ToolTipsHelper;)V',lambda m:const_result(m,'0x0').replace(
        '    invoke-virtual {v0, p1}, Lcom/bytedance/osui/grouplist/c$a;->g(Lcom/bytedance/osui/grouplist/c;)V',
        f'    invoke-static {{v0, p1}}, {S}->extendNavigation(Ljava/lang/Object;Landroid/view/View;)V\n\n    invoke-virtual {{v0, p1}}, Lcom/bytedance/osui/grouplist/c$a;->g(Lcom/bytedance/osui/grouplist/c;)V').replace('    return-void',f'    invoke-static/range {{p1 .. p1}}, {H}->hideUnknown(Landroid/view/View;)V\n    return-void')))
    edit('com/pvr/appmanager/Filter.smali',lambda s:method(s,'filterData(Ljava/lang/String;Lcom/pvr/appmanager/z/g0;Ljava/util/List;ZLjava/lang/String;)V',lambda m:m.replace(
        '    const-string v0, "type"',f'    invoke-static {{p1, p3}}, {S}->filterCatalog(Ljava/lang/String;Ljava/util/List;)Ljava/util/List;\n    move-result-object p3\n    invoke-static {{p1}}, {S}->filterType(Ljava/lang/String;)Ljava/lang/String;\n    move-result-object p1\n\n    const-string v0, "type"',1)))
    edit('com/pvr/appmanager/Filter.smali',lambda s:method(s,'getUpdateApps(Ljava/util/List;)Ljava/util/List;',lambda m:after_directives(m,f'    invoke-static {{p1}}, {S}->visibleCatalog(Ljava/util/List;)Ljava/util/List;\n    move-result-object p1')))
    edit('com/pvr/appmanager/Filter.smali',lambda s:method(s,'appendData(Lcom/pvr/appmanager/z/g0;Lcom/pvr/appmanager/entity/AppData;)V',lambda m:m.replace(
        '    const-string v0, "adapter"',f'    invoke-static {{p2}}, {S}->includeApp(Ljava/lang/Object;)Z\n    move-result v0\n    if-nez v0, :gexoty_append_visible\n    return-void\n    :gexoty_append_visible\n    const-string v0, "adapter"',1)))
    edit('com/pvr/appmanager/AllAppActivity.smali',lambda s:body(s,'getCategoryIndex(ZLjava/lang/String;)I',f'    invoke-static {{p2}}, {H}->categoryIndex(Ljava/lang/String;)I\n    move-result v0\n    return v0'))
    edit('com/pvr/appmanager/AllAppActivity.smali',lambda s:method(s,'changeRecyclerViewType(Ljava/lang/String;)V',lambda m:after_directives(const_result(m,'0x0'),f'    invoke-static/range {{p1 .. p1}}, {H}->normalTab(Ljava/lang/String;)Ljava/lang/String;\n    move-result-object p1')))
    # p0 is reused as String near return; attach while it still holds the Activity.
    edit('com/pvr/appmanager/AllAppActivity.smali',lambda s:method(s,'onCreate(Landroid/os/Bundle;)V',lambda m:m.replace(
        '    invoke-direct {p0}, Lcom/pvr/appmanager/AllAppActivity;->initAdapter()V',
        f'    invoke-direct {{p0}}, Lcom/pvr/appmanager/AllAppActivity;->initAdapter()V\n\n    invoke-static {{p0}}, {H}->attach(Landroid/app/Activity;)V')))
    edit('com/pvr/appmanager/AllAppActivity.smali',lambda s:method(s,'onResume()V',lambda m:m.replace(
        '    invoke-super {p0}, Landroidx/fragment/app/FragmentActivity;->onResume()V',
        f'    invoke-super {{p0}}, Landroidx/fragment/app/FragmentActivity;->onResume()V\n\n    invoke-static {{p0}}, {S}->resumed(Landroid/app/Activity;)V')))
    # Do not allow a display refresh to enable/disable other packages.
    edit('com/pvr/appmanager/utils/DisableAppHelper.smali',lambda s:body(s,'handleData(Ljava/util/List;Ljava/util/List;)V','    return-void',0))
    edit('com/pvr/appmanager/datacache/backlist/a.smali',lambda s:body(s,'c(Ljava/lang/String;)Z','    const/4 v0, 0x0\n    return v0'))
    edit('com/pvr/appmanager/datacache/backlist/b.smali',lambda s:body(s,'b(Landroid/content/Context;)Ljava/util/List;','    new-instance v0, Ljava/util/ArrayList;\n    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V\n    return-object v0'))
    # Add missing launchable packages and saved shortcuts after original discovery.
    edit('com/pvr/appmanager/b0/a.smali',lambda s:method(s,'b(Landroid/content/Context;Ljava/util/List;Ld/b0/b/p;)Ljava/util/List;',lambda m:m.replace('    return-object p0',f'    invoke-static {{p0}}, {H}->mergeInventory(Ljava/util/List;)Ljava/util/List;\n    move-result-object p0\n    invoke-static {{p0}}, {I}->enrichInventory(Ljava/util/List;)Ljava/util/List;\n    move-result-object p0\n    return-object p0')))
    edit('com/pvr/appmanager/utils/menu/MoreMenusHelper.smali',lambda s:method(s,'getAllMenus(Ljava/lang/Object;ILd/b0/b/p;)Ljava/util/List;',lambda m:m.replace('    return-object v0',f'    invoke-static {{v0, p1, p2}}, {H}->extendMenus(Ljava/util/List;Ljava/lang/Object;I)Ljava/util/List;\n    move-result-object v0\n    return-object v0')))
    edit('com/pvr/appmanager/utils/menu/MoreMenusHelper.smali',lambda s:method(s,'generateView(Landroid/content/Context;Lcom/pvr/appmanager/utils/menu/MenuBean;Landroid/view/View$OnClickListener;)Landroid/view/View;',lambda m:m.replace('    invoke-virtual {p0, p3}, Landroid/widget/TextView;->setOnClickListener(Landroid/view/View$OnClickListener;)V',f'    invoke-static {{p0, p2, p3}}, {H}->bindMenu(Landroid/view/View;Ljava/lang/Object;Landroid/view/View$OnClickListener;)V')))
    (decoded/'gexoty-patches.json').write_text(json.dumps({'base':'2.9.5/200905100','applicationId':application_id,'modified_methods':changes,'authorities':mapping},ensure_ascii=False,indent=2),encoding='utf-8')
    return changes

if __name__=='__main__':
    print(json.dumps(transform(sys.argv[1]),indent=2))
