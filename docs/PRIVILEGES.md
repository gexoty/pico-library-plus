# Системные права, Root и Shizuku

Текущая версия по явному запросу пользователя запрашивает Root при первом запуске.
Root выбран для требующих его функций; отказ сопровождается предупреждением.
Подробности и read-only интеграция PICO Store — в [FEATURE-UPDATE.md](FEATURE-UPDATE.md).

У пользователя есть Root и Shizuku; планируется установка нового APK как привилегированного.
В исходниках реализованы три явно выбираемых пути, без автоматического переключения идентичности.

| Способ | Реализация | Условие |
|---|---|---|
| Системный (ручной выбор) | ActivityManager.forceStopPackage через reflection | Действительно выдано FORCE_STOP_PACKAGES, скрытый API доступен |
| Root | su -c /system/bin/am force-stop --user <userId> <package> | Пользователь разрешил su этому пакету |
| Shizuku | UserService -> am force-stop под UID shell/root | Сервер Shizuku работает, разрешение API выдано |

Root-команда строится только после строгой проверки package, без произвольных аргументов.
User ID вычисляется из UID клиента, не берётся из UI. Для Shizuku аргументы передаются
массивом ProcessBuilder, а не shell-строкой. Binder ограничен одним действием, без
универсального исполнения команд. Нет exported Android service для force-stop.
Таймаут su — 15 секунд; am в UserService — 8 секунд, ожидание подключения — 15 секунд.
Операции выполняются вне UI-потока. Пользователь выбирает действие именно в меню карточки.

Защищены собственный пакет, android, SystemUI, известные VR-shell пакеты, Shizuku и Magisk.
Это не исчерпывающий список критических сервисов прошивки; приёмку вести на тестовом приложении.
Выход am=0 означает завершение команды, а не независимое подтверждение полного отсутствия
любых последующих процессов пакета. UI не выдаёт утверждение о таком наблюдении.

## Почему system app недостаточно

В AOSP Android 10 FORCE_STOP_PACKAGES — signature|privileged.
Размещение в /system/app не равно /system/priv-app и не равно UID 1000.
Шаблон `templates/privapp-permissions-com.gexoty.picolibrary.xml` предназначен для
review установщиком: allowlist должен соответствовать разделу с APK и реальной прошивке.
Некоторые PICO permissions могут быть только signature и не выдаются этим XML.
Не добавляйте sharedUserId android.uid.system и не отключайте проверку подписей.
Не меняйте SELinux или hidden-API security policy для прохождения тестов.

Без нужного разрешения системный backend сообщает ошибку. Тогда пользователь может
выбрать Root/Shizuku. Нет подмены force-stop методом killBackgroundProcesses.
Привилегии самого лаунчера остаются отдельной темой от backend остановки.

## Shizuku

Включены официальные зависимости API/provider/aidl/shared 13.1.5 с Maven Central и MIT LICENSE.
ShizukuProvider объявлен с authority нового пакета и защитой INTERACT_ACROSS_USERS_FULL.
UserService — обычный Binder-класс, создаваемый Shizuku, а не компонент service манифеста.
Если нет авторизации, интерфейс запрашивает её и просит повторить остановку;
отложенного автоматического force-stop после разрешения нет.
При выборе Root приложение не обращается к Shizuku для остановки, и наоборот.

## Первичные источники

- AOSP FORCE_STOP_PACKAGES: https://github.com/aosp-mirror/platform_frameworks_base/blob/android-10.0.0_r47/core/res/AndroidManifest.xml
- Allowlist: https://source.android.com/docs/core/permissions/perms-allowlist
- sharedUserId: https://developer.android.com/guide/topics/manifest/manifest-element#uid
- Shizuku API/UserService: https://github.com/RikkaApps/Shizuku-API
- ActivityManager: https://developer.android.com/reference/android/app/ActivityManager

Это инструкции для будущего этапа; права и разделы шлема в ходе подготовки не изменялись.
