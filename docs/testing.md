# Проверки

## Автоматические проверки

- RulesTest: порядок, exact/domain/suffix/wildcard/regex, IDN/case/trailing dot, порты, схема/path, отключённые правила, source/null, сохранение исходного URL, missing browser, fallback, ASK и self-routing, IPv6 и некорректные URL.
- ConfigCodecTest: round-trip, unknown version, malformed/oversized JSON, duplicate IDs, invalid regex, строгий Boolean.
- IntentHandlingTest: видимость generic handler, исключение self, external VIEW → явный компонент с исходным URL, отсутствие untrusted extras/referrer, правило с referrer, fallback, отклонение не-web URL.
- UiSmokeTest: создание и повторное открытие сохранённого правила, disabled save для пустого условия, descriptions controls, настройки и переключение светлой/тёмной темы; снимки экрана.
- CI выполняет UI smoke на телефоне, при font_scale 1.6 и широкой области. Android Lint обязателен.

Test-only CaptureBrowserActivity находится только в androidTest APK и не попадает в release. Intent tests перехватывают/проверяют outgoing Intent через Espresso Intents; они не доказывают загрузку страницы в Firefox, Chrome или Bearium.

## Ручная проверка перед релизом

Установить APK и Firefox/Chrome/Bearium (если доступны). Назначить Router default browser. Создать приоритетный маршрут pikabu.ru → Firefox, .ru → Bearium и fallback Chrome. Проверить переход из Telegram с отключённым встроенным браузером, IDN-ссылку, удаление выбранного браузера и неизвестный источник. Проверить системный default role на конкретной прошивке.

Проверить TalkBack focus, названия переключателей и действий, доступ к сохранению при 200% шрифте, landscape/широкое окно, длинные названия приложений, клавиатуру/IME, dynamic color и выключенный dynamic color. Проверить круглую/квадратную/сквиркл launcher mask и themed icon. Импорт повреждённого файла и неподдерживаемой версии не должен изменить текущую конфигурацию. Проверить append/replace и экспорт после штатного обновления подписанного APK.

## Фактически выполненные проверки

Состояние конкретной ревизии подтверждается соответствующим GitHub Actions run и его отчётами, а не наличием теста в исходниках. Итоговый run и ограничения будут зафиксированы после завершения CI. Реальные браузеры, Telegram, TalkBack и OEM default-role на физических устройствах в удалённой сборочной среде не проверяются.
