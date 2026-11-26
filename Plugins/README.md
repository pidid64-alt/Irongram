# Система плагинов Irongram

Порт идеи и архитектуры системы плагинов [exteraGram](https://github.com/exteraSquad/exteraGram)
под Irongram (Nekogram).

Отличие в реализации: в exteraGram плагины — это Python‑скрипты, которые исполняет движок на
Chaquopy. Здесь движок самодостаточный: плагин — это обычный JVM‑байткод (`classes.dex`), который
загружается `DexClassLoader`‑ом внутри процесса приложения. Никаких сторонних рантаймов, нативных
библиотек и сетевых зависимостей, поэтому система работает на любой сборке Irongram.

---

## 1. Что уже есть

| Возможность | Статус |
|---|---|
| Установка плагина из файла `.plugin` | ✅ |
| Список плагинов, включение/выключение, удаление, перезагрузка | ✅ |
| Хранилище настроек на каждый плагин | ✅ |
| Декларативные настройки (switch, input, selector, header, divider, text, custom view) | ✅ |
| Собственный экран настроек плагина (фрагмент) | ✅ |
| Хук отправки сообщений (изменить/отменить) | ✅ |
| События жизненного цикла приложения (`app_start`, `app_resume`, `app_pause`) | ✅ |
| Подписка на любые `NotificationCenter`‑уведомления с автоотпиской | ✅ |
| Safe mode + автоотключение упавшего плагина | ✅ |
| Песочница / проверка подписи | ❌ (плагин исполняется в процессе приложения с полными правами) |
| Python‑плагины exteraGram | ❌ (другой формат API) |
| Хуки произвольных методов (Xposed‑подобные) | ❌ (см. «Ограничения») |

Точки входа в UI: **Настройки Nekogram → Plugins** (и поиск по настройкам).

---

## 2. Структура кода

```
TMessagesProj/src/main/java/tw/nekomimi/nekogram/plugins/
├── PluginsConstants.java      константы (папки, имена файлов, лимиты, события)
├── PluginsController.java     движок: скан, загрузка, включение, установка, safe mode
├── PluginInstaller.java       разбор и распаковка .plugin архивов
├── PluginInfo.java            метаданные из plugin.json
├── BasePlugin.java            базовый класс плагина (публичный API)
├── PluginContext.java         фасад: настройки, хуки, логи, observers, потоки
├── PluginPreferences.java     типизированный SharedPreferences на плагин
├── PluginsHooks.java          реестр хуков и их диспетчеризация
├── InstalledPlugin.java       состояние установленного плагина (для движка и UI)
├── settings/                  декларативные настройки
│   ├── PluginSettingItem.java
│   ├── PluginSettingSwitch.java
│   ├── PluginSettingInput.java
│   ├── PluginSettingSelector.java
│   ├── PluginSettingHeader.java
│   ├── PluginSettingDivider.java
│   ├── PluginSettingText.java
│   └── PluginSettingCustom.java
└── ui/
    ├── PluginsActivity.java        экран списка плагинов
    ├── PluginSettingsActivity.java экран настроек плагина
    └── PluginCellFactory.java      ячейка списка (имя, версия/автор/ошибка, переключатель)
```

Интеграция в клиент (минимальная, 3 точки):

1. `ApplicationLoader.onCreate()` → `PluginsController.getInstance().init()`.
2. `SendMessagesHelper.sendMessage(SendMessageParams)` → `PluginsHooks.dispatchMessageSending(...)`.
3. `NekoSettingsActivity` → строка «Plugins» + запись в поиске настроек.

---

## 3. Формат плагина

`.plugin` — это обычный ZIP‑архив:

```
plugin.json      обязателен
classes.dex      обязателен (скомпилированный код плагина)
icon.png         необязателен (зарезервирован под будущий UI)
```

`plugin.json`:

```json
{
  "id": "com.example.hello",
  "name": "Hello world",
  "entrypoint": "com.example.hello.HelloPlugin",
  "version": "1.0",
  "author": "Someone",
  "description": "Добавляет суффикс к исходящим сообщениям",
  "apiVersion": 1,
  "minAppVersionCode": 7175
}
```

| Поле | Обязательное | Описание |
|---|---|---|
| `id` | да | уникальный идентификатор, `[a-zA-Z0-9._-]`, до 64 символов; используется как имя каталога |
| `name` | да | отображаемое имя |
| `entrypoint` | да | полное имя класса, наследника `BasePlugin`, с публичным конструктором без аргументов |
| `version` | нет | по умолчанию `1.0` |
| `author`, `description` | нет | показываются на экране плагина |
| `apiVersion` | нет | по умолчанию `1`; если больше, чем `PluginsConstants.API_VERSION`, плагин будет отклонён |
| `minAppVersionCode` | нет | минимальный `versionCode` приложения |

Лимиты: архив ≤ 16 МБ, `plugin.json` ≤ 128 КБ, `classes.dex` ≤ 16 МБ. Из архива
извлекаются **только** эти три записи, поэтому zip‑slip невозможен.

Установленные плагины лежат в `files/plugins/<id>/`, настройки — в `SharedPreferences`
`plugin_prefs_<id>`, общее состояние — в `SharedPreferences` `plugins`.

---

## 4. API плагина

### 4.1 Точка входа

```java
public class HelloPlugin extends BasePlugin {

    @Override
    public void onLoad() {              // main thread
        getContext().toast("Hello!");
        getContext().log("loaded");
    }

    @Override
    public void onUnload() {            // main thread, хуки и observers уже снимаются автоматически
    }

    @Override
    public void onAppEvent(String event) {  // app_start / app_resume / app_pause
    }

    @Override
    public List<PluginSettingItem> createSettings() {
        return Arrays.asList(new PluginSettingSwitch("enabled", "Enable", false));
    }

    @Override
    public void onSettingChanged(String key) {
    }

    @Override
    public BaseFragment createSettingsFragment() {  // необязательно
        return null;
    }
}
```

Плагин загружается внутрь процесса приложения и видит все классы клиента
(`org.telegram.*`, `tw.nekomimi.*`), поэтому он может вызывать любые API клиента напрямую.
Обратная сторона: изоляции нет, ошибка в плагине — это ошибка в приложении. Движок
страхует только свои вызовы: исключения из хуков и `onLoad` логируются, плагин отключается.

### 4.2 `PluginContext`

```java
PluginContext ctx = getContext();

ctx.getInfo();                  // PluginInfo
ctx.getId(); ctx.getName(); ctx.getVersion();
ctx.getApplicationContext();    // Context приложения
ctx.getPluginDirectory();       // File каталога плагина
ctx.getPreferences();           // PluginPreferences
ctx.getHooks();                 // PluginsHooks
ctx.runOnUiThread(runnable);
ctx.runOnUiThread(runnable, delayMs);
ctx.runOnBackgroundThread(runnable);
ctx.log("..."); ctx.log("...", throwable);
ctx.toast("...");
ctx.getNotificationCenter(account);
ctx.observe(account, delegate, NotificationCenter.messagesDidLoad, ...); // автоотписка при выгрузке
ctx.getAppVersionCode(); ctx.getAppVersionName();
```

`PluginPreferences`: `get/put` для `boolean`, `int`, `long`, `float`, `String`, плюс
`contains`, `remove`, `getAll`, `clear`.

### 4.3 Хуки

```java
// Отправка сообщения: можно поправить текст/сущности или отменить отправку.
getContext().getHooks().onMessageSending((ctx, params, account) -> {
    if (params.message != null) params.message += " 🐾";
    return false; // true — не отправлять
});

// События приложения.
getContext().getHooks().onAppEvent((ctx, event) -> ctx.log("event " + event));
```

Хуки регистрируются в `onLoad()` (main thread) и снимаются автоматически при выгрузке плагина.
Диспетчеризация никогда не пробрасывает исключения наружу.

### 4.4 Декларативные настройки

```java
new PluginSettingHeader("Заголовок")
new PluginSettingSwitch(key, text, defaultValue)
new PluginSettingInput(key, text, hint, defaultValue)                 // диалог ввода
new PluginSettingInput(key, text, hint, defaultValue, multiline, maxLength)
new PluginSettingSelector(key, text, CharSequence[] entries, String[] values, int defaultIndex)
new PluginSettingText(text)                                           // подпись
new PluginSettingDivider(text)                                        // разделитель + подпись
new PluginSettingCustom(key, provider)                                // своя View
```

Значения хранятся в настройках плагина, после изменения вызывается
`BasePlugin.onSettingChanged(key)` — плагину достаточно читать значения из
`getContext().getPreferences()`.

---

## 5. Безопасность и восстановление

* **Safe mode** (переключатель на экране плагинов): при старте плагины не загружаются вообще.
  Полезно, если плагин ломает запуск приложения.
* **Автоотключение**: если `onLoad()` бросил исключение, плагин помечается ошибкой,
  отключается и больше не загружается до ручного включения.
* **Изоляция ошибок хуков**: исключение в хуке логируется (`FileLog`, тег `[plugin <id>]`),
  остальные плагины и клиент продолжают работу.
* Плагины исполняются с правами приложения. Устанавливайте только те плагины, которым доверяете.

---

## 6. Как собрать плагин

Нужны: JDK 17+, Android SDK (build-tools с `d8`), собранный проект (чтобы взять classpath
приложения).

```sh
./gradlew :TMessagesProj:assembleDebug          # один раз, чтобы получить classes.jar
export ANDROID_HOME=/path/to/android-sdk
cd Plugins/examples/hello-world
./build.sh                                       # соберёт hello-world.plugin
```

Затем: Настройки Nekogram → Plugins → **Install from file** → выбрать `.plugin`.

Скрипт `build.sh` делает ровно это:

```sh
javac -classpath "$ANDROID_JAR:$APP_CLASSES" -d build/classes $(find src -name '*.java')
"$D8" --lib "$ANDROID_JAR" --output build/dex $(find build/classes -name '*.class')
zip hello-world.plugin plugin.json build/dex/classes.dex
```

Важно: **не** включайте в `classes.dex` классы самого приложения (их даёт родительский
classloader), иначе получите конфликт дублирующихся классов.

---

## 7. Отличия от exteraGram и ограничения

* В exteraGram плагины — Python, движок (`PythonPluginsEngine`) на Chaquopy, SDK плагинов
  обновляется по сети, есть каталог плагинов и анализатор. Здесь этого нет.
* Хуки здесь — не перехват произвольных методов, а явные точки расширения, вызываемые
  клиентом. Полноценный method hooking на ART требует Xposed‑подобного рантайма
  (`de.robv.android.xposed`), это отдельная большая задача.
* Python‑плагины exteraGram не совместимы: API другой. Совместимость возможна только
  отдельным движком.

Возможные следующие шаги:

1. Установка `.plugin` прямо из сообщения/файла Telegram (меню документа).
2. Каталог плагинов (список репозиториев, обновления, версии).
3. Иконки плагинов и группировка по автору.
4. Дополнительные хуки: меню чата, меню сообщения, меню профиля, исходящие апдейты.
5. Проверка подписи архива и «разрешить только из каталога».
