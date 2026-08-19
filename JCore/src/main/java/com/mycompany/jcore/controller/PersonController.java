package com.mycompany.jcore.controller;

import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Statement;
import vendor.Security.Security;

/**
 * Пример контроллера с выводом переданных параметров клиенту.
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
            byte[][] binaryFiles
    ) throws IOException {

        String result = "";

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

        for (int i = 0; i < binaryFiles.length; i++) {

            byte[] file = binaryFiles[i];

            System.out.println(
                    "Получен файл #" +
                    i +
                    ", размер: " +
                    file.length +
                    " байт"
            );
            
            //конвертируем полученные байты обратно в картинку
            try (FileOutputStream output =
                    new FileOutputStream("photo.jpg")) {

                output.write(file);
            }

            result +=
                    "file #" +
                    i +
                    " size -> " +
                    file.length +
                    "\r\n";
        }

        return result;
    }
}
