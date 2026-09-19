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
 * Ключевые особенности:
 *
 *  1. Поддержка файлов любого размера, в том числе > 2ГБ.
 *     Файл читается и отправляется ПО КУСКАМ, каждый кусок не больше
 *     {@link #CHUNK_SIZE}. Клиент НЕ держит весь файл в памяти —
 *     только один кусок за раз.
 *
 *  2. Формат каждого куска на проводе:
 *       [1 байт: флаг продолжения (1 = есть следующий кусок, 0 = последний)]
 *       [4 байта: длина этого куска (big-endian int)]
 *       [N байт: данные куска]
 *
 *     Последний кусок каждого файла помечается флагом 0.
 *     Все промежуточные куски — флагом 1.
 *
 *  3. Маркер окончания списка файлов:
 *     пустой кусок с флагом 0 (chunkSize == 0, continueFlag == 0).
 *     Приходит ПОСЛЕ всех файлов, а не между ними.
 *
 *  4. Размер куска ({@link #CHUNK_SIZE}) должен быть согласован
 *     с сервером (см. Server.MAX_CHUNK_SIZE). Если клиент пришлёт
 *     кусок больше — сервер откажется его принимать.
 *
 * Использование:
 *   FileClient client = new FileClient("127.0.0.1", 8082);
 *   client.sendFile("PersonController", "createPersonAction",
 *                   new String[]{"param1", "param2"},
 *                   new File("test.txt"));
 *
 * @author User
 */
public class FileClientPusher {
    
    /**
     * Размер одного куска файла.
     *
     * Должен совпадать с Server.MAX_CHUNK_SIZE. 64 МБ — разумный
     * компромисс: достаточно крупный, чтобы накладные расходы протокола
     * (5 байт заголовка на кусок) были незаметны, и достаточно мелкий,
     * чтобы пик памяти на клиенте и сервере оставался умеренным.
     *
     * НЕ увеличивай до Integer.MAX_VALUE: это приведёт к выделению
     * буфера в 2 ГБ на каждой стороне и, скорее всего, к OutOfMemoryError.
     */
    private static final int CHUNK_SIZE = 64 * 1024 * 1024;   // 64 МБ

    private final String host;
    private final int port;

    public FileClientPusher(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Отправляет файл(ы) на указанный роут контроллера.
     *
     * Каждый файл автоматически режется на куски по {@link #CHUNK_SIZE}.
     * Клиент не загружает файл целиком в память — читает и отправляет
     * по кускам.
     *
     * @param controllerName имя контроллера (например, "PersonController").
     * @param methodName     имя метода (например, "createPersonAction").
     * @param params         текстовые параметры запроса.
     * @param files          файлы для отправки (любого размера).
     * @return ответ от сервера.
     * @throws IOException при ошибке сети или чтения файла.
     */
    public String sendFile(
            String controllerName,
            String methodName,
            String[] params,
            File... files
    ) throws IOException {

        try (Socket socket = new Socket(host, port)) {

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            InputStream in = socket.getInputStream();

            // ------------------------------------------------------------------
            // 1. Формируем и отправляем текстовую часть запроса.
            //    Формат: ControllerName/methodName<endl>param1<endl>...<BINARY>
            // ------------------------------------------------------------------
            StringBuilder textPart = new StringBuilder();
            textPart.append(controllerName).append("/").append(methodName);

            for (String param : params) {
                textPart.append("<endl>").append(param);
            }

            out.write(textPart.toString().getBytes(StandardCharsets.UTF_8));
            out.write("<BINARY>".getBytes(StandardCharsets.UTF_8));

            // ------------------------------------------------------------------
            // 2. Отправляем каждый файл по кускам.
            // ------------------------------------------------------------------
            for (File file : files) {
                sendFile(out, file);
            }

            // ------------------------------------------------------------------
            // 3. Маркер окончания списка файлов:
            //    пустой кусок с флагом 0.
            // ------------------------------------------------------------------
            out.writeByte(0);   // continueFlag = 0
            out.writeInt(0);    // chunkSize = 0
            out.flush();

            // ------------------------------------------------------------------
            // 4. Читаем ответ от сервера (текстом до EOF).
            // ------------------------------------------------------------------
            StringBuilder response = new StringBuilder();
            int b;
            while ((b = in.read()) != -1) {
                response.append((char) b);
            }

            return response.toString();

           // return readAnswer(socket);
        }
    }

    /**
     * Отправляет один файл, автоматически нарезая его на куски
     * по {@link #CHUNK_SIZE} байт.
     *
     * Алгоритм:
     *   1. Открываем FileInputStream.
     *   2. Читаем очередной кусок размером до CHUNK_SIZE.
     *   3. Если прочитали меньше CHUNK_SIZE — это последний кусок
     *      (continueFlag = 0). Иначе — есть продолжение (continueFlag = 1).
     *   4. Пишем в сокет: [флаг][длина][данные].
     *   5. Повторяем, пока не отправим все куски.
     *
     * В памяти в каждый момент — только один буфер размером CHUNK_SIZE.
     * Он выделяется один раз и переиспользуется для всех кусков файла.
     *
     * Пустой файл (размер 0) НЕ отправляется: по протоколу пустой кусок
     * без продолжения — это маркер конца списка файлов, а не пустой файл.
     * Если файл пустой, он будет просто пропущен.
     *
     * @param out  поток вывода в сокет.
     * @param file файл для отправки.
     * @throws IOException при ошибке чтения файла или записи в сокет.
     */
    private void sendFile(DataOutputStream out, File file) throws IOException {

        long fileSize = file.length();

        // Пустой файл не передать: [0][0] — это маркер конца списка файлов,
        // а не «пустой файл». Пропускаем.
        if (fileSize == 0) {
            System.out.println(
                    "Пропускаем пустой файл: " + file.getName()
            );
            return;
        }

        // Оценка количества кусков — только для лога.
        long estimatedChunks = (fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE;

        System.out.println(
                "Отправка файла: " + file.getName() +
                ", размер: " + fileSize + " байт" +
                ", кусков (оценка): " + estimatedChunks
        );

        // Буфер ровно под один кусок. Выделяется один раз,
        // переиспользуется для всех кусков файла.
        byte[] buffer = new byte[CHUNK_SIZE];

        try (java.io.PushbackInputStream pis =
             new java.io.PushbackInputStream(new FileInputStream(file), 1)) {

            while (true) {
                int bytesRead = pis.readNBytes(buffer, 0, CHUNK_SIZE);
                if (bytesRead == 0) break;

                boolean hasNext;
                if (bytesRead < CHUNK_SIZE) {
                    hasNext = false;
                } else {
                    int probe = pis.read();
                    if (probe == -1) {
                        hasNext = false;
                    } else {
                        hasNext = true;
                        pis.unread(probe);   // ← вот ключевая строка
                    }
                }

                out.writeByte(hasNext ? 1 : 0);
                out.writeInt(bytesRead);
                out.write(buffer, 0, bytesRead);

                if (!hasNext) break;
            }

            out.flush();
        }
    }
    
    // читает ответ от сервера
//    private String readAnswer(Socket socket) throws IOException {
//        InputStream in = socket.getInputStream();
//        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
//        byte[] marker = "<BINARY>".getBytes(StandardCharsets.UTF_8);
//        int i = 0, b;
//        while ((b = in.read()) != -1) {
//            buf.write(b);
//            if (b == marker[i] && ++i == marker.length) break;
//            else if (b != marker[i]) i = 0;
//        }
//        byte[] all = buf.toByteArray();
//        return new String(all, 0, Math.max(0, all.length - marker.length), StandardCharsets.UTF_8);
//    }
    
    public static void main(String[] args) {

        // Адрес и порт сервера JCore.
        String host = "127.0.0.1";
        int port = 8082;

        // Контроллер и метод, которые будут обрабатывать запрос.
        String controllerName = "PersonController";
        String methodName = "createPersonAction";

        // Текстовые параметры запроса.
        String[] params = new String[]{"param1", "param2"};

        
        //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // Файлы для отправки. Замени пути на свои: это пример того, как можно
        //передавать тяжелые файлы на backend часть
        File[] files = new File[]{
            new File(".." + File.separator + "test_photo.jpg"), // тестовый пример передачи фотографии (находится на директории выше проекта)
            //new File(".." + File.separator + "test_video.mp4"),
            //new File(".." + File.separator + "heavy_test_video.mp4")
        };

        FileClientPusher client = new FileClientPusher(host, port);

        try {
            String response = client.sendFile(
                    controllerName,
                    methodName,
                    params,
                    files
            );
            System.out.println("Ответ сервера: " + response);
        } catch (IOException e) {
            System.err.println("Ошибка при отправке файлов: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

// PersonController/createPersonAction<endl>param1975<BINARY>
//[0][0][0][0][0]