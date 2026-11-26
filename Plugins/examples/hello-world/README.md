# Hello world plugin

Минимальный пример плагина для Irongram: жизненный цикл, декларативные настройки,
собственные `SharedPreferences`, хук отправки сообщений и подписка на события приложения.

## Что делает

* при загрузке показывает тост и пишет в лог;
* если включена настройка **Append a suffix** — добавляет суффикс к исходящим текстовым сообщениям
  (в начало или в конец, зависит от селектора **Position**);
* логирует события `app_start` / `app_resume` / `app_pause`;
* на экране плагина показывает свои настройки (switch, поле ввода, селектор, подпись).

## Сборка

```sh
# один раз, чтобы получить classes.jar приложения
./gradlew :TMessagesProj:assembleDebug

export ANDROID_HOME=/path/to/android-sdk
./build.sh
```

Результат — `build/hello-world.plugin`.

## Установка

Настройки Nekogram → **Plugins** → **Install from file** → выбрать `hello-world.plugin`.
После установки плагин включён; тап по строке включает/выключает его, долгое нажатие —
меню действий (настройки, перезагрузка, удаление).

## Структура

```
plugin.json                              метаданные (id, name, entrypoint, version, ...)
src/com/example/helloworld/HelloPlugin.java
build.sh                                 javac + d8 + zip
```

`plugin.json` ссылается на класс `com.example.helloworld.HelloPlugin`, который наследует
`tw.nekomimi.nekogram.plugins.BasePlugin`.
