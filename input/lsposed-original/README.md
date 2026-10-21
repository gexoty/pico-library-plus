# PICO 资源库未知来源移除

[中文](#中文) | [English](#english) | [Русский](#русский)

---

<a id="中文"></a>
## 中文

适用于 PICO 4 的 LSPosed/Vector 模块。它会隐藏 AppManager 资源库中的“未知来源”导航分区，并让原本归入该分区的 PICO SDK 应用彻底离开“未知来源”——通过“源头短路 + 分类后兜底”双机制并入常规分区显示。

### 功能

- 仅作用于 `com.pvr.appmanager`。
- 隐藏资源库中 tag 为 `unknown` 的导航项。
- 源头短路（合并自 [thoricelli](https://github.com/thoricelli) 的方案）：在分类链源头强制 `Filter.isStoreSource()` 返回 true，相关条目在分类阶段即并入常规归类，“未知来源”不再产生任何条目。
- 分类后兜底：在 AppManager 完成自身分类后，识别 `source="other"` 且 `subSource="sdk_app"` 的项目，将其内存显示分类改为 `subSource="2d_app"`。
- 将此前保存的“未知来源”页签选择重定向到“应用”。

### 不会修改的内容

本模块不修改 Android 的安装安全策略或包管理行为。它不会修改“允许未知来源安装”权限、`PackageManagerService`、`PackageInstaller`、应用签名、安装器元数据、系统包管理策略、PICO Store 或 PICO VR Shell。

### 兼容性

开发和验证环境：

- 设备：PICO 4 / A8110
- 系统：PICO OS 5.13.7
- 目标包名：`com.pvr.appmanager`
- 目标 Activity：`com.pvr.appmanager.AllAppActivity`
- AppManager：`2.9.5`，versionCode `200905100`
- Hook 框架：Zygisk Vector / LSPosed Legacy API

AppManager 后续更新如变更相关类或方法，模块可能需要适配。

### 构建

需要 JDK 17 与 Android SDK Platform 34：

```sh
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

调试 APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`。

### 安装与验证

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell su -c '/data/adb/lspd/cli modules enable com.picoxr.appmanagerfix'
adb shell su -c '/data/adb/lspd/cli scope add com.picoxr.appmanagerfix com.pvr.appmanager/0'
adb shell am force-stop com.pvr.appmanager
adb shell am start -a pvr.intent.action.STORE_APP
```

在 Git Bash 中，含设备绝对路径的命令需加 `MSYS_NO_PATHCONV=1`。

验证日志：

```sh
adb shell su -c 'grep -F "PicoAppManagerFix" /data/adb/lspd/log/modules_*.log | tail -n 40'
```

预期出现 `loaded into com.pvr.appmanager`、`hidden unknown navigation item` 与 `store-source bypass installed`。如兜底逻辑检测到受影响应用，会额外记录 `moved <package> from unknown to apps`。

---

<a id="english"></a>
## English

An LSPosed/Vector module for PICO 4. It hides the **Unknown** navigation section in the AppManager library and moves affected PICO SDK applications out of it — via a source-level short-circuit and a post-classification fallback — into the regular sections.

### Features

- Runs only in `com.pvr.appmanager`.
- Hides the navigation item tagged `unknown`.
- Source-level short-circuit (merged from [thoricelli](https://github.com/thoricelli)'s approach): forces `Filter.isStoreSource()` to return true, so affected entries are re-classified before they can land in the Unknown bucket.
- Post-classification fallback: after AppManager completes its own classification, entries with `source="other"` and `subSource="sdk_app"` are re-mapped to `subSource="2d_app"`.
- Redirects a previously persisted Unknown tab selection to Apps.

### What It Does Not Change

This module does not alter Android installation security or package-management behavior. It does not modify unknown-source installation permission, `PackageManagerService`, `PackageInstaller`, application signatures, installer metadata, system package-management policy, PICO Store, or PICO VR Shell.

### Compatibility

Developed and verified with:

- Device: PICO 4 / A8110
- Firmware: PICO OS 5.13.7
- Target package: `com.pvr.appmanager`
- Target Activity: `com.pvr.appmanager.AllAppActivity`
- AppManager: `2.9.5`, versionCode `200905100`
- Hook framework: Zygisk Vector / LSPosed Legacy API

Future AppManager updates may require adaptation if the hooked classes or methods change.

### Build

Requires JDK 17 and Android SDK Platform 34:

```sh
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

### Install And Verify

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell su -c '/data/adb/lspd/cli modules enable com.picoxr.appmanagerfix'
adb shell su -c '/data/adb/lspd/cli scope add com.picoxr.appmanagerfix com.pvr.appmanager/0'
adb shell am force-stop com.pvr.appmanager
adb shell am start -a pvr.intent.action.STORE_APP
```

In Git Bash, prefix commands containing device absolute paths with `MSYS_NO_PATHCONV=1`.

Check the log:

```sh
adb shell su -c 'grep -F "PicoAppManagerFix" /data/adb/lspd/log/modules_*.log | tail -n 40'
```

Expected entries include `loaded into com.pvr.appmanager`, `hidden unknown navigation item` and `store-source bypass installed`. When the fallback path detects an affected application, the module also logs `moved <package> from unknown to apps`.

---

<a id="русский"></a>
## Русский

Модуль LSPosed/Vector для PICO 4. Он скрывает раздел навигации **Unknown** в библиотеке AppManager и выводит затронутые приложения PICO SDK из него — через перехват на уровне источника и пост-классификационный фолбэк — в обычные разделы.

### Возможности

- Работает только в `com.pvr.appmanager`.
- Скрывает пункт навигации с тегом `unknown`.
- Перехват на уровне источника (объединено с подходом [thoricelli](https://github.com/thoricelli)): принудительно заставляет `Filter.isStoreSource()` возвращать true — затронутые записи переклассифицируются, не попадая в раздел Unknown.
- Пост-классификационный фолбэк: после классификации AppManager записи с `source="other"` и `subSource="sdk_app"` переназначаются на `subSource="2d_app"`.
- Перенаправляет ранее сохранённый выбор вкладки Unknown на Apps.

### Что модуль не изменяет

Модуль не меняет политику безопасности установки Android и работу менеджера пакетов. Он не изменяет разрешение установки из неизвестных источников, `PackageManagerService`, `PackageInstaller`, подписи приложений, метаданные установщика, системную политику управления пакетами, PICO Store или PICO VR Shell.

### Совместимость

Разработано и проверено на:

- Устройство: PICO 4 / A8110
- Прошивка: PICO OS 5.13.7
- Целевой пакет: `com.pvr.appmanager`
- Целевая Activity: `com.pvr.appmanager.AllAppActivity`
- AppManager: `2.9.5`, versionCode `200905100`
- Hook-фреймворк: Zygisk Vector / LSPosed Legacy API

После обновлений AppManager может потребоваться адаптация модуля, если изменятся перехватываемые классы или методы.

### Сборка

Требуются JDK 17 и Android SDK Platform 34:

```sh
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

Отладочный APK будет создан по пути `app/build/outputs/apk/debug/app-debug.apk`.

### Установка и проверка

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell su -c '/data/adb/lspd/cli modules enable com.picoxr.appmanagerfix'
adb shell su -c '/data/adb/lspd/cli scope add com.picoxr.appmanagerfix com.pvr.appmanager/0'
adb shell am force-stop com.pvr.appmanager
adb shell am start -a pvr.intent.action.STORE_APP
```

В Git Bash добавляйте `MSYS_NO_PATHCONV=1` перед командами с абсолютными путями на устройстве.

Проверка журнала:

```sh
adb shell su -c 'grep -F "PicoAppManagerFix" /data/adb/lspd/log/modules_*.log | tail -n 40'
```

Ожидаются строки `loaded into com.pvr.appmanager`, `hidden unknown navigation item` и `store-source bypass installed`. При срабатывании фолбэка модуль также запишет `moved <package> from unknown to apps`.

---

## 致谢 / Credits / Благодарности

- 感谢 [thoricelli](https://github.com/thoricelli) 提供的另一种实现方案（在分类链源头短路 `Filter.isStoreSource()`）；本模块的合并机制以 Java 重写了该方案并合并进来。
- Thanks to [thoricelli](https://github.com/thoricelli) for the alternative approach (source-level short-circuit of `Filter.isStoreSource()`); the merged mechanism in this module is a Java re-implementation of it.
- Благодарность [thoricelli](https://github.com/thoricelli) за альтернативный подход (перехват `Filter.isStoreSource()` на уровне источника); объединённый механизм в этом модуле — Java-реализация этого подхода.
