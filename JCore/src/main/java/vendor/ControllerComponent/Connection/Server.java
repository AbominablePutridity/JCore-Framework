package vendor.ControllerComponent.Connection;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import vendor.ControllerComponent.Controller;
import vendor.JCoreMeta;
import java.util.UUID;
import vendor.ControllerComponent.Connection.Exchange.ClientRequest;
import vendor.ControllerComponent.Connection.Exchange.Data;
import vendor.ControllerComponent.Connection.Exchange.ServerResponse;

/**
 * Серверная часть фреймворка.
 *
 * Логика приёма подключений и разбора текстовой части запроса не менялась:
 *  - пул из 4 потоков и BlockingQueue;
 *  - разбор текстовой части до маркера {@code <BINARY>};
 *  - формат маршрута и параметров (route<endl>param1<endl>param2<endl><BINARY>);
 *  - отправка ответа клиенту.
 *
 * Изменилась только обработка бинарной части запроса:
 *
 *  1. Куски файлов больше НЕ собираются в цепочку в памяти.
 *     Раньше сервер строил односвязный список FileChunk и передавал его
 *     контроллеру целиком. Это приводило к тому, что весь файл (а при
 *     нескольких файлах — все файлы сразу) оказывался в heap.
 *
 *  2. Теперь каждый кусок пишется на диск СРАЗУ после чтения из сокета.
 *     В памяти в каждый момент находится только ОДИН кусок, независимо
 *     от размера файла и их количества в запросе.
 *
 *  3. Контроллер получает {@code File[]} — массив путей к уже сохранённым
 *     на диске файлам. Данных в этих объектах нет: {@link File} — это
 *     ссылка на файл в файловой системе, ~100 байт.
 *
 * Формат бинарной части на проводе (не изменился):
 *
 *     [1 байт: флаг продолжения (1 = есть следующий кусок, 0 = последний)]
 *     [4 байта: длина этого куска (big-endian int)]
 *     [N байт: данные куска]
 *
 * Маркер конца списка файлов — пустой кусок без продолжения:
 *
 *     [0][0][0][0][0]   (continueFlag = 0, chunkSize = 0)
 *
 * @author User
 */
public class Server {

    // Пул из 4 потоков для обработки клиентов
    private static final int THREAD_POOL_SIZE = 4;

    /**
     * Верхняя граница размера одного куска.
     *
     * Защищает сервер от злонамеренного или багованного клиента, который
     * пришлёт {@code chunkSize = Integer.MAX_VALUE}, заставив сервер
     * выделить ~2 ГБ на один кусок. При 4 рабочих потоках это быстро
     * приведёт к OutOfMemoryError.
     *
     * Значение должно быть согласовано с клиентом. 64 МБ — разумный
     * компромисс между накладными расходами протокола (5 байт заголовка
     * на кусок) и пиком памяти.
     */
    private static final int MAX_CHUNK_SIZE = 64 * 1024 * 1024;

    /**
     * Очередь входящих сокетов.
     * Основной поток только принимает подключения и кладёт их сюда,
     * а рабочие потоки разбирают очередь параллельно.
     */
    private BlockingQueue<Socket> queue = new LinkedBlockingQueue<>();

    private int port;
    
    private final String MAIN_UPLOAD_DIR = "uploads" + File.separator; // путь к загруженным файлам от клиента

    /**
     * Объект, обрабатывающий маршрутизацию контроллеров.
     * Именно ему сервер передаёт роут, параметры и пути к сохранённым файлам.
     */
    public Controller controllerPull;

    public Server(Controller controllerPull, int port) {
        this.controllerPull = controllerPull;
        this.port = port;
    }

    /**
     * Запускает сервер: создаёт ServerSocket, стартует рабочие потоки
     * и входит в бесконечный цикл приёма подключений.
     *
     * НЕ ТРОГАЕМ — логика не менялась.
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
     * Обрабатывает клиентское подключение в текущем потоке.
     *
     * Логика не менялась: читаем запрос, передаём в контроллер, отвечаем клиенту.
     * Изменился только тип передаваемых бинарных данных — теперь File[]
     * (пути к файлам на диске), а не FileChunk[] (цепочки данных в памяти).
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

            System.out.println("Получен роут: " + request.getRoute());

            // Передаём контроллеру роут, текстовые параметры и массив File —
            // путей к уже сохранённым на диске файлам.
            Object result = controllerPull.startMethodByUrl(
                    request
            );

            // Отправляем ответ клиенту.
            if (result instanceof ServerResponse) { // проверяем, что возвращаемый обьект принадлежит классу ServerResponse
                ServerResponse resp = (ServerResponse) result;
                resp.convertDataToBytesForStream(clientSocket.getOutputStream());
            } else {
                // иначе печатаем ошибку
                System.out.println("ERROR: контроллер возвратил не обьект класса ServerResponse!");
            }

            //clientSocket.close();
            
            /*PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            
            
            if (result != null) {
                out.println(result.toString());
            } else {
                out.println("ERROR: Controller returned null");
            }*/
            
            

