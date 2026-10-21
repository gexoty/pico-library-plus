# Локальная сборка

Проект сохраняет проверенный бинарный код и ресурсы PICO AppManager 2.9.5. Это не Gradle-проект: Основная сборка не требует Gradle, AGP или Kotlin. Wrapper эталонного LSPosed-проекта в input/lsposed-original сохранён, но не используется. Основная сборка — scripts/build.py; общий Java-код находится в src/, семантические патчи — scripts/patch.py. Исходный манифест, ресурсы, assets и arm64-библиотеки находятся внутри input/original.apk и извлекаются заново для каждой сборки. minSdk и targetSdk остаются 29; Android Platform 34 используется только для компиляции расширения.

Нужны Python 3.13, JDK 21, Android SDK Platform 34 и Build Tools 36.0.0, Apktool 3.0.3. Укажите JAVA_HOME и ANDROID_HOME. Системные Python-пакеты не требуются. Shizuku 13.1.5 сохранён в vendor/shizuku; хеши проверяются перед компиляцией. REA и JADX для сборки не нужны.

```sh
python scripts/fetch-tools.py
python -m unittest discover -s tests -p 'test_*.py' -v
python scripts/build.py --variant standalone
python scripts/build.py --variant system
python scripts/release.py --apktool .tools/apktool.jar
```

При необходимости добавьте --java-home и --sdk. Apktool получается с официального GitHub Release и сверяется с scripts/tools.lock.json. Каждый build создаёт новый build/<timestamp>, включая отдельный framework-кеш Apktool. Gradle-кеши не используются. Оба варианта собираются независимо, исходники не копируются и не переименовываются. Launcher остаётся com.pvr.appmanager.AllAppActivity.

release.py собирает обе версии, выравнивает APK, подписывает стандартным Android debug-ключом ~/.android/debug.keystore (alias androiddebugkey, оба пароля android), проверяет сертификат, пакет, версии, SDK, компоненты, ZIP и SHA256. Если ключ отсутствует, создаётся стандартный debug-ключ вне репозитория. Для сохранения обновляемости храните его резервную копию: одинаковый alias не означает одинаковый сертификат. Прежний локальный debug.keystore используется без замены. Debug-подпись выбрана пользователем также для CI и GitHub Releases; это не подпись PICO.

Результаты в dist/: PicoLibrary-vX.Y.Z.apk, PicoLibrary-System-vX.Y.Z.apk, PicoLibrary-Magisk-vX.Y.Z.zip, SHA256SUMS.txt, verification.json. Публичные файлы — standalone APK, Magisk ZIP и контрольные суммы. System APK дополнительно остаётся локально. Для повторения используйте --output dist-second: каталог результата должен быть пустым.

Версия в VERSION, формат MAJOR.MINOR.PATCH без ведущих нулей. versionCode = 200905100 + MAJOR*1000000 + MINOR*1000 + PATCH; MINOR/PATCH 0..999, итог не выше 2100000000. Значения вне диапазона отвергаются, а не обрезаются. База выше исходной версии PICO при первом релизе 1.0.0. Многозначные компоненты сравниваются численно. Имена, обе версии APK и module.prop получают одну версию. Битовая идентичность APK не обещается: сравнивать можно распакованное содержимое, а подписи и ZIP timestamps могут различаться.

Сборка не устанавливает и не запускает приложение, не использует ADB и не меняет устройство. Успех проверки на ПК не подтверждает запуск системной версии.
