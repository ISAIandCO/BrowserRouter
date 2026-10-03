# Сборка и GitHub Releases

JDK 17, Android SDK platform 36 + build-tools 36.0.0, Gradle Wrapper 8.13, AGP 8.13.2, Kotlin 2.2.21. Версии зафиксированы. Gradle distribution и wrapper проверяются SHA-256; wrapper взят из официального Gradle v8.13.0.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
```

Windows: `gradlew.bat`. Android Studio создаёт local.properties с sdk.dir; файл не коммитится. Загрузка зависимостей требует доступа к Google Maven, Maven Central, Gradle и SDK repository. В РФ доступность зависит от сети; само приложение и его работа не зависят от Google Play Services или этих серверов.

## CI

`ci.yml` запускается для PR, main и вручную; проверяет JVM tests, Android lint, собирает debug APK. После успешной сборки эмулятор API 35 выполняет Intent и Compose tests, дополнительные UI-проходы при font_scale=1.6 и широком окне. Отчёты и APK — `checks-and-debug-apk`; результаты устройства и снимки — `device-tests-and-screenshots`. CI не создаёт релизы на обычный commit/PR.

Debug package — `app.browserrouter.debug`, release — `app.browserrouter`. Их настройки независимы. Временный debug keystore генерируется SDK; новая debug-сборка может потребовать удаления прежней. Перед этим экспортируйте настройки. Для подписанных релизов сохраняйте один постоянный ключ.

## Постоянная подпись

CI также собирает release с R8 и выполняет release lint без signing secrets. Этот APK неподписан, служит проверкой сборки и не публикуется. Подписанный release workflow отдельно требует все secrets; без них публикация завершится ошибкой до сборки распространяемого APK.

Создайте ключ **локально у владельца** и сохраните резервную копию вне GitHub. Пароли вводите интерактивно, не помещайте их в shell history:

```sh
keytool -genkeypair -keystore browserrouter.p12 -storetype PKCS12 \
  -alias browserrouter -keyalg RSA -keysize 4096 -validity 10000
```

Добавьте GitHub → Repository Settings → Secrets and variables → Actions:

| Secret | Содержимое |
|---|---|
| ANDROID_KEYSTORE_BASE64 | Base64 содержимого PKCS12 |
| ANDROID_STORE_PASSWORD | Пароль keystore |
| ANDROID_KEY_ALIAS | browserrouter или выбранный alias |
| ANDROID_KEY_PASSWORD | Пароль ключа; для PKCS12 обычно равен паролю keystore |

Linux: `base64 -w 0 browserrouter.p12` (скопировать непосредственно в secret). PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes('browserrouter.p12'))`. Не присылайте ключ/пароли в issue, PR или чат, не коммитьте их. `.gitignore` исключает keystores.

Gradle читает SIGNING_KEYSTORE, SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD. Workflow декодирует ключ в RUNNER_TEMP, ограничивает доступ, проверяет обязательные secrets и удаляет ключ в always-step. Отсутствие ключа не превращает релиз в debug APK.

## Публикация

1. Дождаться зелёных проверок и включить код в main.
2. Для новой версии увеличить versionName **и versionCode** в app/build.gradle.kts. Code должен расти при каждом выпускаемом обновлении.
3. Создать и push tag, точно равный `v<versionName>` на проверенном commit main, например `v1.0.0`.
4. release.yml сначала выполнит общие CI-проверки, включая эмулятор. Затем проверит принадлежность commit истории main, соответствие tag/versionName, наличие secrets, release lint, соберёт release APK и проверит подпись apksigner.
5. GitHub Release получит `BrowserRouter-<версия>.apk`, source ZIP и SHA256SUMS.txt. Google Play workflow нет.

```sh
git tag v1.0.0
git push origin v1.0.0
```

Не менять/перезаписывать уже опубликованный tag или assets. При проблеме исправить код и выпустить новую версию. Если шаг публикации завершился сетевой ошибкой, сначала проверить, появился ли Release: workflow намеренно не использует clobber. При отсутствии Release можно повторить run; при частичной публикации проверять артефакты вручную.

Обновление поверх установленного приложения требует того же applicationId и совместимой подписи. Потеря release key мешает обычным обновлениям. Конфигурация сохраняется при штатном обновлении; смена пакета/переустановка может удалить её. Экспорт — независимая резервная копия. Автоматическая ротация signing key в проекте не реализована.