            System.out.println("Ответ отправлен клиенту");

            clientSocket.close();
            System.out.println("Соединение закрыто");

        } catch (IOException e) {
            System.out.println("Ошибка при работе с клиентом: " + e.getMessage());
            e.printStackTrace();
        } catch (Throwable t) {
            // Ловим всё, включая RuntimeException/Error из контроллера.
            System.out.println("НЕОЖИДАННАЯ ОШИБКА в handleClient:");
            t.printStackTrace();
        }
    }

    /**
     * Читает запрос клиента.
     *
     * Текстовая часть имеет формат:
     *   PersonController/createPersonAction<endl>helloWorld!<endl>JCore!<endl><BINARY>
     *
     * После {@code <BINARY>} начинается бинарная часть — список файлов.
     *
     * Каждый файл — это один или несколько кусков.
     * Формат одного куска:
     *   [1 байт: флаг продолжения (1 = есть следующий кусок, 0 = последний)]
     *   [4 байта: длина этого куска]
     *   [N байт: данные куска]
     *
     * Сервер НЕ собирает куски в памяти — он пишет их на диск по мере
     * поступления (см. {@link #readOneFileToDisk}).
     *
     * НЕ ТРОГАЕМ — логика поиска маркера не менялась.
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
     * вызываем {@link #readOneFileToDisk} и добавляем полученный File
     * (путь к файлу на диске) в общий список.
     *
     * ВАЖНО: никакой склейки кусков в памяти не происходит. Каждый файл
     * пишется на диск сразу, а контроллер получает только пути.
     */
    private ClientRequest createClientRequest(String text, DataInputStream binaryInput) throws IOException {

        ClientRequest request = new ClientRequest();

        // Разбираем текстовую часть.
        String[] parsedData = parseClientQuery(text, "<endl>");

        if (parsedData.length < 1) {
            return null;
        }

        // Первый элемент — роут.
        request.setRoute(parsedData[0]);

        // Остальные — текстовые параметры.
        Data clientData = new Data();
        clientData.setParams(new String[parsedData.length - 1]);
        
        request.setData(clientData); //.setParams(new String[parsedData.length - 1]);
        for (int i = 1; i < parsedData.length; i++) {
            request.getData().getParams()[i - 1] = parsedData[i];
        }

        // Если бинарной части нет — возвращаем пустой массив.
        if (binaryInput == null) {
            request.getData().setBinaryFiles(new File[0]);
            return request;
        }

        // Читаем файлы, пока они есть в потоке.
        //
        // Имена файлов формируются сервером динамически (file_0._,
        // file_1._, ...), чтобы несколько файлов в одном запросе
        // не перезаписывали друг друга. Расширение — "jpg" по умолчанию;
        // при необходимости можно вынести в параметр метода.
        List<File> files = new ArrayList<>();
        int index = 0;

        while (true) {
            System.out.println("DEBUG: читаю файл #" + index);
            
            // проверяем наличие директорий для файлов, если не существует - создаем
            File parentDir = new File(MAIN_UPLOAD_DIR);
            if(!parentDir.exists())
            {
                parentDir.mkdirs();
            }
            
            String id = UUID.randomUUID().toString();
            File f = readOneFileToDisk(binaryInput, MAIN_UPLOAD_DIR + "file_" + id, "bin");
            if (f == null) {
                System.out.println("DEBUG: файлов больше нет, прочитано " + files.size());
                break;
            }
            files.add(f);
            index++;
        }

        request.getData().setBinaryFiles(files.toArray(new File[0]));
        System.out.println("DEBUG: итого файлов: " + request.getData().getBinaryFiles().length);
        return request;
    }

    /**
     * Читает один файл из потока и СРАЗУ пишет его куски на диск.
     *
     * В отличие от старой версии, которая строила цепочку FileChunk
     * в памяти, этот метод держит в heap только ОДИН кусок за раз:
     *   - прочитали кусок,
     *   - записали его в FileOutputStream,
     *   - отпустили ссылку (GC соберёт),
     *   - перешли к следующему.
     *
     * Пик памяти = размер одного куска, а не всего файла.
     * Это позволяет корректно принимать файлы любого размера — хоть 10 ГБ,
     * хоть 100 ГБ, — при фиксированном потреблении памяти.
     *
     * Возвращаемое значение:
     *   - {@link File} — путь к сохранённому файлу, если файл был прочитан;
     *   - {@code null} — если достигнут маркер конца списка файлов
     *     (пустой кусок без продолжения до того, как что-либо записали).
     *
     * Обработка ошибок:
     *   - Если поток закрылся до начала файла — возвращаем null,
     *     частично созданный файл удаляем.
     *   - Если поток закрылся в середине файла — возвращаем то, что успели
     *     записать (файл будет неполным). Альтернатива — бросить исключение;
     *     текущее поведение выбрано как более мягкое.
     *   - Если запись на диск упала — удаляем частично записанный файл
     *     и пробрасываем исключение наверх.
     *
     * @param in        поток бинарных данных.
     * @param fileName  имя файла БЕЗ расширения (например, "file_0").
     * @param extension расширение (например, "jpg" или ".jpg" — нормализуется).
     *                  Может быть {@code null} или пустым — тогда ".bin".
     * @return File — путь к сохранённому файлу, либо null, если достигнут
     *         конец списка файлов.
     * @throws IOException при ошибке чтения из потока или записи на диск.
     */
    private File readOneFileToDisk(
        DataInputStream in,
        String fileName,
        String extension
) throws IOException {

    // ----------------------------------------------------------------
    // 0. Нормализуем расширение и готовим целевой файл.
    // ----------------------------------------------------------------
    String suffix;
    if (extension == null || extension.isEmpty()) {
        suffix = ".bin";
    } else if (extension.startsWith(".")) {
        suffix = extension;
    } else {
        suffix = "." + extension;
    }

    File output = new File(fileName + suffix);

    System.out.println("DEBUG: создаю " + output.getAbsolutePath());

    if (output.exists()) {
        boolean deleted = output.delete();
        System.out.println("DEBUG: удалил старый? " + deleted);
    }

    // Флаг, что мы записали хотя бы один кусок.
    boolean wroteAnyChunk = false;

    // ВАЖНО: поток открываем внутри try, но НЕ оборачиваем в try-with-resources
    // вокруг всего цикла — иначе delete() в catch сработает до close().
    FileOutputStream fos = null;

    try {
        fos = new FileOutputStream(output);

        while (true) {

            int continueFlag;
            int chunkSize;

            try {
                System.out.println("DEBUG: читаю заголовок куска...");
                continueFlag = in.readByte() & 0xFF;
                chunkSize = in.readInt();
                System.out.println("DEBUG: continueFlag=" + continueFlag
                        + ", chunkSize=" + chunkSize);
            } catch (IOException e) {
                System.out.println("DEBUG: EOF на чтении заголовка, "
                        + "wroteAnyChunk=" + wroteAnyChunk);

                // Закрываем поток ДО удаления, иначе Windows не даст удалить.
                try { fos.close(); } catch (IOException ignored) {}
                fos = null;

                if (!wroteAnyChunk) {
                    boolean deleted = output.delete();
                    System.out.println("DEBUG: удалил пустой файл? " + deleted);
                    return null;
                }
                return output;
            }

            // Маркер конца списка файлов.
            if (chunkSize == 0 && continueFlag == 0 && !wroteAnyChunk) {
                try { fos.close(); } catch (IOException ignored) {}
                fos = null;
                boolean deleted = output.delete();
                System.out.println("DEBUG: маркер конца, удалил? " + deleted);
                return null;
            }

            if (chunkSize < 0) {
                throw new IOException("Некорректный размер куска: " + chunkSize);
            }
            if (chunkSize > MAX_CHUNK_SIZE) {
                throw new IOException(
                        "Слишком большой кусок: " + chunkSize
                                + " (максимум " + MAX_CHUNK_SIZE + ")"
                );
            }

            byte[] chunkData = new byte[chunkSize];
            in.readFully(chunkData);
            fos.write(chunkData);
            wroteAnyChunk = true;

            System.out.println("DEBUG: записал кусок " + chunkSize + " байт");

            if (continueFlag == 0) {
                fos.close();
                fos = null;
                System.out.println("DEBUG: файл готов: "
                        + output.getAbsolutePath()
                        + ", размер " + output.length());
                return output;
            }
        }

    } catch (IOException e) {
        // Закрываем поток, потом удаляем — иначе Windows не даст.
        if (fos != null) {
            try { fos.close(); } catch (IOException ignored) {}
        }
        try {
            Files.deleteIfExists(output.toPath());
        } catch (IOException suppressed) {
            e.addSuppressed(suppressed);
        }
        throw e;
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