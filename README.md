# NS (NeverSleep) — каркас музыкального приложения

Android-приложение в стиле Material You. Два акцентных цвета — **primary** и **tertiary** — берутся из системной палитры (Android 12+, цвета обоев) и передаются в интерфейс через мост `NSBridge`. На Android 11 и младше работает статичный Material3-фолбэк.

Интерфейс — один файл `app/src/main/assets/index.html`, запускается внутри WebView с адреса `https://ns.local/assets/index.html` (не `file://`, поэтому нормально работают localStorage и Web API).

## Что уже работает

- Нижняя навигация из двух пунктов: **Поиск** и **Любимое**, оба кликабельные.
- Поиск: текстовый ввод + фильтр по жанрам, живой результат по демо-каталогу (8 треков).
- Нажатие на трек или на сердечко добавляет/удаляет его в плейлист «Любимое».
- Плейлист сохраняется в `localStorage` — выживает перезапуск приложения.
- Системные цвета подхватываются при запуске и обновляются при смене темы/обоев (`onConfigurationChanged`, `onResume`).
- Кнопка Android «назад»: сначала сбрасывает фильтр/возвращает в поиск, потом закрывает приложение.

Пока нет: реального воспроизведения звука, загрузки локальных файлов и сетевого каталога. Это следующий этап.

## Структура

```
ns-app/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/index.html                     ← весь интерфейс (HTML+CSS+JS)
│       ├── java/dev/neversleep/ns/MainActivity.java ← мост системных цветов
│       └── res/                                   ← имя, тема, иконка
├── .github/workflows/build-apk.yml                ← облачная сборка
├── build.gradle, settings.gradle, gradle.properties
├── upload.sh                                      ← загрузка на GitHub через API
└── README.md
```

---

## Шаг 1. Создай репозиторий на GitHub

1. Открой https://github.com/new (нужен аккаунт; если его нет — https://github.com/signup).
2. **Repository name**: `ns-neversleep` (можно другое латинское имя).
3. Выбери **Public**, остальные галочки (`Add a README`, `.gitignore`, license) **не ставь**.
4. Нажми **Create repository**. Пока репозиторий пустой — это нормально.

## Шаг 2. Загрузи файлы

На телефоне нет `git`, поэтому выбери один из двух способов.

### Способ А (быстрый): токен + скрипт

1. GitHub → аватар справа вверху → **Settings** → внизу слева **Developer settings** → **Personal access tokens** → **Tokens (classic)** → **Generate new token (classic)**.
2. Note: `ns-upload`, Expiration: `7 days`, поставь галочку только **`repo`** → **Generate token**. Скопируй токен (начинается с `ghp_`).
3. В рабочей папке выполни:

   ```sh
   cd /data/user/0/ytk.youcode.dev/files/workspace
   GH_TOKEN='вставь_сюда_токен' sh ns-app/upload.sh
   ```

4. Скрипт сам создаст репозиторий (если его ещё нет) и зальёт все файлы с правильной структурой папок. В конце выведет ссылку на репозиторий.
5. **Токен больше никому не показывай.** После загрузки можно вернуться в настройки GitHub и нажать **Revoke** — автосборке он не нужен.

> Если создал репозиторий вручную на шаге 1, скрипт просто допишет файлы туда же.

### Способ Б (без токена, через браузер)

В репозитории жми **Add file → Create new file**. В поле имени файла можно ввести путь с `/` — папки создадутся сами. Создай по очереди эти 11 файлов, вставив содержимое соответствующих файлов из `ns-app/`:

```
.github/workflows/build-apk.yml
app/build.gradle
app/src/main/AndroidManifest.xml
app/src/main/assets/index.html
app/src/main/java/dev/neversleep/ns/MainActivity.java
app/src/main/res/drawable/ic_launcher.xml
app/src/main/res/values/strings.xml
app/src/main/res/values/styles.xml
build.gradle
gradle.properties
settings.gradle
```

Каждый раз внизу жми зелёную кнопку **Commit changes**. Первый файл `.github/workflows/build-apk.yml` создаст папки `.github/workflows` автоматически.

Есть компьютер или Termux с git — третий вариант: `git clone <ссылка>`, скопируй содержимое `ns-app/` в папку репозитория, затем `git add . && git commit -m "NS scaffold" && git push`.

## Шаг 3. Собери APK

1. Открой репозиторий → вкладка **Actions**. Если GitHub спросит разрешить workflows — разреши.
2. Сборка запускается автоматически после загрузки файлов. Можно также нажать **Собрать NS APK** → **Run workflow**.
3. Жди 3–7 минут до зелёной галочки. Открой задание → внизу **Artifacts** → `ns-apk` → скачай ZIP.
4. Распакуй ZIP (в «Файлах»: долгое нажатие → Извлечь), найди `app-debug.apk`.
5. Нажми APK → **Разрешить установку из неизвестных источников** → **Установить**.

APK debug, подписан отладочным ключом — для своего телефона достаточно, в Google Play такой не примут.

## Шаг 4. Обновление

Поменял `index.html` или Java-код → повтори шаг 2 (скрипт сам подхватит SHA существующих файлов), затем Actions соберёт новую APK. Версию приложения меняй в `app/build.gradle` (`versionCode`, `versionName`).

## Как работают два акцента

`MainActivity` вызывает `DynamicColors.applyToActivityIfAvailable()` и читает атрибуты темы `colorPrimary` и `colorTertiary` (плюс surface/onSurface). Мост `NSBridge.colors()` отдаёт их в JS как JSON; функция `nsApplySystemColors()` расставляет CSS-переменные `--a1`, `--a2`, `--a1c`, `--a2c` и т.д. Первый акцент — активная вкладка и поиск, второй — сердечки, чипсы, бейдж плейлиста и градиент логотипа. При смене темы/обоев `onConfigurationChanged()` пересылает палитру заново.

Если мост недоступен (открыть файл напрямую в браузере), используется встроенная Material3-палитра и `prefers-color-scheme`.

## Если что-то не так

- **Красный крестик в Actions** — открой шаг с ошибкой и пришли мне текст ошибки.
- **HTTP 401/403 от скрипта** — токен просрочен или у него нет права `repo`.
- **«Приложение не установлено»** — удали прежнюю версию с пакетом `dev.neversleep.ns` и попробуй снова.
- **Цвета не меняются** — dynamic color работает на Android 12+; на более старых версиях ожидается статичная палитра.
