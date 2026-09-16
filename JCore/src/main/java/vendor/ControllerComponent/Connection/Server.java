package vendor.ControllerComponent.Connection;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import vendor.ControllerComponent.Controller;
import vendor.JCoreMeta;

/**
 * Серверная часть фреймворка.
 *
 * Что изменилось по сравнению с предыдущей версией:
 *
 *  1. Тип бинарных данных в запросе изменён с {@code byte[][]} на {@code FileChunk[]}.
 *     Теперь каждый "файл" в запросе — это не один byte[], а цепочка кусков,
 *     потому что физически один byte[] не может содержать файл >2ГБ.
 *
 *  2. Метод чтения бинарной части больше НЕ склеивает куски в один массив.
 *     Он строит односвязный список {@link FileChunk} по мере поступления кусков
 *     из сокета. Склейка — ответственность контроллера (см. FileChunk.mergeAll).
 *
 *  3. Формат каждого куска на проводе теперь содержит дополнительный байт-флаг
 *     "есть ли продолжение" перед 4-байтовым размером:
 *
 *         [1 байт: флаг продолжения (1 = есть следующий кусок, 0 = последний)]
 *         [4 байта: длина этого куска]
 *         [N байт: данные куска]
 *
 *     Это позволяет отличать "продолжение того же файла" от "начало нового файла"
 *     и корректно обрабатывать несколько файлов в одном запросе.
 *
 *  4. Появился вспомогательный метод {@code readOneFile}, который читает
 *     ровно один файл (одну цепочку кусков) из потока.
 *
 * Что осталось без изменений:
 *  - пул из 4 потоков и BlockingQueue;
 *  - разбор текстовой части до маркера <BINARY>;
 *  - формат маршрута и параметров (route<endl>param1<endl>param2<endl><BINARY>);
 *  - отправка ответа клиенту.
 *
 * @author User
 */
public class Server {

    // Пул из 4 потоков для обработки клиентов
    private static final int THREAD_POOL_SIZE = 4;

    /**
     * Очередь входящих сокетов.
     * Основной поток только принимает подключения и кладёт их сюда,
     * а рабочие потоки разбирают очередь параллельно.
     */
    private BlockingQueue<Socket> queue = new LinkedBlockingQueue<>();

    private int port;

    /**
     * Объект, обрабатывающий маршрутизацию контроллеров.
     * Именно ему сервер передаёт роут, параметры и бинарные файлы.
     */
    public Controller controllerPull;

    public Server(Controller controllerPull, int port) {
        this.controllerPull = controllerPull;
        this.port = port;
    }

