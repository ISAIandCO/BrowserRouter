# UI: Material 3 Expressive

Jetpack Compose, одна Activity для управления и отдельная для приёма ссылок. Нет альтернативного Views UI. ViewModel владеет сохранёнными данными и состояниями загрузки/ошибок. Черновик и навигационный выбор используют rememberSaveable, переживая recreation; валидация и маршрутизация находятся вне composable.

Material 3 **1.5.0-alpha04** выбран как зафиксированная Expressive-база с Compose BOM 2025.10.01. Stable 1.4 исключает ExperimentalMaterial3ExpressiveApi; поэтому подключать её и вручную имитировать Expressive не следует. Alpha dependency — осознанный компромисс: обновлять после сборки и визуальных проверок, не использовать dynamic версии. Эта база не называется последним выпуском библиотеки.

- MaterialExpressiveTheme: библиотечные typography, shapes и motion defaults.
- Нативные M3 app bars, cards, FAB, switches, chips, dialogs и modal bottom sheets.
- Expressive ButtonDefaults.shapes для press morphing главных кнопок; LoadingIndicator для загрузки.
- Dynamic color на API 31+, системная/светлая/тёмная тема. На API 29–30 собственная индиго-палитра.
- Activity.enableEdgeToEdge, Scaffold padding, navigationBarsPadding/imePadding для редактора и picker.
- Списки LazyColumn без жёсткой высоты текста. На широком экране контент центрируется и ограничивается 720–840 dp.
- Переход экранов AnimatedContent, раскрытие advanced AnimatedVisibility, перестановка animateItem. Постоянного декоративного движения нет. M3 сохраняет штатные ripple/state layers.
- Основные touch targets ≥48 dp; иконки имеют смысловые descriptions, switch обозначает правило. Названия и package names переносятся, без обрезки до одной строки.
- Приоритет меняется стрелками вместо собственного drag/drop: та же функция доступна через TalkBack/клавиатуру и не требует дополнительной библиотеки.

Bottom navigation содержит два раздела — правила и настройки. Back из настроек возвращает к правилам; back из редактора проверяет несохранённые изменения; bottom sheets/dialogs обрабатывают dismiss. Нет кастомного перехвата системного back в обработчике ссылок.

Adaptive icon: индиго background, вектор маршрута в foreground, тот же alpha silhouette как monochrome для themed icons. Browser/globe branding не используется. Без растровых слоёв и ручных масок; launcher применяет свою форму.

В UI явно показаны unknown source, недоступное приложение, пустой список, загрузка и ошибки конфигурации. Импорт не стирает данные без выбора пользователя. Каталог приложений обновляется при возвращении в Activity.

Официальные рекомендации: [Material 3 Compose](https://developer.android.com/develop/ui/compose/designsystems/material3), [Material Android / Compose](https://m3.material.io/develop/android/jetpack-compose), [M3 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3), [adaptive icons](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive).
