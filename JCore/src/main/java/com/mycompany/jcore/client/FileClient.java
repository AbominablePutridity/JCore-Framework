package com.mycompany.jcore.client;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Простой клиент для отправки файлов на сервер JCore.
 *
 * Изменения по сравнению с предыдущей версией:
 *
 *  1. Поддержка файлов любого размера, в том числе > 2ГБ.
 *     Раньше клиент делал {@code new byte[(int) file.length()]} — это падало
 *     на файлах > 2ГБ (переполнение int) и уж точно не влезало в byte[].
 *
 *  2. Файл читается и отправляется ПО КУСКАМ, каждый кусок не больше
 *     {@link #MAX_CHUNK_SIZE} (~2ГБ с запасом). Клиент НЕ держит весь файл
 *     в памяти — только один кусок за раз.
 *
 *  3. Формат каждого куска на проводе теперь:
 *       [1 байт: флаг продолжения (1 = есть следующий кусок, 0 = последний)]
 *       [4 байта: длина этого куска]
 *       [N байт: данные куска]
 *
 *     Последний кусок каждого файла помечается флагом 0.
 *     Все промежуточные куски — флагом 1.
 *
 *  4. Маркер окончания списка файлов остался тем же по смыслу:
 *     пустой кусок с флагом 0 (chunkSize == 0, continueFlag == 0).
 *
 * Использование:
 *   FileClient client = new FileClient("127.0.0.1", 8082);
 *   client.sendFile("PersonController", "createPersonAction",
 *                   new String[]{"param1", "param2"},
 *                   new File("test.txt"));
 *
 * @author User
 */
public class FileClient {

    /**
     * Максимальный размер одного куска файла.
     *
     * Integer.MAX_VALUE = 2147483647 (~2ГБ - 1 байт).
     * Оставляем запас, чтобы точно не выйти за границы int при арифметике
     * (и чтобы заголовок куска тоже поместился в разумные пределы).
     */
    private static final int MAX_CHUNK_SIZE = Integer.MAX_VALUE - 1024;

    private String host;
    private int port;

    public FileClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Отправляет файл(ы) на указанный роут контроллера.
     *
     * Каждый файл автоматически режется на куски, если он больше MAX_CHUNK_SIZE.
     * Клиент не загружает файл целиком в память — читает и отправляет по кускам.
     *
     * @param controllerName Имя контроллера (например, "PersonController")
     * @param methodName     Имя метода (например, "createPersonAction")
     * @param params         Текстовые параметры запроса
     * @param files          Файлы для отправки (любого размера)
     * @return Ответ от сервера
     */
    public String sendFile(
            String controllerName,
            String methodName,
            String[] params,
            File... files
    ) throws IOException {

        try (Socket socket = new Socket(host, port)) {

            DataOutputStream out = new DataOutputStream(
                    socket.getOutputStream()
            );

            InputStream in = socket.getInputStream();

            // ------------------------------------------------------------------
            // 1. Формируем и отправляем текстовую часть запроса.
            //    Формат тот же: ControllerName/methodName<endl>param1<endl>...
            // ------------------------------------------------------------------
            StringBuilder textPart = new StringBuilder();
            textPart.append(controllerName);
            textPart.append("/");
            textPart.append(methodName);

            for (String param : params) {
                textPart.append("<endl>");
                textPart.append(param);
            }

            byte[] textBytes = textPart.toString().getBytes(StandardCharsets.UTF_8);

            out.write(textBytes);
            out.write("<BINARY>".getBytes(StandardCharsets.UTF_8));

            // ------------------------------------------------------------------
            // 2. Отправляем каждый файл по кускам.
            // ------------------------------------------------------------------
            for (File file : files) {
                sendFile(out, file);
            }

            // ------------------------------------------------------------------
            // 3. Маркер окончания списка файлов:
            //    пустой кусок с флагом 0 (то есть "последний кусок, длина 0").
            //    Сервер распознаёт это как "файлов больше нет".
            // ------------------------------------------------------------------
            out.writeByte(0);   // флаг: это последний кусок (продолжения нет)
            out.writeInt(0);    // длина: 0
            out.flush();

            // ------------------------------------------------------------------
            // 4. Читаем ответ от сервера.
            // ------------------------------------------------------------------
            StringBuilder response = new StringBuilder();
            int buffer;

            while ((buffer = in.read()) != -1) {
                response.append((char) buffer);
            }

            return response.toString();
        }
    }

    /**
     * Отправляет один файл, автоматически нарезая его на куски.
     *
     * Алгоритм:
     *   1. Открываем FileInputStream.
     *   2. Читаем очередной кусок размером не более MAX_CHUNK_SIZE.
     *   3. Определяем, последний ли это кусок: пробуем прочитать ещё 1 байт.
     *      - если read() вернул -1, значит поток закончился, это последний кусок;
     *      - если вернул какой-то байт, значит есть продолжение —
     *        запоминаем этот байт и приклеиваем его к началу следующего куска.
     *   4. Пишем в сокет: флаг продолжения (1 байт), длину (4 байта), данные.
     *   5. Повторяем, пока не отправим все куски.
     *
     * Такой подход позволяет отправлять файлы любого размера, не загружая
     * их целиком в память: в каждый момент в памяти только один кусок.
     *
     * @param out  поток вывода в сокет.
     * @param file файл для отправки.
     */
    private void sendFile(DataOutputStream out, File file) throws IOException {

        // Полный размер файла по метаданным FS (long — не int!).
        // Используем для логов и для оценки количества кусков.
        long fileSize = file.length();

        System.out.println(
                "Отправка файла: " + file.getName() +
                ", размер: " + fileSize + " байт" +
                ", кусков: " + estimateChunkCount(fileSize)
        );

        try (FileInputStream fis = new FileInputStream(file)) {

            // Буфер ровно под один кусок.
            // Выделяется один раз и переиспользуется для всех кусков файла,
            // чтобы не мусорить в heap'е.
            byte[] buffer = new byte[MAX_CHUNK_SIZE];

            // Флаг: был ли предыдущий прочитанный кусок "неполным",
            // то есть закончился ли он ровно на границе MAX_CHUNK_SIZE,
            // и остался ли в файле хотя бы один байт после него.
            //
            // -1 означает "граница ещё не обнаружена".
            // 0..255 — "первый байт следующего куска, уже прочитанный".
            int pendingByte = -1;

            while (true) {

                // ------------------------------------------------------------------
                // Читаем очередной кусок в buffer.
                //
                // read() у FileInputStream может вернуть меньше, чем запрошено,
                // даже если в файле ещё есть данные. Поэтому читаем в цикле,
                // пока не заполним buffer целиком или не упрёмся в конец файла.
                // ------------------------------------------------------------------

                // Если у нас есть "pendingByte" с прошлой итерации,
                // кладём его первым байтом в новый кусок.
                int bytesRead = 0;
                if (pendingByte != -1) {
                    buffer[0] = (byte) pendingByte;
                    bytesRead = 1;
                    pendingByte = -1;
                }

                // Дочитываем остаток куска.
                while (bytesRead < MAX_CHUNK_SIZE) {
                    int read = fis.read(
                            buffer,
                            bytesRead,
                            MAX_CHUNK_SIZE - bytesRead
                    );

                    if (read == -1) {
                        // Конец файла.
                        break;
                    }

                    bytesRead += read;
                }

                if (bytesRead == 0) {
                    // Файл пустой или полностью закончился.
                    // (Для пустого файла всё равно надо отправить один кусок
                    //  с нулевой длиной и флагом 0, чтобы сервер знал,
                    //  что файл есть, но пустой. Ниже это обрабатывается.)
                    break;
                }

                // ------------------------------------------------------------------
                // Определяем, есть ли продолжение.
                //
                // Пробуем прочитать ещё один байт после заполненного куска.
                // ------------------------------------------------------------------
                int nextByte = fis.read();

                boolean hasNext = (nextByte != -1);

                if (hasNext) {
                    // Следующий кусок точно будет — запоминаем байт,
                    // с которого он начнётся.
                    pendingByte = nextByte;
                }

                // ------------------------------------------------------------------
                // Пишем кусок на сервер:
                //   [1 байт: флаг продолжения]
                //   [4 байта: длина]
                //   [N байт: данные]
                // ------------------------------------------------------------------

                // Флаг: 1 = есть следующий кусок, 0 = последний.
                out.writeByte(hasNext ? 1 : 0);

                // Длина куска.
                out.writeInt(bytesRead);

                // Данные куска.
                out.write(buffer, 0, bytesRead);

                // Если продолжения нет — выходим из цикла, файл отправлен.
                if (!hasNext) {
                    break;
                }
            }

            // Отдельный случай: пустой файл (fileSize == 0).
            // Цикл выше не отправил ни одного куска, потому что bytesRead == 0
            // с самого начала. Отправим "пустой последний кусок".
            if (fileSize == 0) {
                out.writeByte(0); // последний кусок
                out.writeInt(0);  // длина 0
            }

            // Сбрасываем буфер в сокет.
            out.flush();
        }
    }

    /**
     * Оценка количества кусков для файла заданного размера.
     * Нужна только для логов — фактическое количество кусков
     * может отличаться на 1 в зависимости от того, попадёт ли
     * размер файла ровно на границу.
     *
     * @param fileSize размер файла в байтах.
     * @return приблизительное количество кусков.
     */
    private long estimateChunkCount(long fileSize) {
        if (fileSize == 0) {
            return 1; // пустой файл — всё равно один "кусок"
        }
        // ceil(fileSize / MAX_CHUNK_SIZE) без double-арифметики.
        return (fileSize + MAX_CHUNK_SIZE - 1) / MAX_CHUNK_SIZE;
    }

    /**
     * Простой пример использования клиента.
     */
    public static void main(String[] args) {

        FileClient client = new FileClient("127.0.0.1", 8082);

        try {
            // Путь можно заменить на любой большой файл — например, 4ГБ видео.
            File photo = new File("../test_photo.jpg");

            if (!photo.exists()) {
                System.err.println("Файл не найден: " + photo.getAbsolutePath());
                return;
            }

            System.out.println(
                    "Отправляем файл (" + photo.length() + " байт)..."
            );

            String response = client.sendFile(
                    "PersonController",
                    "createPersonAction",
                    new String[]{"upload_photo"},
                    photo
            );

            System.out.println("Ответ от сервера:");
            System.out.println(response);

        } catch (IOException e) {
            System.err.println("Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }
}