    /**
     * Запускает сервер: создаёт ServerSocket, стартует рабочие потоки
     * и входит в бесконечный цикл приёма подключений.
     */
    public void startServer() throws IOException, InterruptedException {
        ServerSocket serverSocket = new ServerSocket(port);

        JCoreMeta.logoRenderer();

        System.out.println("Сервер запущен на порту: " + port);

        // Запускаем 4 рабочих потока.
        // Каждый поток в бесконечном цикле берёт сокет из очереди и обрабатывает его.
        for (int i = 0; i < THREAD_POOL_SIZE; i++) {
            new Thread(() -> {
                while (true) {
                    try {
                        Socket client = queue.take(); // блокируется, пока не появится сокет
                        handleClient(client);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }

        // Основной поток принимает подключения и кладёт их в очередь.
        // Он не блокируется на обработке — это делают рабочие потоки.
        while (true) {
            Socket clientSocket = serverSocket.accept();
            queue.put(clientSocket);
        }
    }

    /**
     * Разобранный запрос клиента.
     *
     * Отличие от предыдущей версии — тип поля binaryFiles.
     * Раньше было byte[][]: один массив на файл.
     * Теперь FileChunk[]: один FileChunk-цепочка на файл.
     *
     * Каждый FileChunk — это либо одиночный кусок (файл <= ~2ГБ),
     * либо голова цепочки из нескольких кусков (файл > ~2ГБ).
     */
    private static class ClientRequest {

        // Например: PersonController/createPersonAction
        String route;

        // Например: ["helloWorld!", "JCore!"]
        String[] params;

        // Например:
        // [
        //     FileChunk (цепочка для файла 1),
        //     FileChunk (цепочка для файла 2),
        //     FileChunk (цепочка для файла 3)
        // ]
        //
        // Количество файлов в массиве — любое.
        // Количество кусков внутри каждой цепочки — любое (ограничено только памятью).
        FileChunk[] binaryFiles;
    }

    /**
     * Обрабатывает клиентское подключение в текущем потоке.
     *
     * Логика не изменилась: читаем запрос, передаём в контроллер, отвечаем клиенту.
     * Изменился только тип передаваемых бинарных данных — теперь FileChunk[].
     */
    private void handleClient(Socket clientSocket) {
        try {
            System.out.println("Новое подключение: " + clientSocket.getInetAddress());

            ClientRequest request = readClientRequest(clientSocket.getInputStream());

            if (request == null) {
                System.out.println("Получен пустой запрос");
                clientSocket.close();
                return;
            }

            System.out.println("Получен роут: " + request.route);

            // Передаём контроллеру роут, текстовые параметры и массив FileChunk-цепочек.
            // Контроллер сам решает, склеивать ли куски в файл (через FileChunk.mergeAll)
            // или обрабатывать их потоково.
            Object result = controllerPull.startMethodByUrl(
                    request.route,
                    request.params,
                    request.binaryFiles
            );

            // Отправляем ответ клиенту.
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            if (result != null) {
                out.println(result.toString());
            } else {
                out.println("ERROR: Controller returned null");
            }

            System.out.println("Ответ отправлен клиенту");

            clientSocket.close();
            System.out.println("Соединение закрыто");

        } catch (IOException e) {
            System.out.println("Ошибка при работе с клиентом: " + e.getMessage());
        }
    }

    /**
     * Читает запрос клиента.
     *
     * Текстовая часть имеет формат:
     *   PersonController/createPersonAction<endl>helloWorld!<endl>JCore!<endl><BINARY>
     *
     * После <BINARY> начинается бинарная часть — список файлов.
     *
     * Каждый файл — это один или несколько кусков.
     * Формат одного куска:
     *   [1 байт: флаг продолжения (1 = есть следующий кусок, 0 = последний)]
     *   [4 байта: длина этого куска]
     *   [N байт: данные куска]
     *
     * Сервер НЕ склеивает куски — он строит цепочку FileChunk
     * и передаёт её контроллеру как единый "файл".
     *
     * Логика чтения текстовой части не изменилась — мы по-прежнему
     * ищем маркер <BINARY> побайтово, чтобы не сломать бинарные данные.
     */
    private ClientRequest readClientRequest(InputStream inputStream) throws IOException {

        DataInputStream in = new DataInputStream(inputStream);

        // Буфер для текстовой части (всё до <BINARY>).
        ByteArrayOutputStream textBuffer = new ByteArrayOutputStream();

        byte[] binaryMarker = "<BINARY>".getBytes(StandardCharsets.UTF_8);

        // Индекс текущего совпадающего символа маркера.
        // Используется для поиска подстроки <BINARY> в потоке байт.
        int markerIndex = 0;

        while (true) {
            int currentByte = in.read();

            if (currentByte == -1) {
                // Клиент закрыл соединение до появления <BINARY>.
                break;
            }

            textBuffer.write(currentByte);

            // Проверяем, не начали ли мы находить <BINARY>.
            if (currentByte == binaryMarker[markerIndex]) {
                markerIndex++;

                if (markerIndex == binaryMarker.length) {
                    // Маркер найден полностью.
                    // Убираем его из текстовой части — он не является частью данных.
                    byte[] textData = textBuffer.toByteArray();
                    byte[] textWithoutBinaryMarker = new byte[textData.length - binaryMarker.length];
                    System.arraycopy(textData, 0, textWithoutBinaryMarker, 0, textWithoutBinaryMarker.length);

                    String text = new String(textWithoutBinaryMarker, StandardCharsets.UTF_8);

                    // Переходим к чтению бинарной части.
                    return createClientRequest(text, in);
                }
            } else {
                // Несовпадение — сбрасываем индекс маркера.
                // (Упрощённый поиск: не учитывает случаи вроде "<BIN<BINARY>",
                //  но для текущего протокола этого достаточно.)
                markerIndex = 0;
            }
        }

        // Если <BINARY> не было — обрабатываем запрос только как текст.
        String text = new String(textBuffer.toByteArray(), StandardCharsets.UTF_8);
        return createClientRequest(text, null);
    }

    /**
     * Создаёт объект запроса из текстовой части и бинарных файлов.
     *
     * Текстовая часть разбирается как раньше: первый элемент — роут,
     * остальные — текстовые параметры.
     *
     * Бинарная часть читается в цикле: пока в потоке есть файлы,
     * вызываем {@link #readOneFile(DataInputStream)} и добавляем результат
     * (голову цепочки кусков) в общий список файлов.
     *
     * ВАЖНО: здесь нет никакой склейки кусков. Каждый файл — это именно
     * цепочка FileChunk, которую контроллер получит как единое целое.
     */
    private ClientRequest createClientRequest(String text, DataInputStream binaryInput) throws IOException {

        ClientRequest request = new ClientRequest();

        // Разбираем текстовую часть.
        String[] parsedData = parseClientQuery(text, "<endl>");

        if (parsedData.length < 1) {
            return null;
        }

        // Первый элемент — роут.
        request.route = parsedData[0];

        // Остальные — текстовые параметры.
        request.params = new String[parsedData.length - 1];
        for (int i = 1; i < parsedData.length; i++) {
            request.params[i - 1] = parsedData[i];
        }

        // Если бинарной части нет — возвращаем пустой массив.
        if (binaryInput == null) {
            request.binaryFiles = new FileChunk[0];
            return request;
        }

        // Читаем файлы, пока они есть в потоке.
        List<FileChunk> files = new ArrayList<>();

        while (true) {
            FileChunk head;
            try {
                head = readOneFile(binaryInput);
            } catch (IOException e) {
                // Поток закрылся или данных больше нет — прекращаем чтение.
                break;
            }

            if (head == null) {
                // Маркер конца списка файлов (пустой кусок без продолжения).
                break;
            }

            files.add(head);
        }

        request.binaryFiles = files.toArray(new FileChunk[0]);
        return request;
    }

    /**
     * Читает один файл как цепочку кусков.
     *
     * Алгоритм:
     *   1. Читаем флаг продолжения (1 байт) и размер куска (4 байта).
     *   2. Читаем данные куска.
     *   3. Создаём FileChunk и добавляем его в конец цепочки.
     *   4. Если флаг == 0 — файл закончился, возвращаем голову цепочки.
     *      Если флаг == 1 — продолжаем читать следующий кусок того же файла.
     *
     * Особые случаи:
     *   - Пустой кусок с флагом 0 (chunkSize == 0, continueFlag == 0) —
     *     это маркер конца списка файлов. Возвращаем null.
     *   - Ошибка чтения в самом начале — тоже считаем концом списка (возвращаем head).
     *   - Ошибка чтения в середине файла — тоже возвращаем head, но это уже
     *     означает, что файл пришёл неполным. При желании можно бросать исключение.
     *
     * Поле {@code next} в FileChunk НЕ final, поэтому мы можем достраивать
     * цепочку по мере поступления кусков:
     *   - head указывает на первый кусок;
     *   - tail указывает на последний добавленный кусок;
     *   - при добавлении нового куска делаем tail.next = newChunk и сдвигаем tail.
     *
     * Это ровно тот сценарий, ради которого next сделан не final.
     *
     * @param in поток бинарных данных.
     * @return голова цепочки кусков одного файла, либо null, если достигнут конец списка файлов.
     */
    private FileChunk readOneFile(DataInputStream in) throws IOException {

        // Голова цепочки — то, что вернём в итоге.
        FileChunk head = null;

        // Хвост цепочки — последний добавленный кусок.
        // Нужен, чтобы добавлять следующий кусок за O(1), а не пробегать всю цепочку каждый раз.
        FileChunk tail = null;

        while (true) {

            // Шаг 1. Читаем флаг продолжения.
            // readByte() возвращает signed byte, поэтому приводим к unsigned через & 0xFF.
            // Получаем 0 или 1 (по протоколу), но на всякий случай допускаем любое значение != 0
            // как "есть продолжение" — см. проверку ниже.
            int continueFlag;
            try {
                continueFlag = in.readByte() & 0xFF;
            } catch (IOException e) {
                // Поток закрылся — файлов больше нет.
                return head;
            }

            // Шаг 2. Читаем размер куска (4 байта, big-endian).
            // readInt() читает именно 4 байта и собирает их в int.
            int chunkSize = in.readInt();

            // Шаг 3. Проверяем маркер конца списка файлов:
            // пустой кусок без продолжения. Так клиент сигнализирует,
            // что файлов больше не будет.
            if (chunkSize == 0 && continueFlag == 0) {
                return head;
            }

            // Защита от некорректных данных.
            if (chunkSize < 0) {
                throw new IOException("Некорректный размер куска: " + chunkSize);
            }

            // Шаг 4. Читаем данные куска целиком.
            // readFully блокируется, пока не прочитает ровно chunkSize байт
            // (или не бросит EOFException, если поток закрылся раньше).
            byte[] chunkData = new byte[chunkSize];
            in.readFully(chunkData);

            // Шаг 5. Создаём узел цепочки.
            FileChunk chunk = new FileChunk(chunkData);

            // Шаг 6. Добавляем кусок в конец цепочки.
            if (head == null) {
                // Это первый кусок файла — он же и голова, и хвост.
                head = chunk;
                tail = chunk;
            } else {
                // Присоединяем новый кусок после текущего хвоста
                // и сдвигаем хвост. Именно ради этой операции next не final.
                tail.next = chunk;
                tail = chunk;
            }

            // Шаг 7. Если флаг == 0 — файл закончился, возвращаем голову.
            // Если флаг != 0 — значит, будет ещё кусок этого же файла,
            // и цикл продолжается.
            if (continueFlag == 0) {
                return head;
            }
        }
    }

    /**
     * Парсит запрос пользователя по разделителю.
     *
     * @param query     строка запроса.
     * @param delimeter разделитель (в нашем случае "<endl>").
     * @return массив разобранных элементов.
     */
    private String[] parseClientQuery(String query, String delimeter) {
        return query.split(delimeter);
    }
}