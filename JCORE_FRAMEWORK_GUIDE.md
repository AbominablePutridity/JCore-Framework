# JCore Framework — Полный свод правил и техническая документация

> **Назначение этого документа:** данный файл — единый источник правды об устройстве фреймворка JCore.
> Он написан так, чтобы разработчик (человек или нейросеть), открыв только этот файл, мог корректно
> проектировать и создавать веб-приложения на JCore без изучения исходников фреймворка.
> Все описания сверены с фактическим кодом в `JCore/src/main/java/vendor/`.

---

## Оглавление

1. [Что такое JCore](#1-что-такое-jcore)
2. [Структура проекта и карта исходников](#2-структура-проекта-и-карта-исходников)
3. [ОБЯЗАТЕЛЬНЫЕ архитектурные правила](#3-обязательные-архитектурные-правила)
4. [Транспортный протокол (TCP/IP)](#4-транспортный-протокол-tcpip)
5. [Компонент DI — внедрение зависимостей](#5-компонент-di--внедрение-зависимостей)
6. [Компонент EntityOrm — работа с базой данных](#6-компонент-entityorm--работа-с-базой-данных)
7. [Компонент ControllerComponent — сервер и роутинг](#7-компонент-controllercomponent--сервер-и-роутинг)
8. [Компонент Security — защита роутов](#8-компонент-security--защита-роутов)
9. [Жизненный цикл приложения (порядок запуска)](#9-жизненный-цикл-приложения-порядок-запуска)
10. [Пошаговый рецепт: создание веб-приложения на JCore](#10-пошаговый-рецепт-создание-веб-приложения-на-jcore)
11. [Полный сквозной пример приложения](#11-полный-сквозной-пример-приложения)
12. [Важные нюансы, ограничения и подводные камни](#12-важные-нюансы-ограничения-и-подводные-камни)
13. [Тестирование и отладка](#13-тестирование-и-отладка)
14. [Чек-лист типичных ошибок](#14-чек-лист-типичных-ошибок)

---

## 1. Что такое JCore

**JCore** — легковесный самописный Java-фреймворк для создания простых веб-приложений и API-сервисов.
Транспортным уровнем является **сырой TCP/IP через `ServerSocket`** (НЕ HTTP-сервер): клиент подключается
сокетом, отправляет текстовый запрос специального формата, сервер синхронно возвращает строку-ответ
и закрывает соединение.

Фреймворк состоит из 4 главных компонентов (все лежат в `src/main/java/vendor/`):

| Компонент | Пакет | Ответственность |
|---|---|---|
| **DI** | `vendor.DI` | Контейнер зависимостей (`ContainerDI`) + конфиг регистрации бинов (`ConfigDI`) |
| **EntityOrm** | `vendor.EntityOrm` | ORM поверх JDBC: сущности, метаданные, автосоздание таблиц, вставка данных, безопасные параметризованные запросы, сериализация ResultSet → List/JSON |
| **ControllerComponent** | `vendor.ControllerComponent` | TCP-сервер (`Server`), пул потоков, разбор запроса клиента, роутинг запросов к методам контроллеров через рефлексию (`Controller`) |
| **Security** | `vendor.Security` | Аутентификация по логину/паролю (BCrypt) и авторизация по ролям для защиты роутов |

**Технологический стек:** Java 21, Maven (`JCore/pom.xml`), PostgreSQL JDBC driver 42.7.9,
BCrypt (`at.favre.lib:bcrypt:0.10.2`). Главный класс запуска: `com.mycompany.jcore.JCore`
(настраивается в `pom.xml`, свойство `exec.mainClass`).

---

## 2. Структура проекта и карта исходников

```
framework/
├── README.md                        # краткая документация автора (уступает этому файлу в деталях)
├── JCORE_FRAMEWORK_GUIDE.md         # ЭТОТ ФАЙЛ — главный свод правил
└── JCore/
    ├── pom.xml                      # Maven: Java 21, postgresql, bcrypt
    └── src/main/java/
        ├── vendor/                          # === КОД САМОГО ФРЕЙМВОРКА (не менять без необходимости) ===
        │   ├── JCoreMeta.java               # ASCII-логотип при старте сервера (logoRenderer())
        │   ├── DI/
        │   │   ├── ContainerDI.java         # статический DI-контейнер (HashMap<Class<?>, Object>)
        │   │   └── ConfigDI.java            # конфиг регистрации бинов; setBeans() вызывается в main()
        │   ├── EntityOrm/
        │   │   ├── ConfigJDBC.java          # настройки подключения к БД (url, user, password)
        │   │   ├── Entity.java              # абстрактный базовый класс всех сущностей
        │   │   ├── Repository.java          # абстрактный базовый класс всех репозиториев
        │   │   ├── EntityInfo.java          # метаданные сущности (класс, поля, связи)
        │   │   ├── FieldNameWithType.java   # имя/тип/значение одного поля
        │   │   ├── RelationField.java       # описание внешнего ключа (связи между сущностями)
        │   │   └── DataSerializer.java      # ResultSet → List<Map>; List<Map> → JSON-строка
        │   ├── ControllerComponent/
        │   │   ├── Controller.java          # реестр контроллеров + запуск методов по роуту (рефлексия)
        │   │   └── Connection/
        │   │       └── Server.java          # TCP-сервер: ServerSocket, очередь, 4 воркера, парсинг запросов
        │   └── Security/
        │       └── Security.java            # checkRole(), hashPassword(), BCrypt
        └── com/mycompany/jcore/             # === КОД ПРИЛОЖЕНИЯ РАЗРАБОТЧИКА ===
            ├── JCore.java                   # main(): стартовая последовательность приложения
            ├── entities/                    # СЛОЙ 1: сущности (Person, Car)
            ├── repository/                  # СЛОЙ 2: репозитории (PersonRepository, CarRepository)
            ├── controller/                  # СЛОЙ 4: контроллеры (PersonController)
            └── client/
                └── FileClient.java          # готовый программный клиент для отправки запросов и файлов
```

> **Правило:** пакет `com.mycompany.jcore.service/` (СЛОЙ 3: сервисы) в примере отсутствует,
> но его ОБЯЗАТЕЛЬНО нужно создавать в каждом приложении — см. раздел 3.

---

## 3. ОБЯЗАТЕЛЬНЫЕ архитектурные правила

### 3.1. Слоистая архитектура — строго обязательна

Любое веб-приложение на JCore должно строиться из **четырёх архитектурных слоёв**
для масштабируемости и удобства сопровождения. Нарушать порядок зависимостей между слоями запрещено:

```
┌──────────────────────────────────────────────────────────────┐
│  СЛОЙ 4: CONTROLLERS (пакет ...controller)                    │
│  Взаимодействие и передача данных по транспортному каналу     │
│  (TCP) от клиента к серверу. Разбор params/binaryFiles,       │
│  проверка доступа (Security), вызов сервиса, возврат строки.  │
│  БЕЗ бизнес-логики. БЕЗ прямых обращений к репозиториям.      │
├──────────────────────────────────────────────────────────────┤
│  СЛОЙ 3: SERVICES (пакет ...service)                          │
│  Вся бизнес-логика приложения. Валидация данных, вычисления,  │
│  оркестрация нескольких репозиториев, формирование ответа     │
│  (в т.ч. JSON через DataSerializer).                          │
│  Имеет объекты РЕПОЗИТОРИЕВ, внедрённые через конструктор.    │
├──────────────────────────────────────────────────────────────┤
│  СЛОЙ 2: REPOSITORIES (пакет ...repository)                   │
│  Управление сущностями (по сути DAO-классы). Наследуются от   │
│  Repository<ENTITY, DTO>. Методы выборки/изменения данных     │
│  через executeSQL / executeUpdate / setData. БЕЗ бизнес-логики│
├──────────────────────────────────────────────────────────────┤
│  СЛОЙ 1: ENTITIES (пакет ...entities)                         │
│  Архитектура модели данных. Классы-наследники Entity,         │
│  публичные поля = колонки таблицы БД. Никакой логики.         │
└──────────────────────────────────────────────────────────────┘
```

**Правила направления зависимостей:**

- Controller → Service → Repository → Entity. Обратные зависимости запрещены.
- Controller **не имеет права** содержать SQL или бизнес-логику — только:
  приём параметров → (опционально `checkRole`) → вызов метода сервиса → возврат результата строкой.
- Service **не знает** о существовании контроллеров и транспортного протокола.
- Repository работает только со своей сущностью и SQL.
- Entity — чистое описание данных (поля + конструктор со Statement + регистрация связей).

### 3.2. Запрет сторонних библиотек

Фреймворк имеет **все необходимые модули на борту**: DI-контейнер, ORM, HTTP-подобный транспорт,
JSON-сериализацию, безопасность с хешированием паролей, клиент для передачи файлов.

> **Подключение сторонних модулей и библиотек в проект при разработке веб-приложений — НЕЖЕЛАТЕЛЬНО.**

Разрешено использовать только:

1. Стандартную библиотеку JDK (`java.*`);
2. Уже подключённые в `pom.xml` зависимости фреймворка: PostgreSQL JDBC driver и BCrypt;
3. Собственные классы внутри слоёв приложения.

Не добавляйте Jackson/Gson (есть `DataSerializer.convertToJson`), Spring/Lombok/Hibernate и т.п.
Если не хватает функциональности — реализуйте её своими руками в слое сервисов.

### 3.3. Правила именования

| Что | Правило | Пример |
|---|---|---|
| Сущность | Существительное, PascalCase, имя = имя таблицы БД | `Post`, `UserAccount` |
| Поле-внешний ключ | `[имяКлассаНаКотороеСсылаемся]Id` в lowerCamelCase | ссылка на `Person` → поле `personId` типа `Long` |
| Репозиторий | `<Сущность>Repository` | `PostRepository` |
| Сервис | `<Сущность>Service` или `<Домен>Service` | `PostService` |
| Контроллер | `<Сущность>Controller` — имя класса ЧАСТЬ РОУТА | `PostController` |
| Экшен контроллера | `<глагол><Сущность>Action` — имя метода ЧАСТЬ РОУТА | `createPostAction` |

Имя класса контроллера и имя метода экшена **напрямую образуют адрес роута**, поэтому они
часть публичного контракта API — менять их после релиза нельзя.

---

## 4. Транспортный протокол (TCP/IP)

### 4.1. Общая схема

```
КЛИЕНТ                                СЕРВЕР JCore
  │  connect(host, port 8082)            │
  │─────────────────────────────────────►│  accept() → сокет кладётся в BlockingQueue
  │  ЗАПРОС (текст [+ бинарные файлы])   │  один из 4 воркеров разбирает запрос
  │─────────────────────────────────────►│  → роутинг → экшен контроллера → сервис
  │  ОТВЕТ (одна строка)                 │
  │◄─────────────────────────────────────│  PrintWriter.println(result.toString())
  │  close                               │  clientSocket.close()
```

Модель: **один запрос = одно соединение**. Клиент подключается, отправляет полный запрос,
получает ровно одну строку ответа, соединение закрывается сервером. Keep-alive нет.
Кодировка текста — UTF-8.

### 4.2. Формат запроса

Запрос состоит из **текстовой части** и опциональной **бинарной части**.

**Текстовая часть:**

```text
ИмяКонтроллера/имяЭкшена<endl>параметр1<endl>параметр2<endl>...<endl>
```

- Первый элемент до первого `<endl>` — **роут**: `ИмяПростогоКлассаКонтроллера/имяМетода`.
- Всё, что дальше — параметры, они попадают в массив `String[] params` экшена.
- Разделитель параметров — литеральная строка `<endl>` (не перевод строки!).
- Параметров может быть сколько угодно (в т.ч. ноль).

**Бинарная часть (опциональна)** — идёт сразу после текстовой и начинается маркером `<BINARY>`:

```text
<BINARY>[int32 размер файла 1][байты файла 1][int32 размер файла 2][байты файла 2]...[int32 0]
```

- Размер каждого файла — 4 байта big-endian int (как `DataOutputStream.writeInt`).
- Список файлов завершается int-ом со значением `0`.
- Если файлы не нужны — бинарная часть не отправляется вовсе.

**Полный пример запроса с файлом:**

```text
PersonController/createPersonAction<endl>helloWorld!<endl>JCore!<endl><BINARY>[4 байта размера][содержимое jpg][int 0]
```

### 4.3. Блок аутентификации (если роут защищён Security)

Если экшен проверяет доступ через `checkRole(...)`, запрос **обязан** содержать параметр вида:

```text
<endl>логин<security>пароль<endl>
```

Например:

```text
PersonController/createPersonAction<endl>helloWorld!<endl>JCore!<endl>ivanov<security>pass<endl>
```

`Security` сканирует все параметры запроса, находит тот, что содержит подстроку `<security>`,
и разбивает его на логин (до маркера) и пароль (после). Этот параметр также остаётся обычным
элементом массива `params` — учитывайте это при обработке.

### 4.4. Формат ответа

Ответ — **одна строка** (`result.toString()`), отправляется через `PrintWriter.println(...)`.

- Для структурированных данных возвращайте JSON-строку, собранную методом
  `DataSerializer.convertToJson(List<Map<String, Object>>)` — клиентская сторона парсит её сама.
- Если экшен вернул `null` (или бросил исключение, которое дошло до `Controller`),
  клиент получит строку `ERROR: Controller returned null`.
- Если ни один контроллер не совпал с роутом — тоже `ERROR: Controller returned null`.

### 4.5. Передача тяжёлых данных — бинарные файлы (фото, видео, документы)

Второй параметр каждого экшена — `byte[][] binaryFiles` — существует специально для передачи
**тяжёлых данных** от клиента: фотографий, видео, аудио, документов и любых других бинарных
блобов. Рабочий пример реализации есть в самом проекте: контроллер
`com.mycompany.jcore.controller.PersonController#createPersonAction`, клиент
`com.mycompany.jcore.client.FileClient` (тестовый файл — `test_photo.jpg` в корне репозитория).

#### Почему массив двумерный

`byte[][]` — это **массив файлов**, где каждый элемент `byte[]` — содержимое одного файла:

```text
binaryFiles[0] -> byte[] первого файла (например, фото)
binaryFiles[1] -> byte[] второго файла (например, видео)
binaryFiles[N] -> byte[] N-го файла
```

Благодаря этому за **один запрос** клиент может загрузить сколько угодно файлов, а не один.
Если файлы не передавались — сервер передаст в экшен пустой массив (`byte[0][]`),
поэтому цикл по `binaryFiles` безопасен всегда.

#### Формат на проводе (повторение + детали)

Текстовая часть идёт первой и целиком, затем маркер `<BINARY>`, затем последовательность файлов,
затем терминатор:

```text
[текст: роут<endl>параметры<endl>[логин<security>пароль]<endl>]
<BINARY>
[int32 BE размер файла 1][байты файла 1]
[int32 BE размер файла 2][байты файла 2]
...
[int32 BE 0]   <- терминатор списка файлов
```

- Размер — обычный `int` (4 байта, big-endian), пишется `DataOutputStream.writeInt(int)`,
  читается `DataInputStream.readInt()`. Максимальный размер одного файла ≈ 2 ГБ (`Integer.MAX_VALUE`).
- Содержимое читается методом `readFully(byte[])` — сервер гарантированно дожидается всех байт файла.
- Отрицательный размер → сервер бросает `IOException` и разрывает соединение.
- Значение размера `0` означает «файлов больше нет».
- Маркер `<BINARY>` ищется в потоке побайтово (сканером с состоянием): если маркера нет до EOF,
  запрос считается чисто текстовым. Поэтому подстрока `<BINARY>` запрещена в текстовых параметрах.

#### Как отправить тяжёлые данные с клиента

**Способ 1 (рекомендуемый) — готовый класс `FileClient`:**

```java
FileClient client = new FileClient("127.0.0.1", 8082);

// Один или несколько файлов за один запрос:
String response = client.sendFile(
    "PersonController",            // имя класса контроллера (часть роута)
    "createPersonAction",          // имя экшена (часть роута)
    new String[]{"upload_photo"},  // текстовые параметры
    new File("photo.jpg"),         // файл 1
    new File("clip.mp4")           // файл 2 (и так сколько нужно)
);
System.out.println(response); // ответ сервера одной строкой
```

`FileClient` сам: формирует текстовую часть → пишет маркер `<BINARY>` → для каждого файла
пишет `writeInt(размер)` + содержимое → пишет терминатор `writeInt(0)` → flush → читает ответ до EOF.

**Способ 2 — свой клиент (точный протокол вручную):**

```java
try (Socket socket = new Socket("127.0.0.1", 8082)) {
    DataOutputStream out = new DataOutputStream(socket.getOutputStream());

    // 1) Текстовая часть ЦЕЛИКОМ (роут + параметры + security-блок, если нужен)
    out.write("PostController/uploadMediaAction<endl>my_video<endl>"
              .getBytes(StandardCharsets.UTF_8));

    // 2) Маркер начала бинарной части
    out.write("<BINARY>".getBytes(StandardCharsets.UTF_8));

    // 3) Файлы: [int размер][байты] на каждый
    byte[] video = Files.readAllBytes(Path.of("clip.mp4"));
    out.writeInt(video.length);
    out.write(video);

    byte[] preview = Files.readAllBytes(Path.of("preview.jpg"));
    out.writeInt(preview.length);
    out.write(preview);

    // 4) Терминатор списка файлов
    out.writeInt(0);
    out.flush();

    // 5) Читаем ответ до закрытия соединения сервером
    StringBuilder response = new StringBuilder();
    int b;
    while ((b = socket.getInputStream().read()) != -1) {
        response.append((char) b);
    }
}
```

#### Как принять и обработать тяжёлые данные в экшене

Эталонный пример из проекта (`PersonController.createPersonAction`):

```java
public String createPersonAction(String[] params, byte[][] binaryFiles) throws IOException {
    String result = "";

    // Текстовые параметры
    for (String param : params) {
        result += "param is -> " + param + "\r\n";
    }

    // Бинарные файлы: каждый элемент массива - отдельный файл целиком
    for (int i = 0; i < binaryFiles.length; i++) {
        byte[] file = binaryFiles[i];

        System.out.println("Получен файл #" + i + ", размер: " + file.length + " байт");

        // Пример: сохраняем полученные байты обратно в файл на диске
        try (FileOutputStream output = new FileOutputStream("photo_" + i + ".jpg")) {
            output.write(file);
        }

        result += "file #" + i + " size -> " + file.length + "\r\n";
    }

    return result; // клиенту уходит отчёт о принятых файлах
}
```

Обратите внимание: в примере проекта файл сохранялся в одно и то же имя `photo.jpg` — при
нескольких файлах/запросах они перезаписывали бы друг друга. В реальных приложениях
генерируйте уникальные имена (например, `UUID.randomUUID() + расширение`) и сохраняйте путь в БД.

#### Правила работы с тяжёлыми данными (обязательны к соблюдению)

1. **Порядок частей строгий:** весь текст (роут, параметры, security-блок) → `<BINARY>` → файлы → `int 0`.
   Нельзя вставлять файлы «между» параметрами или слать текст после маркера.
2. **Файлы грузятся в память целиком:** и клиент (`readAllBytes`), и сервер (`new byte[fileSize]`)
   держат файл в heap. Для очень больших видео учитывайте лимиты JVM (`-Xmx`) и не шлите
   файлы больше доступной памяти. Стриминговой передачи в фреймворке нет.
3. **Максимум ~2 ГБ на один файл** (ограничение int-размера). Больше — разбивайте на части
   несколькими запросами (чанковая загрузка отдельным upload-роутом).
4. **Один запрос = одно соединение:** тяжёлую загрузку выполняйте ОТДЕЛЬНЫМ запросом к
   специализированному upload-экшену (например, `MediaController/uploadVideoAction`),
   а метаданные/текст создавайте обычными текстовыми запросами. Не смешивайте в одном запросе
   бизнес-операцию и мегабайты данных без необходимости.
5. **Пул из 4 воркеров:** каждая загрузка занимает одного воркера на всё время приёма файла.
   Несколько одновременных тяжёлых аплоадов блокируют обработку остальных запросов —
   планируйте нагрузку с учётом этого.
6. **Сохранение в БД:** колонка типа `byte[]` мапится в `BYTEA`, но вставлять большие файлы
   через `setData()` нельзя (сломается сериализация — см. 12.2). Используйте
   `Entity.executeUpdate(sql, params)` c `setObject` для мелких блобов; для фото/видео
   предпочтительнее файловая система + хранение пути в таблице.
7. **Ответ после загрузки** — строка-отчёт (кол-во файлов, размеры, имена сохранённых),
   которую клиент парсит сам.

---

## 5. Компонент DI — внедрение зависимостей

Расположение: `vendor/DI/`. Два класса.

### 5.1. ContainerDI

Простейший контейнер: статическая карта `HashMap<Class<?>, Object>`.

```java
ContainerDI.register(Class<?> type, Object instance); // положить бин
<T> T bean = ContainerDI.getBean(Class<T> type);      // достать бин по типу
```

Особенности:

- Все методы статические — создавать объект контейнера не нужно.
- Ключ — точный тип класса. Регистрируйте и доставайте по одному и тому же классу.
- Один тип = один экземпляр (повторный `register` перезапишет бин). Фактически все бины — синглтоны.
- `getBean` вернёт `null`, если бин не зарегистрирован (NPE при использовании — следите за порядком
  регистрации в конфиге).

### 5.2. ConfigDI

Конфигурационный класс приложения. Статический метод `setBeans()` вызывается **самым первым**
в `main()` и заполняет контейнер системными бинами:

```java
public static void setBeans() throws SQLException {
    // 1. Маршрутизатор контроллеров
    ContainerDI.register(Controller.class, new Controller());

    // 2. Сервер (порт задаётся ЗДЕСЬ; по умолчанию 8082)
    ContainerDI.register(Server.class, new Server(ContainerDI.getBean(Controller.class), 8082));

    // 3. Подключение к БД (данные из ConfigJDBC)
    ContainerDI.register(Connection.class, new ConfigJDBC().getConnectionDB());

    // 4. Statement для выполнения SQL в сущностях/репозиториях/Security
    ContainerDI.register(Statement.class, ContainerDI.getBean(Connection.class).createStatement());

    // ===== ДАЛЕЕ — БИНЫ ПРИЛОЖЕНИЯ (регистрируете вы) =====
    // Порядок: сначала СУЩНОСТИ, затем РЕПОЗИТОРИИ, затем СЕРВИСЫ.
}
```

**Что и в каком порядке регистрирует разработчик приложения:**

```java
// 1) сущности (каждой нужен Statement)
ContainerDI.register(Person.class, new Person(ContainerDI.getBean(Statement.class)));
ContainerDI.register(Post.class,  new Post(ContainerDI.getBean(Statement.class)));

// 2) репозитории (каждому нужна его сущность из контейнера)
ContainerDI.register(PersonRepository.class, new PersonRepository(ContainerDI.getBean(Person.class)));
ContainerDI.register(PostRepository.class,  new PostRepository(ContainerDI.getBean(Post.class)));

// 3) сервисы (каждому нужны его репозитории из контейнера)
ContainerDI.register(PostService.class, new PostService(ContainerDI.getBean(PostRepository.class)));
```

**Контроллеры в DI НЕ регистрируются.** Они создаются вручную в `main()` и складываются напрямую
в список сервера (см. раздел 7.3), при этом зависимости (Statement, сервисы) передаются в конструктор
из DI-контейнера.

---

## 6. Компонент EntityOrm — работа с базой данных

Расположение: `vendor/EntityOrm/`. Это самописный ORM поверх JDBC, ориентированный на PostgreSQL.

### 6.1. Конфигурация подключения — ConfigJDBC

Перед первым запуском откройте `vendor/EntityOrm/ConfigJDBC.java` и настройте под свою БД:

```java
private String urlConnection = "jdbc:postgresql://localhost:5432/test1"; // строка подключения
private String userName = "postgres";                                     // пользователь БД
private String password = "root";                                         // пароль БД
```

Есть геттеры/сеттеры, но исторически значения правятся прямо в полях. По умолчанию в `pom.xml`
стоит драйвер PostgreSQL — можно заменить на любой другой реляционный драйвер, однако
генерируемый DDL использует синтаксис PostgreSQL (`SERIAL`, `TEXT`, `BYTEA`, `TIMESTAMPTZ`,
массивы `TEXT[]` и т.д.), поэтому **целевая СУБД — PostgreSQL**.

Метод `getConnectionDB()` возвращает `java.sql.Connection` (используется в `ConfigDI.setBeans()`).

### 6.2. Сущность — класс-наследник Entity

Абстрактный `Entity` даёт потомку:

- **Поля:** `public long id` (первичный ключ), `public List<RelationField> refs` (внешние ключи),
  приватный `Statement statement` (принимается в конструкторе и передаётся `super(statement)`).
- **Методы внутреннего использования** (вызываются системой через Repository, НЕ вызывайте их сами):
  - `initializeChild()` — через рефлексию собирает метаданные потомка (`EntityInfo`: класс,
    все объявленные в потомке поля с типами и значениями, список refs);
  - `createTable(EntityInfo)` — генерирует и выполняет DDL;
  - `insertData(EntityInfo)` — генерирует и выполняет INSERT.
- **Безопасные статические методы для работы с данными клиентов** (используйте их в репозиториях):
  - `executeSQL(String sql, Object[] params)` → `ResultSet` (SELECT; PreparedStatement,
    полная защита от SQL-инъекций; **вызывающий код обязан закрыть ResultSet**);
  - `executeUpdate(String sql, Object[] params)` → `int` (INSERT/UPDATE/DELETE; PreparedStatement;
    закрывает PreparedStatement сам);
  - `printResultSetSimple(ResultSet)` — отладочный вывод ResultSet в консоль.

**Правила описания сущности:**

1. Класс наследуется от `Entity`; конструктор принимает `Statement` и передаёт в `super(...)`.
2. Каждая колонка таблицы = **публичное поле** класса (читается рефлексией через `getDeclaredFields()`,
   т.е. только поля, объявленные непосредственно в классе-потомке; унаследованные `id`/`refs` колонками не становятся).
3. Поле `id` объявлять не нужно — оно уже есть в родителе и мапится в `id SERIAL PRIMARY KEY`.
4. Для связи «многие к одному» (N:1):
   - объявите поле `public Long <имяСвязуемогоКлассаLowerCamel>Id;`
   - в конструкторе добавьте `refs.add(new RelationField(<СвязуемыйКласс>.class, <это поле>));`
5. Генерируемая таблица: `CREATE TABLE IF NOT EXISTS <ИмяКласса> (id SERIAL PRIMARY KEY, <колонки>, FOREIGN KEY(personId) REFERENCES Person(id) ON DELETE CASCADE)`.
   Имя FK-колонки система выводит из имени связуемого класса (первая буква в нижний регистр + `Id`),
   поэтому поле сущности обязано называться точно так же.

**Пример — сущность без связей:**

```java
package com.mycompany.jcore.entities;

import java.sql.Statement;
import vendor.EntityOrm.Entity;

public class Person extends Entity {
    public String name;
    public String surname;

    public String login;     // для Security
    public String password;  // хранить ТОЛЬКО хеш (Security.hashPassword)
    public String role;      // роль для Security ("USER", "ADMIN", ...)

    public Person(Statement statement) {
        super(statement);
    }
}
```

**Пример — сущность со связью N:1 (у каждой машины есть владелец):**

```java
package com.mycompany.jcore.entities;

import java.sql.Statement;
import vendor.EntityOrm.Entity;
import vendor.EntityOrm.RelationField;

public class Car extends Entity {
    public String mark;
    public String color;
    public Long personId; // правило именования: Person -> personId

    public Car(Statement statement) {
        super(statement);
        refs.add(new RelationField(Person.class, personId)); // связь с персоной машины
    }
}
```

### 6.3. Маппинг типов Java → SQL (convertTypeToSqlType)

| Тип поля Java | Тип колонки PostgreSQL |
|---|---|
| `String` | `TEXT` |
| `int` / `Integer` | `INTEGER` |
| `long` / `Long` | `BIGINT` |
| `short` / `Short`, `byte` / `Byte` | `SMALLINT` |
| `float` / `Float` | `REAL` |
| `double` / `Double` | `DOUBLE PRECISION` |
| `boolean` / `Boolean` | `BOOLEAN` |
| `java.util.Date`, `java.sql.Date`, `java.time.LocalDate` | `DATE` |
| `java.sql.Time`, `java.time.LocalTime` | `TIME` |
| `java.sql.Timestamp`, `java.time.LocalDateTime` | `TIMESTAMP` |
| `java.time.Instant` | `TIMESTAMPTZ` |
| `byte[]` | `BYTEA` |
| `String[]` / `Integer[]` / `Long[]` | `TEXT[]` / `INTEGER[]` / `BIGINT[]` |
| любой другой тип | `TEXT` |

Используйте только типы из таблицы — иначе колонка станет `TEXT`.

### 6.4. Репозиторий — класс-наследник Repository

`Repository<ENTITY extends Entity, DTO extends Entity>` — абстракция над всеми репозиториями (DAO).

Готовые методы:

- `init()` — создаёт таблицу в БД на основе метаданных сущности (`initializeChild()` + `createTable()`).
  Вызывается **один раз при старте приложения** для каждого репозитория (см. раздел 9).
- `setData(DTO data)` — вставляет данные объекта в таблицу (`initializeChild()` + `insertData()`).
  ⚠️ Имеет важный нюанс с `id` — см. раздел 12.2. Для вставки данных, пришедших от клиента,
  предпочтителен `executeUpdate` в собственных методах репозитория.
- `getEntity()` — возвращает управляемый объект сущности.

Шаблон репозитория:

```java
package com.mycompany.jcore.repository;

import com.mycompany.jcore.entities.Post;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import vendor.EntityOrm.DataSerializer;
import vendor.EntityOrm.Entity;
import vendor.EntityOrm.Repository;

public class PostRepository extends Repository<Post, Post> {

    public PostRepository(Post entityClass) {
        super(entityClass);
    }

    // ==== Собственные методы выборки/изменения (DAO) ====

    /** Все посты. ResultSet закрывает DataSerializer. */
    public List<Map<String, Object>> findAll() throws SQLException {
        ResultSet rs = Entity.executeSQL(
            "SELECT * FROM Post ORDER BY id",
            new Object[]{}
        );
        return DataSerializer.serializeFromResultDataToList(rs);
    }

    /** Один пост по id. */
    public Map<String, Object> findById(long id) throws SQLException {
        ResultSet rs = Entity.executeSQL(
            "SELECT * FROM Post WHERE id = ? LIMIT 1",
            new Object[]{ id }
        );
        List<Map<String, Object>> rows = DataSerializer.serializeFromResultDataToList(rs);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Вставка записи с параметризованным запросом (безопасно для клиентских данных). */
    public int insert(String title, String body, Long authorId) throws SQLException {
        return Entity.executeUpdate(
            "INSERT INTO Post (title, body, personId) VALUES (?, ?, ?)",
            new Object[]{ title, body, authorId }
        );
    }

    public int deleteById(long id) throws SQLException {
        return Entity.executeUpdate("DELETE FROM Post WHERE id = ?", new Object[]{ id });
    }
}
```

**Жёсткие правила безопасности:**

- Любые значения, пришедшие от клиента (`params` контроллера), передаются в SQL **только**
  через плейсхолдеры `?` и массив параметров `executeSQL` / `executeUpdate`. Конкатенация
  клиентских строк в SQL запрещена (SQL-инъекции).
- Имена таблиц/колонок в SQL пишутся руками разработчиком — они клиенту не приходят.
- Внутренние методы `init()`/`setData()` существуют для схемы и сидирования — их нельзя
  дёргать по данным клиента.

### 6.5. DataSerializer — сериализация результатов

`vendor/EntityOrm/DataSerializer.java` — мост между JDBC и ответами клиенту:

- `serializeFromResultDataToList(ResultSet)` → `List<Map<String, Object>>`
  (ключ — имя колонки, значение — ячейка; **сам закрывает ResultSet**);
- `convertToJson(List<Map<String, Object>>)` → корректная JSON-строка `[{"col": value}, ...]`
  (строки — в кавычках, числа/boolean — как есть, null → `null`, остальное — через `toString()`);
- `printList(...)`, `printJson(...)` — отладочный вывод в консоль.

Типовой поток данных: `executeSQL` → `serializeFromResultDataToList` → бизнес-обработка в сервисе
→ `convertToJson` → возврат строки из контроллера клиенту.

---

## 7. Компонент ControllerComponent — сервер и роутинг

Расположение: `vendor/ControllerComponent/`.

### 7.1. Server — TCP-сервер

`vendor/ControllerComponent/Connection/Server.java`.

- Конструктор: `Server(Controller controllerPull, int port)` (бин создаётся в `ConfigDI`, порт 8082).
- `startServer()` — блокирующий запуск: печатает ASCII-логотип (`JCoreMeta.logoRenderer()`),
  открывает `ServerSocket(port)` и навсегда принимает подключения.
- **Потоковая модель:** главный поток только `accept()`-ит сокеты и кладёт их в
  `BlockingQueue<Socket>`; 4 фиксированных потока-воркера (`THREAD_POOL_SIZE = 4`) в бесконечном
  цикле берут сокеты из очереди и обрабатывают. Одновременно обрабатывается до 4 запросов,
  остальные ждут в очереди (очередь не ограничена).
- Обработка одного клиента (`handleClient`): прочитать запрос → распарсить → вызвать
  `controllerPull.startMethodByUrl(route, params, binaryFiles)` → отправить `result.toString()`
  строкой → закрыть сокет.
- Ошибки ввода-вывода по соединению логируются в консоль сервера, сервер продолжает работать.

### 7.2. Controller — реестр контроллеров и рефлексивный роутинг

`vendor/ControllerComponent/Controller.java`.

- Поле `public List<Object> declaredControllers` — список объектов-контроллеров приложения.
- Метод `startMethodByUrl(String resultUrl, String[] params, byte[][] binaryFiles)`:
  1. Делит роут по `/`: `parts[0]` — имя класса, `parts[1]` — имя метода.
  2. Линейно ищет в `declaredControllers` объект, у которого `getClass().getSimpleName()` равен `parts[0]`.
  3. Находит метод **строго с сигнатурой `(String[], byte[][])`** через
     `getMethod(parts[1], String[].class, byte[][].class)` и вызывает его:
     `method.invoke(controller, params, binaryFiles)`.
  4. Возвращает результат либо `null` (не найден контроллер/метод, или внутри было исключение —
     оно логируется как `CONTROLLER ERROR: ...`).

**Следствия (критично для написания экшенов):**

- Экшен **обязан** быть `public` и иметь сигнатуру **ровно**
  `public <тип> имяAction(String[] params, byte[][] binaryFiles)`
  — оба параметра обязательны, даже если файлы не используются. Метод с одним `String[] params`
  **не будет найден** (NoSuchMethodException → клиент получит `ERROR: Controller returned null`).
- Перегрузки методов с одинаковым именем не поддерживаются.
- Роут чувствителен к регистру: `PostController/createPostAction` ≠ `postcontroller/...`.
- Исключения внутри экшена доходят до `Controller`, глотаются и превращаются в `null`.
  Поэтому **экшен обязан сам перехватывать свои исключения** и возвращать осмысленную строку ошибки.

### 7.3. Регистрация контроллеров

Контроллеры не живут в DI. В `main()` после получения бина сервера:

```java
Server server = ContainerDI.getBean(Server.class);

server.controllerPull.declaredControllers.add(
    new PostController(ContainerDI.getBean(Statement.class),
                       ContainerDI.getBean(PostService.class))
);

server.startServer();
```

Зависимости контроллера (Statement для Security, сервисы) берутся из DI-контейнера
и передаются через конструктор контроллера.

---

## 8. Компонент Security — защита роутов

Расположение: `vendor/Security/Security.java`. Механика: аутентификация по логину+паролю (BCrypt)
и авторизация по роли, хранящимся в таблице пользователей в БД.

### 8.1. Как подключить к контроллеру

Контроллер **наследуется от `Security`** и передаёт `Statement` в `super(...)`:

```java
public class PersonController extends Security {

    public PersonController(Statement statement) { // Statement обязателен для Security
        super(statement);
    }

    public String createPersonAction(String[] params, byte[][] binaryFiles) {
        if (super.checkRole("Person", "login", "password", "role", "USER", params)) {
            // доступ разрешён — вызываем сервис и возвращаем результат
            return "...";
        }
        return super.returnException(); // стандартная строка ACCESS_DENIED
    }
}
```

### 8.2. Метод checkRole

```java
checkRole(
    String tableName,        // таблица пользователей, напр. "Person"
    String loginField,       // колонка логина,   напр. "login"
    String passwordField,    // колонка пароля (ХЕША BCrypt), напр. "password"
    String roleField,        // колонка роли,     напр. "role"
    String roleForChecking,  // роль, допущенная к роуту, напр. "USER" или "ADMIN"
    String[] clientParams    // параметры запроса клиента (ищем блок логин<security>пароль)
) -> boolean
```

Алгоритм внутри:

1. `extractLoginAndPasswordFromClientQuery(clientParams)` — ищет параметр с подстрокой
   `<security>` и делит его на логин/пароль.
2. Одним параметризованным (инъекционно-безопасным) запросом достаёт по логину хеш пароля и роль:
   `SELECT password, role FROM Person WHERE login = ? LIMIT 1`.
3. `checkHashedPassword(storedHash, password)` — сверяет пароль с BCrypt-хешем.
4. При совпадении пароля сравнивает роль из БД с `roleForChecking` (строгое равенство строк).
5. Возвращает `true` только если всё совпало; иначе `false`.

Действия разработчика при создании пользователя: пароль в БД класть **только** в виде хеша —

```java
person.password = Security.hashPassword("pass"); // BCrypt, cost 12
```

### 8.3. Требования к запросу и ответы

- Запрос к защищённому роуту **обязан** содержать блок `<endl>логин<security>пароль<endl>`
  (см. раздел 4.3). Без него `checkRole` вернёт `false`.
- `returnException()` возвращает строку:
  `"ACCESS_DENIED: ОШИБКА! Данные логина и пароля не верны, либо ваша роль не предусматривает получение данных по данному роуту!"`.
- Проверку `checkRole` выполняйте **до** обращения к сервисам — контроллер должен отказывать
  в доступе без выполнения бизнес-логики.

---

## 9. Жизненный цикл приложения (порядок запуска)

Стартовая последовательность в `main()` — **строго в этом порядке**:

```java
public class JCore {
    public static void main(String[] args) throws Exception {
        // (1) Инициализация DI-контейнера: системные бины + ваши сущности/репозитории/сервисы
        ConfigDI.setBeans();

        // (2) Создание таблиц: init() у КАЖДОГО репозитория.
        //     ПОРЯДОК ВАЖЕН: от справочных таблиц (без FK) к связующим!
        //     (Person раньше Car, т.к. Car ссылается на Person)
        ContainerDI.getBean(PersonRepository.class).init();
        ContainerDI.getBean(CarRepository.class).init();

        // (3) ОПЦИОНАЛЬНО: сидирование стартовых данных (например, администратора)
        //     Пароли — только через Security.hashPassword(...)
        //     См. нюанс про setData в разделе 12.2.

        // (4) Запуск сервера: берём бин, регистрируем контроллеры, стартуем
        Server server = ContainerDI.getBean(Server.class);
        server.controllerPull.declaredControllers.add(
            new PersonController(ContainerDI.getBean(Statement.class))
        );
        server.startServer(); // блокирует поток навсегда
    }
}
```

Повторные запуски безопасны: DDL использует `CREATE TABLE IF NOT EXISTS`.

---

## 10. Пошаговый рецепт: создание веб-приложения на JCore

Чек-лист создания нового приложения «с нуля» (пример домена — посты):

**Шаг 1. Конфигурация окружения**
- [ ] `pom.xml`: убедиться, что `exec.mainClass` указывает на ваш main-класс.
- [ ] `vendor/EntityOrm/ConfigJDBC.java`: вписать url/пользователя/пароль своей БД PostgreSQL.
- [ ] Создать пустую схему/базу данных.

**Шаг 2. Слой сущностей (`entities`)**
- [ ] Для каждой таблицы — класс `X extends Entity` с публичными полями-колонками
      (типы — только из таблицы маппинга 6.3).
- [ ] Конструктор `X(Statement statement) { super(statement); }`.
- [ ] Связи: поле `yId` типа `Long` + `refs.add(new RelationField(Y.class, yId))`.
- [ ] Для пользовательской сущности — поля `login`, `password`, `role`.

**Шаг 3. Слой репозиториев (`repository`)**
- [ ] `XRepository extends Repository<X, X>` c конструктором `super(entityClass)`.
- [ ] Добавить DAO-методы: выборки через `Entity.executeSQL` + `DataSerializer.serializeFromResultDataToList`;
      вставки/обновления/удаления через `Entity.executeUpdate`. Все клиентские данные — только через `?`.

**Шаг 4. Слой сервисов (`service`)**
- [ ] `XService` с полями-репозиториями, внедрёнными через конструктор.
- [ ] Перенести сюда ВСЮ бизнес-логику: валидацию, вычисления, сборку JSON
      (`DataSerializer.convertToJson`), сценарии над несколькими репозиториями.
- [ ] Методы возвращают готовые строки (или структуры, которые контроллер превратит в строку).

**Шаг 5. Слой контроллеров (`controller`)**
- [ ] `XController extends Security` (если нужна авторизация; иначе можно обычный класс),
      конструктор принимает `Statement` (+ сервисы) и раздаёт зависимости.
- [ ] Экшены строго: `public String someAction(String[] params, byte[][] binaryFiles)`.
- [ ] Внутри: `try/catch` всего → при ошибке вернуть понятную строку; при защищённом роуте —
      сначала `checkRole(...)`, при отказе — `returnException()`.
- [ ] Тело условия — вызов метода сервиса и возврат его результата.

**Шаг 6. Конфиг DI (`vendor/DI/ConfigDI.java`)**
- [ ] Зарегистрировать сущности (с `Statement`), затем репозитории (с сущностями),
      затем сервисы (с репозиториями).

**Шаг 7. Main-класс**
- [ ] `ConfigDI.setBeans();`
- [ ] `init()` всех репозиториев в правильном порядке (справочники → связующие).
- [ ] (Опционально) сидирование данных.
- [ ] Получить `Server`, добавить все контроллеры в `server.controllerPull.declaredControllers`.
- [ ] `server.startServer();`

**Шаг 8. Проверка**
- [ ] Прогнать запросы telnet/PuTTY/FileClient (раздел 13) по всем роутам,
      включая негативные кейсы (неверный логин/роль, несуществующий роут, файлы).

---

## 11. Полный сквозной пример приложения

Мини-блог: сущность `Post`, репозиторий, сервис, контроллер с двумя роутами
(публичное чтение и защищённое создание). Показывает все слои и связки.

**entities/Post.java**

```java
package com.mycompany.jcore.entities;

import java.sql.Statement;
import vendor.EntityOrm.Entity;
import vendor.EntityOrm.RelationField;

public class Post extends Entity {
    public String title;
    public String body;
    public Long personId; // автор поста

    public Post(Statement statement) {
        super(statement);
        refs.add(new RelationField(Person.class, personId));
    }
}
```

**repository/PostRepository.java** — см. полный шаблон в разделе 6.4.

**service/PostService.java**

```java
package com.mycompany.jcore.service;

import com.mycompany.jcore.repository.PersonRepository;
import com.mycompany.jcore.repository.PostRepository;
import java.util.List;
import java.util.Map;
import vendor.EntityOrm.DataSerializer;

public class PostService {

    private final PostRepository postRepository;
    private final PersonRepository personRepository;

    // Репозитории внедряются через конструктор (регистрируется в ConfigDI)
    public PostService(PostRepository postRepository, PersonRepository personRepository) {
        this.postRepository = postRepository;
        this.personRepository = personRepository;
    }

    /** Бизнес-логика: получить все посты в формате JSON. */
    public String getAllPostsJson() throws Exception {
        List<Map<String, Object>> posts = postRepository.findAll();
        return DataSerializer.convertToJson(posts);
    }

    /** Бизнес-логика: создать пост с валидацией входных данных клиента. */
    public String createPost(String title, String body, String authorIdStr) {
        try {
            if (title == null || title.isBlank()) {
                return "ERROR: title is empty";
            }
            long authorId = Long.parseLong(authorIdStr);

            if (personRepository.findById(authorId) == null) {
                return "ERROR: author not found";
            }

            int rows = postRepository.insert(title.trim(), body == null ? "" : body.trim(), authorId);
            return rows == 1 ? "OK: post created" : "ERROR: insert failed";
        } catch (NumberFormatException e) {
            return "ERROR: authorId must be a number";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
```

**controller/PostController.java**

```java
package com.mycompany.jcore.controller;

import java.sql.Statement;
import vendor.Security.Security;

public class PostController extends Security {

    private final PostService postService; // сервис внедряется через конструктор

    public PostController(Statement statement, PostService postService) {
        super(statement); // Security требует Statement
        this.postService = postService;
    }

    /** Публичный роут: GET-аналог. Запрос: PostController/getAllPostsAction<endl> */
    public String getAllPostsAction(String[] params, byte[][] binaryFiles) {
        try {
            return postService.getAllPostsJson();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /** Защищённый роут (только USER).
     *  Запрос: PostController/createPostAction<endl>Заголовок<endl>Текст<endl>1<endl>ivanov<security>pass<endl> */
    public String createPostAction(String[] params, byte[][] binaryFiles) {
        if (!super.checkRole("Person", "login", "password", "role", "USER", params)) {
            return super.returnException();
        }
        // params[0]=title, params[1]=body, params[2]=authorId,
        // последний param содержит логин<security>пароль — сервис его не получает
        if (params.length < 3) {
            return "ERROR: expected params: title, body, authorId";
        }
        return postService.createPost(params[0], params[1], params[2]);
    }
}
```

**Регистрация в ConfigDI.setBeans() (добавка к системным бинам):**

```java
ContainerDI.register(Post.class, new Post(ContainerDI.getBean(Statement.class)));
ContainerDI.register(PostRepository.class, new PostRepository(ContainerDI.getBean(Post.class)));
ContainerDI.register(PostService.class,
        new PostService(ContainerDI.getBean(PostRepository.class),
                        ContainerDI.getBean(PersonRepository.class)));
```

**Регистрация в main():**

```java
ContainerDI.getBean(PostRepository.class).init(); // ПОСЛЕ PersonRepository.init()

server.controllerPull.declaredControllers.add(
    new PostController(ContainerDI.getBean(Statement.class),
                       ContainerDI.getBean(PostService.class))
);
```

**Проверка (telnet/PuTTY raw на 127.0.0.1:8082):**

```text
PostController/getAllPostsAction<endl>
PostController/createPostAction<endl>Мой пост<endl>Текст поста<endl>1<endl>ivanov<security>pass<endl>
```

---

## 12. Важные нюансы, ограничения и подводные камни

### 12.1. Сигнатура экшена — только (String[], byte[][])

Рефлексия ищет метод строго `getMethod(name, String[].class, byte[][].class)`.
Экшен `fooAction(String[] params)` (как в старых примерах README) **не найдётся**.
Всегда пишите оба параметра, даже если бинарные файлы не нужны.

### 12.2. Нюанс Repository.setData() и поля id

`insertData()` строит `INSERT INTO ... (id, ...) VALUES (<id>, ...)`, где `<id>` — значение поля
`id` **того объекта сущности, который лежит в репозитории** (бин-синглтон из DI), а не поля DTO.
Последствия:

- первый вызов `setData` вставит строку с `id=0`, повторный вызов упадёт по дубликату первичного ключа;
- значение `data.id` игнорируется.

Поэтому:

- для вставки данных, приходящих от клиента, используйте собственные DAO-методы с
  `Entity.executeUpdate(...)` (плейсхолдеры `?`) — это основной рабочий путь;
- `setData` годится для разового сидирования при старте (одна запись на сущность) либо
  перед каждым вызовом выставляйте уникальный `id` вручную;
- поля типа `byte[]` через `setData` корректно не вставляются (попадут в SQL как строка) —
  для BLOB используйте `executeUpdate` с `setObject`.

### 12.3. Порядок создания таблиц

FK-констрейнты требуют существования целевой таблицы. Вызывайте `init()` репозиториев
от справочных сущностей к связующим. Ошибка порядка → SQLException при старте.

### 12.4. Ресурсы JDBC

- `ResultSet` из `Entity.executeSQL` закрывает вызывающий. `DataSerializer.serializeFromResultDataToList`
  закрывает его сам — предпочтительный способ чтения.
- `Connection` и `Statement` — долгоживущие бины-синглтоны, их не закрывают в течение работы приложения.

### 12.5. Потокобезопасность

Обработку ведут 4 параллельных воркера. Один общий `Statement` используется несколькими
потоками одновременно — JDBC `Statement` не потокобезопасен. Практическое правило:
в сервисах/репозиториях выполнять запросы атомарно (один `executeSQL`/`executeUpdate` на операцию,
без разделяемого изменяемого состояния между шагами). Не храните промежуточные результаты
в полях синглтон-бинов.

### 12.6. Ограничения протокола

- Ответ — одна строка; многострочные ответы допустимы (`\r\n` внутри), но клиент читает поток до EOF.
- Параметры не должны содержать подстроку `<endl>` (сломает парсинг), а логин/пароль — подстроку `<security>`.
- Маркер `<BINARY>` ищется побайтово в текстовом потоке — не включайте его в текстовые параметры.
- Размер файла в заголовке — int (max ~2 ГБ); отрицательный размер → разрыв соединения с ошибкой.
- Файлы принимаются целиком в память (без стриминга) — см. правила тяжёлых данных в разделе 4.5.
- Нет таймаутов на чтение: недобросовестный клиент может удерживать один из 4 воркеров.
- Сервер отвечает `ERROR: Controller returned null` и на неизвестный роут, и на исключение в экшене —
  различайте ситуации собственными строками ошибок в экшенах.

### 12.7. Известные косметические особенности кода фреймворка

- В `Security.extractLoginAndPasswordFromClientQuery` условие логирования инвертировано:
  сообщение «ДАННЫЕ ДЛЯ ВХОДА НЕ НАЙДЕНЫ» печатается, когда данные как раз найдены.
  На логику проверки это не влияет.
- `checkRole` закрывает `ResultSet` только при успешном `next()` — при отсутствии пользователя
  курсор остаётся открытым до GC. Не критично, но имейте в виду при профилировании.
- Логи SQL-запросов (`QUERY FOR EXECUTION`) печатаются в консоль сервера — удобно при отладке.

---

## 13. Тестирование и отладка

### 13.1. Telnet / PuTTY (ручные запросы)

- **PuTTY:** Host `127.0.0.1`, Port `8082`, Connection type: *Other → Raw*,
  Close window on exit: *Never*. Вводите строку запроса целиком и Enter.
- **telnet:** `telnet 127.0.0.1 8082`, затем строка запроса.
- Пример простого запроса:
  `PersonController/createPersonAction<endl>helloWorld!<endl>JCore!<endl>`
- Пример защищённого запроса:
  `PersonController/createPersonAction<endl>helloWorld!<endl>JCore!<endl>ivanov<security>pass<endl>`

### 13.2. Программный клиент FileClient

Готовый класс `com.mycompany.jcore.client.FileClient` — отправка текстовых параметров и файлов:

```java
FileClient client = new FileClient("127.0.0.1", 8082);
String response = client.sendFile(
    "PersonController", "createPersonAction",
    new String[]{"upload_photo"},
    new File("../test_photo.jpg")
);
System.out.println(response);
```

Клиент сам формирует текстовую часть, маркер `<BINARY>`, фрейминг файлов
(`writeInt(size)` + байты) и терминатор `writeInt(0)`, затем читает ответ до EOF.
Используйте его как образец для собственного тестового клиента.

### 13.3. Что смотреть в консоли сервера

- ASCII-логотип и `Сервер запущен на порту: ...` — успешный старт;
- `Новое подключение: ...`, `Получен роут: ...`, `Ответ отправлен клиенту` — жизненный цикл запроса;
- `QUERY FOR EXECUTION -> ...` — сгенерированный SQL (DDL/INSERT);
- `CONTROLLER ERROR: ...` — исключение внутри экшена (ищите причину в своём коде);
- `SQLError: ...` — проблемы DDL/DML.

---

## 14. Чек-лист типичных ошибок

| Симптом | Причина | Как исправить |
|---|---|---|
| Клиент получает `ERROR: Controller returned null` на существующий роут | Экшен имеет сигнатуру без `byte[][]` или не `public` | Сделать сигнатуру `(String[], byte[][])`, модификатор `public` |
| То же на любой роут | Контроллер не добавлен в `server.controllerPull.declaredControllers` | Добавить в `main()` до `startServer()` |
| То же | Неперехваченное исключение в экшене | Обернуть тело экшена в try/catch, вернуть строку ошибки |
| `NullPointerException` при старте | `ConfigDI.setBeans()` не вызван или бин берётся раньше регистрации | Вызывать `setBeans()` первым; соблюдать порядок регистрации |
| `SQLException: relation ... does not exist` / ошибка FK при старте | `init()` вызван не в том порядке или не вызван | Вызывать `init()` каждого репозитория: справочники → связующие |
| Дубликат ключа при вставке через `setData` | `id` берётся из бина сущности в репозитории (см. 12.2) | Использовать `executeUpdate` с `?`, либо уникальный `id` вручную |
| Всегда `ACCESS_DENIED` | В запросе нет блока `логин<security>пароль` | Добавить параметр `<endl>login<security>password<endl>` |
| Всегда `ACCESS_DENIED` (пароль верный) | В БД лежит открытый пароль вместо BCrypt-хеша | Сохранять через `Security.hashPassword(...)` |
| Параметры «склеились»/потерялись | В значении параметра есть `<endl>` | Убрать служебные маркеры из данных, передавать по одному параметру |
| Колонка неожиданно `TEXT` | Тип поля не входит в таблицу маппинга | Использовать типы из раздела 6.3 |
| Сервер «зависает» под нагрузкой | Все 4 воркера заняты медленными клиентами | Сокращать время обработки; не держать соединения; тяжёлое — асинхронно вне протокола |

---

## Приложение A. Быстрая шпаргалка

```text
РОУТ:            ИмяКонтроллера/имяЭкшена
ЗАПРОС:          роут<endl>p1<endl>p2<endl>[login<security>pass<endl>][<BINARY>[int size][bytes]...[int 0]]
ЭКШЕН:           public String xAction(String[] params, byte[][] binaryFiles)
ТЯЖЁЛЫЕ ФАЙЛЫ:   byte[][] = массив файлов; binaryFiles[i] - i-й файл целиком; пусто -> byte[0][]
                 клиент: текст -> "<BINARY>" -> [writeInt(size)+bytes]*N -> writeInt(0) (см. FileClient)
SELECT:          Entity.executeSQL(sql, params) -> ResultSet -> DataSerializer.serializeFromResultDataToList
INSERT/UPD/DEL:  Entity.executeUpdate(sql, params) -> int
JSON-ответ:      DataSerializer.convertToJson(list)
ХЕШ ПАРОЛЯ:      Security.hashPassword(pwd)  /  Security.checkHashedPassword(hash, pwd)
ЗАЩИТА РОУТА:    class XController extends Security; checkRole(table, loginF, passF, roleF, "ROLE", params)
СТАРТ:           setBeans() -> repo.init() (справочники первыми) -> контроллеры в declaredControllers -> startServer()
СЛОИ:            entities <- repository <- service <- controller (строго в этом направлении)
БИБЛИОТЕКИ:      только встроенные (JDK + postgresql + bcrypt); сторонние подключать НЕЛЬЗЯ
```
