![JCore](./JCoreIcon.png)

# JCore — Документация
<table>
<tr>
<td valign="top" width="200">

<img src="./LOGO.png" alt="JCore" width="180">

</td>
<td valign="top">

| | |
|---|---|
| **Версия** | 0.0.3 |
| **Тип** | легковесный фреймворк для веб-приложений и API-сервисов на Java |
| **Транспорт** | сокетная коммуникация (TCP) |

</td>
</tr>
</table>

> Этот документ — структурированное описание архитектуры JCore: механизм работы каждого модуля, назначение и сигнатуры основных методов, порядок выполнения в коде. Все описания сверены с фактическим кодом в `JCore/src/main/java/vendor/`.
> Быстрая шпаргалка по API и чек-лист типичных ошибок — в **[JCORE_FRAMEWORK_GUIDE.md](./JCORE_FRAMEWORK_GUIDE.md)**.

---

## Содержание

- [Введение](#введение)
- [Архитектура в одном взгляде](#архитектура-в-одном-взгляде)
- [Быстрый старт](#быстрый-старт)
- [Глава 1. Внедрение зависимостей (DI)](#глава-1-внедрение-зависимостей-di)
- [Глава 2. Работа с базой данных и сущностями](#глава-2-работа-с-базой-данных-и-сущностями)
- [Глава 3. Контроллеры, роутинг и сервер](#глава-3-контроллеры-роутинг-и-сервер)
- [Глава 4. Безопасность (Security)](#глава-4-безопасность-security)
- [Ограничения фреймворка](#ограничения-фреймворка)
- [Глоссарий](#глоссарий)

---

## Введение

JCore — компактный фреймворк для создания простых веб-приложений и API-сервисов на Java. Работает поверх `ServerSocket`, не требует внешнего сервера приложений и не тянет тяжёлых зависимостей. Весь код фреймворка находится в пакете `vendor/` и может быть прочитан целиком за один вечер.

Фреймворк состоит из четырёх основных компонентов:

| Компонент | Путь | Назначение |
|---|---|---|
| DI (Container) | `vendor/DI` | Хранилище и выдача системных бинов |
| EntityOrm | `vendor/EntityOrm` | Описание сущностей и работа с БД через JDBC |
| ControllerComponent | `vendor/ControllerComponent` | Контроллеры, роутинг, сервер |
| Security | `vendor/Security` | Защита роутов |

Как эти модули связаны в работающем приложении:

- **DI** раздаёт системные объекты всем остальным модулям (серверу, сущностям, Security) по типу класса — это «клей», на котором держатся связи между модулями.
- **EntityOrm** создаёт таблицы в БД по описанию сущностей и выполняет безопасные запросы; работает только с базой.
- **ControllerComponent** принимает запросы по TCP, разбирает их и направляет в методы контроллеров; контроллеры через **Security** проверяют доступ и через **EntityOrm** работают с данными.
- **Security** использует `Entity.executeSQL` (модуль EntityOrm), чтобы сверить логин/пароль/роль из БД.

**Важно понимать:** JCore — это не замена Spring, Micronaut или Javalin. Это лёгкий инструмент для небольших задач. Подробнее — в разделе [Ограничения фреймворка](#ограничения-фреймворка).

---

## Архитектура в одном взгляде

### Поток обработки запроса

```
 Клиент                                      Сервер JCore (127.0.0.1:8082)
   │                                              │
   │  connect(host, port)                          │
   │─────────────────────────────────────────────►│  accept() → BlockingQueue<Socket>
   │                                              │  один из 4 воркеров берёт сокет из очереди
   │                                              │
   │  текст: Controller/action<endl>p1<endl>…     │
   │  [ + <BINARY> и файлы кусками, если есть ]    │  readClientRequest() → ClientRequest
   │─────────────────────────────────────────────►│  Controller.startMethodByUrl(request)
   │                                              │      ↓
   │                                              │  экшен: [Security] → сервис → репозиторий → БД
   │                                              │      ↓
   │  ответ: ServerResponse                       │  ServerResponse.convertDataToBytesForStream()
   │     params<endl>…<endl><BINARY>[файлы кусками]│
   │◄─────────────────────────────────────────────│
   │                                              │  close()
```

Пошагово, что происходит на сервере **для одного запроса**:

1. **Приём соединения.** Главный поток сервера (`accept()`) получает сокет клиента и кладёт его `BlockingQueue<Socket>`. Сам этот поток никогда не занимается обработкой — только приёмом.
2. **Выдача работы воркеру.** Один из 4 фиксированных потоков-воркеров (`THREAD_POOL_SIZE = 4`) забирает сокет из очереди блокирующим `take()` и начинает обработку в методе `handleClient(...)`.
3. **Чтение запроса.** `readClientRequest(...)` читает поток байтов: пока не встретит маркер `<BINARY>`, копит текст; после маркера (если есть) читает бинарные файлы. Текст разбивается по `<endl>`: первый элемент — роут, остальные — параметры. Результат — объект `ClientRequest`.
4. **Роутинг.** `Controller.startMethodByUrl(request)` по имени класса из роута находит контроллер в списке `declaredControllers` и через рефлексию вызывает нужный метод-экшен.
5. **Бизнес-логика.** Экшен опционально проверяет доступ через `Security.checkRole(...)`, вызывает сервис/репозиторий и формирует ответ-пакет `ServerResponse`.
6. **Сериализация ответа.** Если контроллер вернул `ServerResponse`, сервер вызывает `convertDataToBytesForStream(...)` — ответ уходит в сокет тем же фреймингом, что и запрос.
7. **Закрытие соединения.** Сокет закрывается. Keep-alive нет: следующий запрос — новое соединение.

**Модель взаимодействия: один запрос = одно соединение.** Кодировка текста — UTF-8.

### Слои приложения

Архитектура строго слоистая, зависимости направлены сверху вниз:

```
┌─────────────────────────────────────────────────────────────┐
│ СЛОЙ 4. Контроллеры (…controller)                            │
│ Приём ClientRequest, проверка доступа (Security), вызов      │
│ сервиса, упаковка результата в ServerResponse.               │
│ Без бизнес-логики и SQL.                                     │
├─────────────────────────────────────────────────────────────┤
│ СЛОЙ 3. Сервисы (…service)                                   │
│ Вся бизнес-логика: валидация, вычисления, оркестрация        │
│ репозиториев, сборка JSON. Внедряются через конструктор.     │
├─────────────────────────────────────────────────────────────┤
│ СЛОЙ 2. Репозитории (…repository)                            │
│ Управление сущностями (DAO). Наследуются от Repository.      │
│ SQL только через параметризованные методы.                   │
├─────────────────────────────────────────────────────────────┤
│ СЛОЙ 1. Сущности (…entities)                                 │
│ Описание модели данных. Классы-наследники Entity,            │
│ публичные поля = колонки таблиц БД.                          │
└─────────────────────────────────────────────────────────────┘
```

- **Controller → Service → Repository → Entity.** Обратная зависимость запрещена: сущность ничего не знает о контроллерах, сервис — о транспортном протоколе.
- Что разрешено и запрещено в каждом слое:
  - **Контроллер** не имеет права содержать SQL и бизнес-логику. Его работа — принять `ClientRequest`, при необходимости проверить доступ, вызвать метод сервиса, упаковать результат в `ServerResponse`.
  - **Сервис** владеет объектами репозиториев (внедрёнными через конструктор) и возвращает готовые строки/JSON. Он «не знает» про TCP и контроллеры.
  - **Репозиторий** работает только со своей сущностью и SQL.
  - **Сущность** — чистое описание данных: поля + конструктор со `Statement` + регистрация связей.
- В примере фреймворка слоя сервисов нет (это демонстрация), но в реальном приложении его создание **обязательно**.

### Структура проекта

```
v0.0.3/Java/
├── README.md                          # этот документ
├── JCORE_FRAMEWORK_GUIDE.md           # полный технический справочник
└── JCore/
    ├── pom.xml                        # Maven: Java 21, postgresql 42.7.9, bcrypt 0.10.2
    └── src/main/java/
        ├── vendor/                    # === КОД ФРЕЙМВОРКА ===
        │   ├── JCoreMeta.java         # ASCII-логотип при старте
        │   ├── DI/                    # ContainerDI, ConfigDI
        │   ├── EntityOrm/             # Entity, Repository, EntityInfo, RelationField,
        │   │                          # FieldNameWithType, ConfigJDBC, DataSerializer
        │   ├── ControllerComponent/
        │   │   ├── Controller.java    # реестр контроллеров + рефлексивный роутинг
        │   │   └── Connection/
        │   │       ├── Server.java    # TCP-сервер: ServerSocket, пул 4 воркеров,
        │   │       │                  # разбор запроса, стриминг файлов в uploads/
        │   │       └── Exchange/      # ClientRequest, Data, ServerResponse
        │   └── Security/              # Security.java (checkRole, BCrypt)
        └── com/mycompany/jcore/       # === КОД ПРИМЕРА ПРИЛОЖЕНИЯ ===
            ├── JCore.java             # main(): стартовая последовательность
            ├── entities/              # Person, Car
            ├── repository/            # PersonRepository, CarRepository
            ├── controller/            # PersonController
            └── client/                # FileClientPusher — готовый TCP-клиент
```

### Технологический стек

| Технология | Значение |
|---|---|
| Язык | Java 21 |
| Сборка | Maven (`JCore/pom.xml`) |
| СУБД | PostgreSQL (драйвер `org.postgresql:postgresql:42.7.9`) |
| Хеширование | BCrypt (`at.favre.lib:bcrypt:0.10.2`) |
| Транспорт | `java.net.ServerSocket` (TCP), без внешнего сервера |

---

## Быстрый старт

### Шаг 1. Подготовка окружения

- Установите **Java 21** и **Maven** (в `pom.xml` уже заданы `maven.compiler.release=21` и главный класс `com.mycompany.jcore.JCore`).
- Разверните базу **PostgreSQL** — она целевая СУБД фреймворка (генерируемый SQL использует синтаксис `SERIAL`, `TEXT`, `BYTEA`, `TIMESTAMPTZ`).
- В файле `vendor/EntityOrm/ConfigJDBC.java` укажите свои параметры подключения:

```java
private String urlConnection = "jdbc:postgresql://localhost:5432/test1"; // строка подключения к БД
private String userName = "postgres"; // пользователь БД
private String password = "root";     // пароль БД
```

Зависимости (`postgresql`, `bcrypt`) уже объявлены в `pom.xml` — дополнительно ничего подключать не нужно.

### Шаг 2. Опишите сущности

Каждая таблица БД — это класс-наследник `Entity`. Публичные поля класса = колонки таблицы, поле `id SERIAL PRIMARY KEY` наследуется от родителя:

```java
package com.mycompany.jcore.entities;

import java.sql.Statement;
import vendor.EntityOrm.Entity;

public class Person extends Entity {
    public String name;
    public String surname;
    public String login;     // для Security
    public String password;  // хранить ТОЛЬКО хеш: Security.hashPassword(...)
    public String role;      // роль: "USER", "ADMIN", ...

    public Person(Statement statement) {
        super(statement);
    }
}
```

Сущность со связью «многие к одному» (внешний ключ) описывается полем `[ИмяКласса]Id` и ссылкой в конструкторе:

```java
package com.mycompany.jcore.entities;

import java.sql.Statement;
import vendor.EntityOrm.Entity;
import vendor.EntityOrm.RelationField;

public class Car extends Entity {
    public String mark;
    public String color;
    public Long personId; // правило именования: ссылка на Person → поле personId

    public Car(Statement statement) {
        super(statement);
        refs.add(new RelationField(Person.class, personId)); // связь N:1
    }
}
```

Правила (подробно — в Главе 2):

- имена колонок — это имена полей, типы — только из таблицы маппинга;
- FK-колонку система выводит из имени связуемого класса: `Person` → `personId`, — поэтому поле обязано называться **точно так же**.

### Шаг 3. Создайте репозитории

Репозитории наследуются от `Repository<ENTITY, DTO>` и управляют своей сущностью:

```java
package com.mycompany.jcore.repository;

import com.mycompany.jcore.entities.Person;
import vendor.EntityOrm.Repository;

public class PersonRepository extends Repository<Person, Person> {

    public PersonRepository(Person entityClass) {
        super(entityClass);
    }
}
```

Добавляйте сюда DAO-методы штучно (выборки через `Entity.executeSQL`, изменения — `Entity.executeUpdate`; для клиентских данных — только через плейсхолдеры `?`).

### Шаг 4. Зарегистрируйте бины в DI-контейнере

В `vendor/DI/ConfigDI.java` после системных бинов добавьте свои. Порядок важен: сначала сущности, затем репозитории (у репозитория в конструкторе лежит сущность из контейнера):

```java
// сущности (каждой нужен Statement)
ContainerDI.register(Person.class, new Person(ContainerDI.getBean(Statement.class)));

// репозитории (каждому нужна его сущность)
ContainerDI.register(PersonRepository.class, new PersonRepository(ContainerDI.getBean(Person.class)));
```

### Шаг 5. Запустите приложение

```java
public static void main(String[] args) throws Exception {
    // 1. Инициализация DI-контейнера (системные + ваши бины) — ПЕРВОЙ строкой!
    ConfigDI.setBeans();

    // 2. Создание таблиц: init() у каждого репозитория.
    //    ПОРЯДОК ВАЖЕН: справочники (без FK) → связующие (с FK)!
    ContainerDI.getBean(PersonRepository.class).init();
    ContainerDI.getBean(CarRepository.class).init();

    // 3. Запуск сервера: бин → регистрация контроллеров → старт
    Server server = ContainerDI.getBean(Server.class);
    server.controllerPull.declaredControllers.add(
        new PersonController(ContainerDI.getBean(Statement.class))
    );

    server.startServer(); // блокирует поток навсегда
}
```

### Шаг 6. Проверьте запросом

Подключитесь **raw-соединением** (PuTTY: сервер `127.0.0.1`, порт `8082`, тип *Other → Raw*; либо `telnet 127.0.0.1 8082`) и отправьте:

```text
PersonController/createPersonAction<endl>helloWorld!<endl>JCore!<endl>
```

Программно — готовым классом `FileClientPusher`:

```java
FileClientPusher client = new FileClientPusher("127.0.0.1", 8082);
String response = client.sendFile(
    "PersonController", "createPersonAction",
    new String[]{"helloWorld!", "JCore!"}
);
System.out.println(response);
```

В консоли сервера вы увидите: ASCII-логотип, `Сервер запущен на порту: 8082`, при запросе — `Новое подключение: …`, `Получен роут: …`, `Ответ отправлен клиенту`, `Соединение закрыто`.

---

## Глава 1. Внедрение зависимостей (DI)

Расположение: `vendor/DI/`. Этот модуль — «клей» всего фреймворка: он хранит системные и пользовательские объекты и выдаёт их по типу класса. Компонент состоит из двух классов: `ContainerDI` (само хранилище) и `ConfigDI` (точка регистрации).

### Механизм работы

- Контейнер — это статическая карта `HashMap<Class<?>, Object>`. Ключ — тип класса (например, `Server.class`), значение — сам объект.
- Все методы статические: объект контейнера создавать не нужно, доступ из любого места приложения одинаковый.
- **Один тип = один экземпляр.** Повторная регистрация того же типа перезаписывает значение. Поэтому все бины фактически синглтоны — это принципиально для сущностей/репозиториев (см. Главу 2).
- Регистрация и получение происходят **по одному и тому же классу**: зарегистрировали по `CarRepository.class` — доставайте по `CarRepository.class`. `getBean` не умеет искать «по интерфейсу» или «по предку».

### ContainerDI — описание методов

```java
public static void register(Class<?> type, Object instance)
```

Регистрирует бин: кладёт объект `instance` в контейнер под ключом `type` (метод-сеттер для значения в контейнер). Если под таким типом уже был объект — он заменяется.

```java
public static <T> T getBean(Class<T> type)
```

Возвращает бин по типу класса (тип T подставляется компилятором автоматически). Если бин не зарегистрирован — вернёт `null` (а не исключение), поэтому при использовании результата возможен `NullPointerException`. Следите за порядком регистрации в `ConfigDI`.

### ConfigDI — механизм работы setBeans()

Статический метод `setBeans()` вызывается **самым первым** в `main()`. Он регистрирует 4 системных бина, и у каждого из них есть зависимость от предыдущего — **порядок строк принципиален**:

```java
public static void setBeans() throws SQLException {
    // 1. Маршрутизатор контроллеров (без зависимостей)
    ContainerDI.register(Controller.class, new Controller());

    // 2. Сервер: зависит от маршрутизатора, порт задаётся здесь (по умолчанию 8082)
    ContainerDI.register(Server.class, new Server(ContainerDI.getBean(Controller.class), 8082));

    // 3. Подключение к БД: данные берутся из ConfigJDBC
    ContainerDI.register(Connection.class, new ConfigJDBC().getConnectionDB());

    // 4. Statement: создаётся на основе зарегистрированного выше подключения
    ContainerDI.register(Statement.class, ContainerDI.getBean(Connection.class).createStatement());

    // ===== ДАЛЕЕ — бины приложения (регистрирует разработчик) =====
}
```

| Бин | Тип | Назначение | Зависит от |
|---|---|---|---|
| Маршрутизатор | `Controller` | Реестр контроллеров и роутинг | — |
| Сервер | `Server` | TCP-сервер (порт 8082) | `Controller` |
| Подключение | `java.sql.Connection` | Соединение с БД | `ConfigJDBC` |
| Statement | `java.sql.Statement` | Выполнение SQL | `Connection` |

Правила при регистрации своих бинов:

1. Порядок: **сущности → репозитории → сервисы**. Каждый следующий берёт из контейнера предыдущий в своём конструкторе.
2. Сущностям в конструктор передаётся `Statement`: `new Person(ContainerDI.getBean(Statement.class))`.
3. Репозиториям передаётся их сущность: `new PersonRepository(ContainerDI.getBean(Person.class))`.
4. Сервисам передаются репозитории: `new PostService(ContainerDI.getBean(PostRepository.class), ...)`.

**Контроллеры в DI НЕ регистрируются** — они создаются вручную в `main()` и складываются в `server.controllerPull.declaredControllers` (см. Главу 3). Это сделано потому, что роутинг опирается на имена классов и методов, а сам список живой.

---

## Глава 2. Работа с базой данных и сущностями

Расположение: `vendor/EntityOrm/`. Самописный ORM поверх JDBC. Работает так: **сущность (описание данных) → репозиторий (DAO-надстройка) → JDBC → PostgreSQL**. Сгенерированный DDL рассчитан на PostgreSQL; можно сменить драйвер в `pom.xml`, но синтаксис DDL останется PostgreSQL-ориентированным.

### Конфигурация подключения — ConfigJDBC

Класс хранит три приватных поля с геттерами/сеттерами (исторически значения правят прямо в полях):

```java
private String urlConnection = "jdbc:postgresql://localhost:5432/test1";
private String userName = "postgres";
private String password = "root";
```

Метод `getConnectionDB()` создаёт соединение через `DriverManager.getConnection(url, user, password)`, логирует `Connect to DB...` / `Connection success!`, при ошибке печатает `SQL ERROR:` и возвращает `null`. Вызывается один раз при инициализации контейнера — соединение живёт всё время работы приложения (это синглтон, см. Ограничения).

### Механизм работы сущности

Сущность — класс-наследник абстрактного `Entity`. Что даёт родитель:

- **Поля потомку:**
  - `public long id` — первичный ключ (мапится в `id SERIAL PRIMARY KEY`);
  - `public List<RelationField> refs` — список связей (внешние ключи);
  - приватный `Statement statement` — принимается в конструкторе (`super(statement)`) и используется внутренними методами.
- **Внутренние методы** — вызываются системой через `Repository`, из кода приложения самими их вызывать **не нужно**:
  - `initializeChild()` → `EntityInfo`: через рефлексию (`getDeclaredFields()`) собирает метаданные потомка — класс, массив полей (имя/тип/текущее значение) и список связей. Колонками становятся только поля, объявленные **непосредственно в классе-потомке**: унаследованные `id` и `refs` в метаданные не попадают.
  - `createTable(EntityInfo)` → `boolean`: строит и выполняет DDL.
  - `insertData(EntityInfo)` → `boolean`: строит и выполняет `INSERT`.
- **Безопасные статические методы** для работы с данными, приходящими от **клиента** (безопасность — через `PreparedStatement`):
  - `executeSQL(String sql, Object[] params)` → `ResultSet` — для `SELECT`. Получает `Connection` из DI-контейнера, готовит `PreparedStatement`, последовательно вызывает `setObject(i+1, param)`. **Вызывающий код обязан закрыть `ResultSet`** (или используйте `DataSerializer.serializeFromResultDataToList`, который закрывает сам).
  - `executeUpdate(String sql, Object[] params)` → `int` — для `INSERT/UPDATE/DELETE`. Возвращает число затронутых строк. `PreparedStatement` закрывается автоматически в `finally`.
  - `printResultSetSimple(ResultSet)` — отладочный вывод заголовков и строк в консоль.

### Генерация DDL (createTable)

Для сущности `Car` с полями `mark`, `color`, `personId` и связью на `Person` генерируется:

```sql
CREATE TABLE IF NOT EXISTS Car (
    id SERIAL PRIMARY KEY,
    mark TEXT,
    color TEXT,
    personId BIGINT,
    FOREIGN KEY(personId) REFERENCES Person(id) ON DELETE CASCADE
);
```

Механика построения:

- `id SERIAL PRIMARY KEY` — всегда, унаследован от `Entity`.
- Имя FK-колонки выводится из имени связуемого класса: **первая буква в нижний регистр + `Id`** (`Person` → `personId`). Поэтому поле сущности обязано называться точно по этому правилу.
- `ON DELETE CASCADE` — удаление родителя удаляет строки-потомки.
- DDL выполняется с `IF NOT EXISTS` — повторные запуски приложения безопасны, таблицы не пересоздаются.
- Код DDL печатается в консоль с префиксом `QUERY FOR EXECUTION ->`.

### Генерация INSERT (insertData)

Строит `INSERT INTO <ИмяКласса> (id, <поля>) VALUES (<id>, <значения>)`. Как преобразуются значения (`formatDataValue`):

| Значение | В SQL |
|---|---|
| `null` | `NULL` |
| `String` | `'значение'` (одинарные кавычки удваиваются: `'` → `''`) |
| `Number` | как есть |
| `Boolean` | `TRUE` / `FALSE` |
| `java.util.Date`, `LocalDate`, `Timestamp` и т.п. | `TIMESTAMP 'значение'` |
| прочее (`byte[]` и т.п.) | `'toString()'` — **не пригодно для BLOB** (см. Ограничения) |

⚠️ **Нюанс `setData` и `id`:** `Repository.setData(data)` вызывает `entityClass.insertData(data.initializeChild())`, где `entityClass` — это бин-синглтон сущности, лежащий в репозитории. В `INSERT` подставляется `id` **бинового** объекта (`entityClass.id`, обычно `0`), а значения полей — из переданного DTO. Следствия:

- первый вызов `setData` вставит строку с `id = 0`;
- повторный вызов упадёт по дубликату первичного ключа;
- установленный вами `data.id` игнорируется.

Поэтому `setData` годится для разового сидения при старте, а данные от клиента вставляйте через `executeUpdate` с плейсхолдерами.

### Маппинг типов Java → SQL

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

### Репозиторий — класс-наследник Repository

`public abstract class Repository<ENTITY extends Entity, DTO extends Entity>` — абстракция над DAO-классами. Держит управляемую сущность и три публичных метода:

```java
public void init() throws Exception
```

Создаёт таблицу по метаданным сущности: `entityClass.createTable(entityClass.initializeChild())`. Вызывается **один раз при старте приложения** для каждого репозитория. Порядок вызова важен: сначала справочники (без FK), потом связующие (с FK) — иначе PostgreSQL упадёт на внешнем ключе.

```java
public void setData(DTO data) throws Exception
```

Вставляет объект в таблицу: `entityClass.insertData(data.initializeChild())`. Только для разового сидирования (см. нюанс `id` выше).

```java
public ENTITY getEntity()
```

Возвращает управляемую сущность (для манипуляции извне).

Типовой репозиторий с собственными DAO-методами:

```java
public class PostRepository extends Repository<Post, Post> {

    public PostRepository(Post entityClass) {
        super(entityClass);
    }

    public List<Map<String, Object>> findAll() throws SQLException {
        ResultSet rs = Entity.executeSQL("SELECT * FROM Post ORDER BY id", new Object[]{});
        return DataSerializer.serializeFromResultDataToList(rs); // закроет ResultSet сам
    }

    public int insert(String title, String body, Long authorId) throws SQLException {
        return Entity.executeUpdate(
            "INSERT INTO Post (title, body, personId) VALUES (?, ?, ?)",
            new Object[]{ title, body, authorId }
        );
    }
}
```

**Жёсткие правила безопасности:**

- клиентские данные — **только** через плейсхолдеры `?` в `executeSQL`/`executeUpdate`; конкатенация строк запрещена (SQL-инъекции);
- имена таблиц/колонок пишет разработчик, они не приходят от клиента;
- `init()` и `setData()` — только для схемы и сидирования; данные из запросов клиента через них не пропускаются.

### DataSerializer — сериализация результатов

Мост между JDBC и ответами клиенту:

```java
public static List<Map<String, Object>> serializeFromResultDataToList(ResultSet resultSet)
```

Читает весь `ResultSet` в `List<Map<String, Object>>` (ключ — имя колонки, значение — ячейка). **Сам закрывает `ResultSet`** в `finally`.

```java
public static String convertToJson(List<Map<String, Object>> list)
```

Преобразует список в JSON-массив `[{"колонка": значение}, ...]`. Правила: строки в двойных кавычках, числа и `boolean` как есть, `null` → `null`, остальное — через `toString()` в кавычках. Пустой/`null` список → `"[]"`. Внимание: внутренние кавычки/слеши в строках экранировать не экранируются — если в данных возможны кавычки, экранируйте их вручную на этапе сервиса.

`printList(...)` и `printJson(...)` — отладочный вывод в консоль.

Типовой поток данных: `executeSQL` → `serializeFromResultDataToList` → бизнес-обработка в сервисе → `convertToJson` → строка в `ServerResponse` (Глава 3).

---

## Глава 3. Контроллеры, роутинг и сервер

Расположение: `vendor/ControllerComponent/`. Транспортный и управляющий модуль: принимает TCP-соединения, разбирает запросы, фреймит файлы, роутит в экшены и сериализует ответы.

### Классы обмена данными (пакет Exchange)

С v0.0.3 и запрос, и ответ — пакеты данных. В обе стороны перевозятся **одни и те же сущности**: текстовые параметры и файлы.

#### Data — универсальный контейнер

| Член | Тип | Описание |
|---|---|---|
| `params` | `String[]` | Текстовые параметры; для запроса — всё после роута, для ответа — результат/сообщение/JSON |
| `binaryFiles` | `File[]` | **Пути** к файлам на диске (файлы уже сохранены стримингом; в памяти — только ссылки ~100 байт) |
| `getParams()/setParams(...)` | — | Геттер/сеттер параметров |
| `getBinaryFiles()/setBinaryFiles(...)` | — | Геттер/сеттер файлов |
| `showParams()` | `StringBuilder` | Для каждого параметра: `param is -> <значение>\r\n`, печатает в консоль |
| `showFiles()` | `StringBuilder` | Для каждого файла: путь и размер через `File.length()` (O(1), без чтения); отсутствующий файл → `file #N -> MISSING` |

Если файлов не было — сервер кладёт **пустой массив `File[0]`** (не `null`), поэтому `for (File f : data.getBinaryFiles())` безопасен всегда.

#### ClientRequest — запрос клиента

| Член | Тип | Описание |
|---|---|---|
| `route` | `String` | Роут запроса (`"PersonController/createPersonAction"`) |
| `data` | `Data` | Параметры и файлы запроса |
| `getPartsByRoute()` | `String[]` | `route.split("/")`: `[0]` — имя класса, `[1]` — имя метода |

#### ServerResponse — ответ сервера

| Член | Тип | Описание |
|---|---|---|
| `data` | `Data` | Пакет ответа |
| `convertDataToBytesForStream(OutputStream)` | `void` | Сериализация ответа на провод (см. ниже) |

Механизм `convertDataToBytesForStream`:

1. Строит текстовую часть: `p1<endl>p2<endl>...<endl><BINARY>` — маркер `<BINARY>` дописывается **всегда**, даже если файлов нет; `null`-массивы параметров/файлов воспринимаются как пустые.
2. Проходит по `Data.binaryFiles`; каждый файл читает через `PushbackInputStream` буфером **64 КБ**, определяет «последний ли это кусок» пробным байтом, пишет `[флаг 1 байт][writeInt(длина)][данные]`.
3. Пустые файлы (0 байт) пропускает (иначе они конфликтуют с терминатором).
4. В конце пишет терминатор `[0][int32 0]` и `flush()`.

### Формат запроса

```text
ControllerName/actionName<endl>param1<endl>param2<endl>
```

- Первый элемент до первого `<endl>` — **роут**: `ИмяКонтроллера/имяЭкшена`, парсится по `/`.
- Остальное — текстовые параметры: они попадают в `String[] params`.
- Разделитель — литеральная строка `<endl>` (не перевод строки).
- Параметров может быть сколько угодно, в том числе ноль.

К защищённому роуту добавляется блок аутентификации вида:

```text
<endl>login<security>password<endl>
```

Он остаётся обычным элементом массива `params` (см. Главу 4).

### Формат бинарной части (тяжёлые файлы)

Бинарная часть идёт сразу после текстовой и начинается маркером `<BINARY>`. Файлы передаются **кусками** (стримингово) — это позволяет передавать файлы любого размера при фиксированной памяти.

Формат одного куска:

```text
[1 байт: флаг продолжения (1 = будет ещё кусок, 0 = последний)][int32 BE: длина куска][данные куска]
```

- Флаг пишется `DataOutputStream.writeByte(...)`, длина — `writeInt(int)` (big-endian), читаются `readByte()`/`readInt()`.
- Флаг `1` — «у файла будет ещё кусок», `0` — «последний кусок файла». Граница между файлами — кусок с флагом 0, за которым следует кусок нового файла (или терминатор).
- **Лимит куска — 64 МБ** (`Server.MAX_CHUNK_SIZE = 64 * 1024 * 1024`). При куске больше лимита или отрицательной длине сервер бросает `IOException` («Слишком большой кусок: N (максимум 67108864)») и разрывает соединение.
- **Конец списка файлов** — «пустой кусок»: `[флаг 0][int32 0]` (ровно 5 байт `00 00 00 00 00`).
- **Пустой файл (0 байт) не передаётся** — он неотличим от терминатора; и клиент (`FileClientPusher`), и сервер пропускают нулевые файлы.
- Если файлы не нужны — бинарная часть не отправляется вовсе: без `<BINARY>` запрос остаётся чисто текстовым, сервер кладёт `File[0]`.
- Содержимое куска читается `readFully(byte[])` — сервер гарантированно дожидается всех байт.

Пример запроса с файлом:

```text
PersonController/createPersonAction<endl>upload_photo<endl><BINARY>[флаг 0][длина куска][содержимое файла][0][длина 0]
```

### Формат ответа

Ответ — объект `ServerResponse`. Сериализация повторяет фрейминг запроса:

```text
param1<endl>param2<endl>…<endl><BINARY>[куски файлов ответа][0][int32 0]
```

- Маркер `<BINARY>` присутствует **всегда** — не пытайтесь разбирать ответ как чистую строку, если ждёте файлы.
- Файлы ответа режутся кусками по 64 КБ; терминатор `[0][int32 0]` в конце.
- Структурированные данные кладутся в `params[0]` как JSON через `DataSerializer.convertToJson(...)`.

**Важно:** если экшен вернул не `ServerResponse` (в т.ч. `null` — роут не найден, метод не найден, исключение внутри экшена) — сервер печатает `ERROR: контроллер возвратил не обьект класса ServerResponse!` в консоль и **закрывает соединение без единого байта ответа** (клиент получает пустой ответ/EOF). Текстовой строки ошибки на проводе нет — разработчик сам возвращает ошибки внутри `ServerResponse`.

### Механизм работы сервера — Server

Конструктор: `Server(Controller controllerPull, int port)`. Ключевые поля: `queue` (`BlockingQueue<Socket>`), `MAIN_UPLOAD_DIR = "uploads/"`, `THREAD_POOL_SIZE = 4`, `MAX_CHUNK_SIZE`.

```java
public void startServer()
```

Открывает `ServerSocket(port)`, печатает логотип `JCoreMeta.logoRenderer()` и «Сервер запущен на порту: …». Запускает **4 воркера** — каждый в бесконечном цикле берёт сокет из очереди `queue.take()` и обрабатывает. Главный поток навсегда блокируется на `accept()` → `queue.put(socket)`. Так приём и обработка разделены: сколько бы клиентов ни подключилось — в очередь, а обрабатываются максимум 4 одновременно.

Обработка одного клиента (`handleClient`):

1. `readClientRequest(...)` читает поток байтов до маркера `<BINARY>` состоянии-машиной поиска, маркер из текста вырезает.
2. Если запрос пуст (нет роута) — печатает «Получен пустой запрос» и закрывает сокет.
3. Делит текст по `<endl>`: `parsedData[0]` → `request.setRoute(...)`, остальные — в `Data.params`.
4. Если был `<BINARY>` — в цикле читает файлы `readOneFileToDisk(...)`, пока не встретит терминатор.
5. Вызывает `controllerPull.startMethodByUrl(request)`.
6. Если результат — `ServerResponse`, вызывает `convertDataToBytesForStream(outputStream)`; иначе печатает ошибку.
7. Закрывает сокет.

Чтение бинарных файлов (`readOneFileToDisk`) — **стриминг на диск**:

- создаёт файл `uploads/file_<UUID>.bin` (папка создаётся автоматически);
- каждый кусок сразу дописывается `FileOutputStream`. В памяти в каждый момент — только один кусок, поэтому сервер принимает файл **любого размера** при фиксированной памяти;
- терминатор до первого записанного куска → файл удаляется, возвращается `null` (приём завершён);
- EOF в середине файла → возвращается то, что записали (может быть неполный файл);
- ошибка диска → частичный файл удаляется, исключение летит наверх.

Обработка ошибок в `handleClient`: `IOException` → «Ошибка при работе с клиентом: …», `Throwable` → «НЕОЖДАННАЯ ОШИБКА в handleClient». Сервер после ошибки продолжает работать.

### Механизм работы роутинга — Controller

```java
public List<Object> declaredControllers // живой список контроллеров
public Object startMethodByUrl(ClientRequest request)
```

1. `request.getPartsByRoute()` → `[имя класса, имя метода]`.
2. Линейно перебирает `declaredControllers` и ищет объект, у которого `getClass().getSimpleName()` равен имени из роута.
3. `getMethod(имя метода, ClientRequest.class)` — рефлексия ищет метод **ровно** с этим именем и **одним параметром `ClientRequest`**.
4. `method.invoke(controller, request)`.

Поведение при ошибках:

- `InvocationTargetException` (исключение внутри экшена) → печатает `CONTROLLER ERROR, real cause:` с трейсом **реальной причины** (`e.getCause()`) и возвращает `null`;
- другие исключения → `CONTROLLER ERROR:` с трейсом, возвращает `null`;
- контроллер не найден / ошибка до invoke → возвращает `null`.

### Контракт экшена (критично)

```java
public ServerResponse someAction(ClientRequest request) { ... }
```

- Экшен **обязан** быть `public` и иметь сигнатуру ровно `(ClientRequest)`. Метод со старыми сигнатурами (`(String[], FileChunk[])`, `(String, String[], File[])`, `(String[] params, byte[][])`) найден **не будет** — рефлексия по ним не ищет.
- Экшен обязан возвращать `ServerResponse`. Исключения он обязан перехватывать сам и возвращать осмысленный пакет с текстом ошибки — иначе клиент получит пустой ответ.
- Перегрузки с одинаковым именем не поддерживаются; роут чувствителен к регистру.
- Данные: `request.getData().getParams()` и `request.getData().getBinaryFiles()` (пустой массив, если файлов нет).

### Регистрация контроллеров

Контроллеры живут не в DI — они регистрируются в `main()`:

```java
Server server = ContainerDI.getBean(Server.class);
server.controllerPull.declaredControllers.add(
    new PersonController(ContainerDI.getBean(Statement.class),
                         ContainerDI.getBean(PostService.class)) // сервисы — тоже из DI
);
server.startServer();
```

Зависимости контроллера (Statement для Security, сервисы) берутся из DI-контейнера и передаются через конструктор.

### Клиент для тяжёлых файлов — FileClientPusher

Готовый программный клиент из примера. `sendFile(controllerName, methodName, params, File... files)`:

1. формирует текстовую часть `Controller/method<endl>p1<endl>...`;
2. пишет маркер `<BINARY>`;
3. каждый файл режет на куски по **64 МБ** (`CHUNK_SIZE`, согласовано с `Server.MAX_CHUNK_SIZE`) буфером на один кусок: `[флаг][writeInt(длина)][данные]`, последний кусок — флаг 0 (для определения «последний ли кусок» читает пробный байт вперёд через `PushbackInputStream`);
4. пишет терминатор `[0][writeInt(0)]`, `flush()`;
5. читает ответ до EOF (не разбирая фрейминг — для текстовых ответов это строка).

Клиент **не держит файл в памяти целиком** — один переиспользуемый буфер. Пустые файлы (0 байт) пропускает.

---

## Глава 4. Безопасность (Security)

Расположение: `vendor/Security/Security.java`. Модуль защищает роуты двумя механиками: **аутентификация** (кто вы: логин + пароль) и **авторизация** (что вам можно: роль). Пароли хранятся в БД только в виде **BCrypt-хешей**.

### Как подключить

Контроллер **наследуется от `Security`** и передаёт `Statement` в `super(...)` — через него модуль обращается к таблице пользователей:

```java
public class PersonController extends Security {

    public PersonController(Statement statement) {
        super(statement);
    }

    public ServerResponse createPersonAction(ClientRequest request) {
        String[] params = request.getData().getParams();

        if (super.checkRole("Person", "login", "password", "role", "USER", params)) {
            // доступ разрешён — вызываем сервис, результат кладём в ServerResponse
        }

        // отказ: returnException() возвращает String, поэтому оборачиваем в пакет
        ServerResponse denied = new ServerResponse();
        Data data = new Data();
        data.setParams(new String[]{super.returnException()});
        denied.setData(data);
        return denied;
    }
}
```

### Метод checkRole — сигнатура и механизм

```java
public boolean checkRole(
    String tableName,       // таблица пользователей, напр. "Person"
    String loginField,      // колонка логина
    String passwordField,   // колонка пароля (BCrypt-ХЕШ)
    String roleField,       // колонка роли
    String roleForChecking, // роль, допущенная к роуту ("USER", "ADMIN", …)
    String[] clientParams   // параметры запроса (ищем блок login<security>password)
)
```

Алгоритм по шагам:

1. **Извлечение данных входа.** `extractLoginAndPasswordFromClientQuery(clientParams)` сканирует массив параметров, находит тот, что содержит подстроку `<security>`, и делит его на две части (`split("<security>", 2)`): до маркера — логин, после — пароль. Если такого параметра нет — возвращает `{null, null}`.
2. **Запрос к БД.** Одним параметризованным запросом достаёт хеш пароля и роль:
   ```sql
   SELECT password, role FROM Person WHERE login = ? LIMIT 1
   ```
   Логин передаётся через плейсхолдер `?` — инъекционно-безопасно.
3. **Проверка пароля.** `checkHashedPassword(storedHash, password)` → `BCrypt.verifyer().verify(password.toCharArray(), storedHash).verified`.
4. **Проверка роли.** При совпадении пароля — строгое равенство строк `actualRole.equals(roleForChecking)`.
5. `true` возвращается только при совпадении «пароль + роль», иначе `false`.

### Хеширование пароля

```java
public static String hashPassword(String password)
```

`BCrypt.withDefaults().hashToString(12, password.toCharArray())` — хеш с cost-фактором 12. Логирует `HASHED_PASSWORD = …`. Вызывается при создании/смене пароля:

```java
person.password = Security.hashPassword("pass"); // в БД ляжет хеш, не открытый пароль
```

```java
public static boolean checkHashedPassword(String cryptedPassword, String password)
```

Сверяет переданный пароль с хешем из БД; логирует `PASSWORD_IS_VERIFIED = …`.

### Прочие методы

```java
public String returnException()
```

Возвращает строку отказа: `"ACCESS_DENIED: ОШИБКА! Данные логина и пароля не верны, либо ваша роль не предусматривает получение данных по данному роуту!"`. Возвращать её из экшена напрямую **нельзя** (сервер не отправит не-`ServerResponse`) — оборачивайте в пакет.

```java
public String[] extractLoginAndPasswordFromClientQuery(String[] clientParams)
```

Ищет блок `<security>` в параметрах и возвращает `{логин, пароль}` (или `{null, null}`). Внимание: в коде условный лог-месседж инвертирован — «ДАННЫЕ ДЛЯ ВХОДА НЕ НАЙДЕНЫ!» печатается как раз тогда, когда данные найдены. На логику проверки это не влияет.

### Требования и рекомендации

- Запрос к защищённому роуту **обязан** содержать блок `<endl>логин<security>пароль<endl>`. Без него `checkRole` вернёт `false`, даже если пароль верный.
- Пароль в БД — только хеш, иначе `checkRole` никогда не пройдёт (`BCrypt.verify` не совпадает с открытым текстом).
- Проверку `checkRole` выполняйте **до** обращения к сервисам — отказ в доступе не должен выполнять бизнес-логику.
- Блок `<security>` остаётся в массиве `params` — учитывайте его при обработке параметров (не передавайте в сервис как бизнес-данные).

---

## Ограничения фреймворка

- **Не универсальный веб-фреймворк.** JCore решает небольшие задачи: нет HTTP/HTTPS, cookies, сессий, REST-конвенций, асинхронности. Транспорт — самописный TCP-протокол поверх `ServerSocket`, клиент к нему тоже самописный.
- **Один запрос = одно соединение.** Keep-alive нет. Тяжёлую загрузку выполняйте отдельным запросом к upload-экшену, метаданные — обычными текстовыми запросами.
- **Пул из 4 воркеров.** Одновременно обрабатывается до 4 запросов; очередь сокетов не ограничена, но долгие/медленные клиенты и тяжёлые аплоады занимают воркеров и блокируют обработку.
- **Нет таймаутов на чтение.** Недобросовестный клиент может удерживать воркера сколь угодно долго.
- **Лимит куска — 64 МБ.** Кусок больше или отрицательная длина → разрыв соединения (`Server.MAX_CHUNK_SIZE`). Сам файл может быть любого размера — передача идёт стримингово, в памяти один кусок (и клиент, и сервер).
- **Пустой файл (0 байт) не передаётся** — неотличим от терминатора списка файлов.
- **Сервер не удаляет файлы из `uploads/`** — после обработки переносите их в своё хранилище или чистите вручную; иначе диск заполнится.
- **Экшен обязан возвращать `ServerResponse`.** Иначе клиент получит пустой ответ (EOF) без текста ошибки; причину ищите в консоли сервера (`CONTROLLER ERROR, real cause:`).
- **Потокобезопасность JDBC.** `Connection` и `Statement` — синглтоны; один общий `Statement` используют 4 воркера, а JDBC-`Statement` не потокобезопасен. Выполняйте запросы атомарно (один `executeSQL`/`executeUpdate` на операцию), не храните промежуточные результаты в полях синглтон-бинов.
- **Нюанс `setData` и `id`.** `id` берётся из бина сущности репозитория, а не из DTO; первый вызов вставит `id = 0`, повторный упадёт. Для клиентских данных — `executeUpdate`. Поля `byte[]` через `setData` вставляются как строка (`toString()`), что ломает BLOB — используйте `executeUpdate` с `setObject`.
- **Порядок `init()` важен.** Справочники → связующие (иначе `SQLException` внешнего ключа при старте).
- **Служебные маркеры.** `<endl>` в значениях параметров ломает парсинг, `<security>` — логин/пароль, `<BINARY>` — поиск границы бинарной части (ищется побайтово в тексте). Эти подстроки запрещены в данных.
- **`DataSerializer.convertToJson` не экранирует строки.** Кавычки и обратные слеши внутри значений в JSON уйдут «как есть» — экранируйте вручную, если данные непредсказуемы.
- **Сигнатура экшена строго `(ClientRequest)`.** Старые сигнатуры из v0.0.1/v0.0.2 не будут найдены рефлексией.
- **Запрет сторонних библиотек** (кроме JDK, `postgresql`, `bcrypt`) — всё необходимое уже на борту; недостающее реализуйте своими руками в слое сервисов.

---

## Глоссарий

| Термин | Определение |
|---|---|
| **JCore** | Легковесный Java-фреймворк для веб-приложений и API-сервисов на TCP-сокетах |
| **Бин (Bean)** | Объект, зарегистрированный в DI-контейнере; получается через `ContainerDI.getBean` |
| **DI (Dependency Injection)** | Внедрение зависимостей — выдача объектов из контейнера по типу класса |
| **Контейнер (ContainerDI)** | Статическая карта `HashMap<Class<?>, Object>` бинов |
| **Сущность (Entity)** | Класс-наследник `Entity`; описание таблицы БД, публичные поля = колонки |
| **EntityInfo** | Метаданные сущности: класс, поля (имя/тип/значение), список связей |
| **RelationField** | Описание связи сущностей: `refClass` + `valueKey` (внешний ключ) |
| **Репозиторий (Repository)** | DAO-надстройка над сущностью: `init()`, `setData()`, собственные выборки |
| **Сервис (Service)** | Слой бизнес-логики; владеет репозиториями, внедрёнными через конструктор |
| **Контроллер (Controller)** | Принимает `ClientRequest`, защищает роут (`Security`), вызывает сервис, возвращает `ServerResponse` |
| **Экшен (Action)** | Метод контроллера `public ServerResponse nameAction(ClientRequest request)` — целевая точка роута |
| **Роут (Route)** | Адрес запроса вида `ИмяКонтроллера/имяЭкшена` |
| **`ClientRequest`** | Разобранный запрос клиента: роут + пакет `Data` |
| **`Data`** | Контейнер данных: `String[] params` + `File[] binaryFiles` (пути к файлам) |
| **`ServerResponse`** | Ответ сервера: пакет `Data`, сериализуется тем же фреймингом, что и запрос |
| **Куcок (Chunk)** | Порция файла на проводе: `[флаг][длина][данные]`; лимит — 64 МБ |
| **Маркер** | Служебная подстрока протокола: `<endl>`, `<BINARY>`, `<security>` |
| **Терминатор** | Маркер конца списка файлов: `[флаг 0][int32 0]` |
| **Воркер (Worker)** | Один из 4 потоков сервера, обрабатывающих сокеты из очереди |
| **`uploads/`** | Папка, куда сервер стримит принятые файлы (`file_<UUID>.bin`) |
| **Security** | Аутентификация (BCrypt) + авторизация по ролям; защита роутов |
| **`checkRole`** | Метод проверки доступа: логин + пароль + роль пользователя из БД |
| **`setBeans()`** | Метод `ConfigDI` — первая строка `main()`, инициализация всех бинов |
| **BCrypt** | Алгоритм хеширования паролей (cost 12); применяется в `Security` |
| **PreparedStatement** | JDBC-запрос с плейсхолдерами `?` — защита от SQL-инъекций |