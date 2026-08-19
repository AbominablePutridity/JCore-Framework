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
 * Использование:
 * FileClient client = new FileClient("127.0.0.1", 8082);
 * client.sendFile("PersonController", "createPersonAction", 
 *                  new String[]{"param1", "param2"}, 
 *                  new File("test.txt"));
 * 
 * @author User
 */
public class FileClient {
    
    private String host;
    private int port;
    
    public FileClient(String host, int port) {
        this.host = host;
        this.port = port;
    }
    
    /**
     * Отправляет файл(ы) на указанный роут контроллера.
     * 
     * @param controllerName Имя контроллера (например, "PersonController")
     * @param methodName Имя метода (например, "createPersonAction")
     * @param params Текстовые параметры запроса
     * @param files Файлы для отправки
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
            
            // Формируем текстовую часть запроса
            StringBuilder textPart = new StringBuilder();
            textPart.append(controllerName);
            textPart.append("/");
            textPart.append(methodName);
            
            // Добавляем текстовые параметры
            for (String param : params) {
                textPart.append("<endl>");
                textPart.append(param);
            }
            
            // Отправляем текстовую часть + маркер бинарных данных
            byte[] textBytes = textPart.toString().getBytes(
                StandardCharsets.UTF_8
            );
            
            out.write(textBytes);
            out.write("<BINARY>".getBytes(StandardCharsets.UTF_8));
            
            // Отправляем каждый файл
            for (File file : files) {
                sendFile(out, file);
            }
            
            // Отправляем маркер окончания файлов (размер 0)
            out.writeInt(0);
            out.flush();
            
            // Читаем ответ от сервера
            StringBuilder response = new StringBuilder();
            int buffer;
            
            while ((buffer = in.read()) != -1) {
                response.append((char) buffer);
            }
            
            return response.toString();
        }
    }
    
    /**
     * Отправляет один файл в формате: 4 байта размера + содержимое
     */
    private void sendFile(DataOutputStream out, File file) 
            throws IOException {
        
        byte[] fileData = readFileBytes(file);
        
        // Отправляем размер файла (4 байта)
        out.writeInt(fileData.length);
        
        // Отправляем содержимое файла
        out.write(fileData);
    }
    
    /**
     * Читает файл в массив байтов
     */
    private byte[] readFileBytes(File file) throws IOException {
        
        byte[] data = new byte[(int) file.length()];
        
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(data);
        }
        
        return data;
    }
    
    /**
     * Простой пример использования клиента
     */
    public static void main(String[] args) {
        
        FileClient client = new FileClient("127.0.0.1", 8082);
        
        try {
            File photo = new File("../test_photo.jpg");
            
            if (!photo.exists()) {
                System.err.println("Файл не найден: " + photo.getAbsolutePath());
                return;
            }
            
            System.out.println(
                "Отправляем фото (" + photo.length() + " байт)..."
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
