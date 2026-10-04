# Иконка BrowserRouter

Одобренный вариант: пересечение Firefox/Bearium/Chrome с эффектом временного сдвига.

В Android используется исходная цветная графика на прозрачном PNG foreground, фон `#10121C` и отдельный монохромный VectorDrawable. Слои имеют viewport 108×108; значимая графика помещается в центральный круг диаметром 65 dp. Форму маски и цвет monochrome задаёт launcher.

- `browserrouter-color-original.png`: исходная цветная графика.
- `browserrouter-color.svg`: самостоятельный векторный экспорт, 64 цвета.
- `browserrouter-monochrome.svg`: очищенные векторные контуры без растровой картинки.
- `browserrouter-adaptive-foreground.svg`: foreground с исходным PNG внутри SVG, сохраняющий исходный вид.
- `browserrouter-adaptive-foreground-vector.svg`: полностью векторный foreground.
- `browserrouter-adaptive-background.svg`, `browserrouter-adaptive-monochrome.svg`: фон и монохромный слой.
- `browserrouter-regular.png`, `.svg`: обычная квадратная цветная версия. В regular SVG встроен PNG.
- `browserrouter-icon-preview.png`: одобренный предпросмотр масок и тематических цветов.

Runtime: `app/src/main/res/drawable-nodpi/browserrouter_foreground.png`, `drawable/ic_launcher_foreground.xml`, `drawable/ic_launcher_background.xml`, `drawable/ic_launcher_monochrome.xml`, `mipmap-anydpi-v26/ic_launcher.xml`. Монохромный слой содержит реальные кривые; векторная цветная версия сохранена для экспорта, Android использует PNG для точного сохранения градиентов и эффекта сдвига.
