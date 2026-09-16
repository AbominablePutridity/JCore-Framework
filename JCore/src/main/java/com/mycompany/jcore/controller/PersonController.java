package com.mycompany.jcore.controller;

import java.io.File;
import java.io.IOException;
import java.sql.Statement;
import vendor.ControllerComponent.Connection.FileChunk;
import vendor.Security.Security;

/**
 * Пример контроллера с выводом переданных параметров клиенту
 * и сохранением полученных бинарных файлов на диск.
 *
 * Изменения по сравнению с предыдущей версией:
 *
 *  1. Тип бинарного параметра изменён с {@code byte[][]} на {@code FileChunk[]}.
 *     Это связано с тем, что один файл больше не передаётся как один byte[]
 *     (byte[] в Java ограничен ~2ГБ). Вместо этого каждый "файл" в запросе —
 *     это цепочка кусков {@link FileChunk}.
 *
 *  2. Контроллер больше НЕ пишет байты в FileOutputStream вручную.
 *     Для каждого файла вызывается {@link FileChunk#mergeAll(FileChunk, String)},
 *     который сам последовательно пишет все куски в файл на диске, не собирая
 *     весь файл в памяти. Это позволяет корректно сохранять файлы >2ГБ.
 *
 *  3. Имя выходного файла формируется динамически (photo_0.jpg, photo_1.jpg, ...),
 *     чтобы несколько файлов в одном запросе не перезаписывали друг друга.
 *
 * @author User
 */
public class PersonController extends Security {

    public PersonController(Statement statement) {
        super(statement);
    }

    /* telnet 127.0.0.1 8082
    запрос (через коммандную строку): PersonController/createPersonAction<endl>helloWorld!<endl>JCore!<endl>ivanov<security>pass<endl>
    */
    public String createPersonAction(
            String[] params,
            FileChunk[] binaryFiles
    ) throws IOException {

        String result = "";

        // ------------------------------------------------------------------
        // 1. Вывод текстовых параметров (без изменений).
        // ------------------------------------------------------------------
        for (String param : params) {

            result +=
                    "param is -> " +
                    param +
                    "\r\n";

            System.out.println(
                    "param is -> " +
                    param
            );
        }

        // ------------------------------------------------------------------
        // 2. Обработка бинарных файлов.
        //
        // Каждый элемент binaryFiles — это не "массив байт", а голова цепочки
        // FileChunk. Цепочка может состоять из одного куска (если файл <= ~2ГБ)
        // или из нескольких кусков (если файл > ~2ГБ).
        //
        // Мы не знаем и не должны знать, сколько кусков в цепочке — этим
        // управляет сам FileChunk. Наша задача — попросить его слить цепочку
        // в файл через mergeAll и получить готовый File.
        // ------------------------------------------------------------------
        for (int i = 0; i < binaryFiles.length; i++) {

            FileChunk fileHead = binaryFiles[i];

            // Общий размер файла = сумма размеров всех кусков цепочки.
            // totalSize() проходит по цепочке и складывает длины.
            long totalSize = fileHead.totalSize();

            // Количество кусков — полезно для логов и диагностики.
            int chunkCount = fileHead.chunkCount();

            System.out.println(
                    "Получен файл #" + i +
                    ", кусков: " + chunkCount +
                    ", суммарный размер: " + totalSize + " байт"
            );

            // ------------------------------------------------------------------
            // 3. Слияние кусков в цельный файл на диске.
            //
            // mergeAll() пишет куски последовательно в FileOutputStream,
            // поэтому в памяти одновременно находится только один кусок.
            // Это корректно работает и для файлов >2ГБ, и для обычных мелких файлов.
            //
            // Расширение передаём без точки — метод сам нормализует его в ".jpg".
            // Имя файла делаем уникальным (photo_0.jpg, photo_1.jpg, ...),
            // иначе несколько файлов в одном запросе затрут друг друга.
            // ------------------------------------------------------------------
            File mergedFile = FileChunk.mergeAll(fileHead, "jpg");

            // createTempFile создаёт файл со случайным именем в temp-директории.
            // Если хотим конкретное имя в конкретной папке — переименуем.
            File targetFile = new File("photo_" + i + ".jpg");

            if (targetFile.exists()) {
                // На всякий случай удаляем старый файл, чтобы не было каши.
                targetFile.delete();
            }

            if (!mergedFile.renameTo(targetFile)) {
                // renameTo может не сработать между разными файловыми системами
                // (например, temp и рабочая директория на разных дисках).
                // В этом случае просто скопируем содержимое вручную.
                try (java.io.FileInputStream fis = new java.io.FileInputStream(mergedFile);
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(targetFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = fis.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                    }
                }
                mergedFile.delete();
            }

            result +=
                    "file #" + i +
                    " chunks -> " + chunkCount +
                    ", size -> " + totalSize +
                    " bytes, saved as -> " + targetFile.getAbsolutePath() +
                    "\r\n";
        }

        return result;
    }
}