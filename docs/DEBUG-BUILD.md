Примечание очистки: исторические APK сохранены в .local-evidence/validation-history; карта — preserved-local-data.json. Новые сборки и результаты текущего этапа описаны в REPOSITORY-AUDIT.md. Исторический текст ниже не является проверкой нового Magisk-модуля.

# Debug-сборка — 21 октября 2026

Это исторический отчёт первой сборки. Актуальный APK обновлён после исправления
вылета; см. [DEVICE-STARTUP-FIX.md](DEVICE-STARTUP-FIX.md).

Изучены все Markdown-документы проекта, исходники расширений, патчи, проверки,
метаданные зависимостей и предыдущие отчёты. Сигнатуры AppData (21 аргумент),
AppResolveInfo (3), MenuBean (5), getApp/v/C/z сверены с исходным smali.
Семантические изменения кода не потребовались.

Сборка выполнена `python scripts/build.py`. StopPolicyTest и встроенные проверки
манифеста, authorities и дополнительного DEX прошли. Оригинал не изменён.

Результат: `validation/picolibrary-debug.apk`.
Приложение: `com.gexoty.picolibrary`, «Библиотека Pico», версия 2.9.5/200905100.
Подписано существующим ключом `%USERPROFILE%/.android/debug.keystore`,
alias `androiddebugkey`, сертификат Android Debug, RSA 2048. Ключ не копировался
в репозиторий. Это debug-подпись, сборка сохраняет настройки исходного манифеста.

После подписи успешно выполнены apksigner verify (APK Signature Scheme v3),
zipalign -c -P 16 -v 4 и scripts/verify.py на подписанном APK.
Точные SHA256, размер и каталог сборки: `validation/debug-build-result.json`.
Журналы: `validation/debug-build.log`, `validation/debug-signature-verification.txt`,
`validation/debug-alignment-verification.txt`.

Для повторной подписи после сборки используйте apksigner из build-tools 36.0.0,
команду sign с --ks, --ks-key-alias androiddebugkey и --out; вход — aligned unsigned
APK из build/latest.json. Затем выполните verify --verbose --print-certs и проверку
zipalign. Подпись выполняется после выравнивания; APK после подписи не изменять.

На Pico 4 ничего не устанавливалось и не запускалось, ADB и подключение устройств
не использовались. Совместимость PICO SDK, VR-интерфейса, Root/Shizuku и системных
разрешений на устройстве не проверялась. Исторические unsigned-отчёты сохранены.
