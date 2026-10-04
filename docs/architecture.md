# Архитектура и конфигурация

Один Android app-модуль, Kotlin + Compose. Движок не зависит от Android или UI. DI-фреймворка, сервера, WebView, foreground-сервиса нет. Фоновые обновления geosite выполняет WorkManager, хранение правил остаётся в AtomicFile.

| Файл | Назначение |
|---|---|
| `Rules.kt` | Модель, разбор URL, нормализация hostname, валидация и first-match routing |
| `BrowserApps.kt` | PackageManager queries, источник Intent, запуск выбранного компонента |
| `ConfigCodec.kt` | Явный формат JSON v2 (чтение v1 сохранено) и строгая проверка импорта |
| `GeositeDownload.kt`, `GeositeUpdater.kt` | HTTPS-источники, загрузка и persistent WorkManager scheduling |
| `Geosite.kt`, `GeositeStore.kt` | V2Ray GeoSiteList, RE2/J, локальная атомарная база и общий immutable cache |
| `ConfigStore.kt` | AtomicFile: чтение/запись одной конфигурации в private files |
| `RouterModel.kt` | ViewModel + StateFlow, сериализация изменений Mutex, IO вне главного потока |
| `MainActivity.kt` | Каталог приложений, RoleManager и SAF import/export |
| `LinkActivity.kt` | Экспортированный обработчик ссылок; маршрутизация или выбор приложения |
| `RouterUi.kt`, `RuleEditor.kt`, `SettingsScreen.kt`, `RouterTheme.kt` | Compose UI, черновик формы, темы |

## Порядок маршрутизации

1. Принимать только ACTION_VIEW с HTTP/HTTPS URL, ограничить размер 16 384 символами, проверить authority и порт.
2. Нормализовать host: Unicode-точки → `.`, убрать одну завершающую точку, IDN.toASCII с STD3, нижний регистр. IPv6 должен быть в `[]`, синтаксис проверяется URI. DNS-запросов нет.
3. Для каждого активного правила проверять все заданные условия через AND. Отсутствующий source не совпадает с правилом для конкретного пакета.
4. Первое совпадение возвращает BROWSER или ASK. Отсутствующий браузер первого правила ведёт к выбору, а не к неявному переходу к следующему правилу.
5. Без совпадения использовать fallback; `null` означает выбор при открытии. Self-target всегда запрещён.
6. Повторно проверить выбранный компонент при запуске; при удалении/отключении приложения оставить понятный экран выбора.

### Матчеры

| Тип | Семантика |
|---|---|
| EXACT | Равенство нормализованного hostname |
| DOMAIN | Сам домен и поддомены через границу `.` |
| SUFFIX | Та же проверка границы для зоны/окончания; допускает начальную `.` |
| WILDCARD | `*` занимает целую DNS-часть и соответствует одному или нескольким фрагментам hostname; вся строка должна совпасть |
| REGEX | Kotlin/Java Regex.matches по всему ASCII hostname; без автоматического case-insensitive |
| GEOSITE | Группа из локальной базы, например `geosite:google@ads`; подробности в [geosite.md](geosite.md) |

Порт учитывает default 80/443. Path — `URI.rawPath`: префикс с учётом регистра и percent-encoding; пустой путь считается `/`. Query/fragment не участвуют в сопоставлении, но полностью сохраняются при передаче. Suffix не является Public Suffix List. IDN использует платформенную Java IDN, а не собственную реализацию IDNA/PSL; это маршрутизация, не проверка подлинности сайта.

Regex компилируется при проверке формы/импорте и выполняется вне UI-потока. Для обычных случаев используйте готовые режимы: сложный backtracking regex способен надолго занять worker thread. Импортируйте правила из доверенного источника; длина шаблона ограничена 256 символами, hostname 253 символами, число правил 200. Эти лимиты не гарантируют bounded-time выполнения любого Java regex.

## JSON v2

Хранение и экспорт одинаковы. Файл `files/routing-v1.json`, UTF-8, максимум 512 КБ. Поля правил сериализуются явно без reflection:

```json
{
  "schemaVersion": 2,
  "fallback": "org.mozilla.firefox",
  "theme": "SYSTEM",
  "dynamicColor": true,
  "onboarded": true,
  "rules": [
    {
      "id": "example-rule-1",
      "enabled": true,
      "source": null,
      "mode": "SUFFIX",
      "host": ".ru",
      "scheme": null,
      "port": null,
      "pathPrefix": null,
      "action": "BROWSER",
      "browser": "app.bearium.browser"
    }
  ]
}
```

Обязательные поля: schemaVersion, rules; у правила id, mode, host, action. Необязательные source/scheme/port/pathPrefix/browser — null или указанное значение. enabled по умолчанию true. Типы не приводятся молча: строка `"false"` не считается Boolean; порт должен быть Int. У action BROWSER обязателен package браузера. У ASK браузер не используется.

Схема v2 добавляет режим GEOSITE, используя существующее поле host. Необязательный блок `geositeUpdates` содержит url, autoUpdate, intervalHours, unmeteredOnly; при его отсутствии используются значения по умолчанию (автообновление выключено). Настройки и резервные копии v1 читаются без потери полей; при следующей записи экспортируется v2. Неизвестная версия отклоняется до изменения файла. Повреждённый текущий файл сохраняется, UI предлагает явное восстановление/сброс, а LinkActivity позволяет вручную выбрать браузер.

AtomicFile обеспечивает rollback при ошибке записи. Чтение и запись между Activity используют один process lock. После finishWrite проверяется содержимое: AtomicFile может только залогировать ошибку rename. ViewModel сообщает успех и закрывает редактор только после сохранения. Конфигурация переживает обновление APK с тем же package и signing key. SAF импорт сначала полностью валидирует файл, затем показывает выбор добавления/замены. При добавлении генерируются новые ID, порядок существующих правил сохраняется. Замена нужна для восстановления всех настроек.

## Расширение

Новый matcher: добавить HostMode, hostPattern/validateRule/matches, название и подсказку в редакторе, тесты краевых случаев и миграцию, если меняется формат. Новый action: добавить Action и явный результат Route; реализовать обработку в LinkActivity, редактор и строгую валидацию. Не добавлять Android-зависимости в движок.
