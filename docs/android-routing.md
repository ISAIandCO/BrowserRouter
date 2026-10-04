# Android Intent routing

## Базовая платформа

minSdk 29 (Android 10): RoleManager доступен без legacy fallback-логики, приоритет современных устройств. targetSdk/compileSdk 36 (Android 16). Открытая Activity работает в foreground: нет фонового сервиса или фонового старта Activity.

LinkActivity экспортирована с ACTION_VIEW + DEFAULT + BROWSABLE и схемами http/https **без host/path ограничения**. Это делает приложение общим веб-обработчиком для системного выбора браузера. Установить default автоматически нельзя: MainActivity вызывает RoleManager.createRequestRoleIntent(ROLE_BROWSER), проверяя доступность роли. Если роль отсутствует, предлагаются системные настройки default apps. Проверка назначения обновляется при onResume. Реальное поведение OEM нужно проверять на устройстве.

AOSP описывает роль BROWSER как браузерный обработчик; BrowserRouter делегирует показ страницы настоящему браузеру и сам не предоставляет веб-движок/геолокацию. Техническая пригодность для роли определяется реализацией RoleController и общим веб-intent-filter; на отдельных прошивках маршрутизатор может быть недоступен для выбора. Не заявлять универсальную совместимость до OEM-тестов.

## Определение источника

1. Activity.callingPackage, если задан (чаще при startActivityForResult).
2. Activity.referrer с URI вида android-app://package, когда доступен.
3. Иначе null — правило для конкретного источника не совпадает.

getReferrer учитывает EXTRA_REFERRER/EXTRA_REFERRER_NAME и сведения framework. Документация прямо предупреждает: это **не security feature**, данные могут быть подделаны. Посредник, sharesheet и launcher также могут заменить источник. Здесь эти данные используются исключительно для удобства пользователя. Не запрашиваются Usage Access, Accessibility или статистика foreground-приложений.

Activity использует стандартный launchMode: каждому внешнему запуску соответствует отдельная Activity. Если позже вводить singleTop/singleTask, обязательно вызвать setIntent(newIntent) до getReferrer в onNewIntent и заново сбросить routing state.

## Package visibility и передача

Manifest queries ограничены VIEW/BROWSABLE http/https и MAIN/LAUNCHER (источники). QUERY_ALL_PACKAGES не используется. Список источников включает видимые launcher-приложения; пакет отсутствующего источника можно задать вручную в дополнительных условиях.

Каталог браузеров получает resolved filters для нейтрального `.invalid` URL и выбирает общие обработчики без authority/path ограничения. Нет привилегированного списка Chrome/Firefox/Bearium. Для конкретной входящей ссылки список выбора включает доступные обработчики этого URL. Пакет BrowserRouter, неэкспортированные/отключённые Activity и компоненты с требуемым разрешением исключаются.

Передача выполняется **новым** ACTION_VIEW + BROWSABLE Intent с исходным URL, явным ComponentName из PackageManager и FLAG_ACTIVITY_NEW_TASK. Исходный URL не пересобирается; fragment, query и escaping сохраняются. Не передаются extras, ClipData, selector, permission grants и flags из недоверенного входящего Intent. setPackage-only не оставляет выбор компонента resolver'у.

Проверяется component.packageName и исключается self. Исключения запуска показываются пользователю; исходный экран сохраняет выбор. Никакой повторной передачи через общий ACTION_VIEW без указания цели нет. Это предотвращает собственный routing loop; цепочки с другим маршрутизатором не контролируются. Пользователь должен выбирать конечный браузер.

## Сценарии, которые не перехватываются

Встроенный браузер/WebView, явный Intent в пакет браузера, explicit Custom Tabs и Android verified App Links могут не вызвать BrowserRouter. Не добавлять случайные host intent filters/autoVerify: приложение не владеет пользовательскими доменами. Неподдерживаемые схемы (intent:, file:, javascript:) отвергаются.

Firefox, Chrome и Bearium обнаруживаются тем же API, что другие приложения. Сборочные тесты используют отдельный установленный test-only обработчик HTTP/HTTPS; проверку настоящих браузеров и Telegram на физическом устройстве нельзя подменять этим тестом.

## Официальные источники

- [RoleManager](https://developer.android.com/reference/android/app/role/RoleManager)
- [Роли Android / AOSP](https://source.android.com/docs/core/permissions/android-roles)
- [Activity.getReferrer](https://developer.android.com/reference/android/app/Activity#getReferrer())
- [Package visibility: common use cases](https://developer.android.com/training/package-visibility/use-cases)
- [Android App Links](https://developer.android.com/training/app-links)
