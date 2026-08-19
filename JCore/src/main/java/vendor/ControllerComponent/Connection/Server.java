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
//import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import vendor.ControllerComponent.Controller;
import vendor.JCoreMeta;

/**
 * От 26.02.26:
 * 
 * Чистая архитектура - один класс, всё в одном файле
 * Простая очередь - BlockingQueue идеально подходит
 * Потоки-воркеры - 4 потока крутятся в бесконечном цикле и берут задачи из очереди
 * Основной поток только принимает подключения и кладет в очередь - не блокируется
 * Метод handleClient остался без изменений - вся логика обработки сохранилась
 * 
 * @author User
 */
public class Server {
    
    //наш контейнер запроса от клиента
    
    /**
    * Полностью разобранный запрос клиента.
    */
   private static class ClientRequest {

       // Например:
       // PersonController/createPersonAction
       String route;

       // Например:
       // ["helloWorld!", "JCore!"]
       String[] params;

       // Например:
       // [
       //     byte[] файл1,
       //     byte[] файл2,
       //     byte[] файл3
       // ]
       byte[][] binaryFiles;
   }
    
    // Пул из 4 потоков для обработки клиентов
    private static final int THREAD_POOL_SIZE = 4;
    private BlockingQueue<Socket> queue = new LinkedBlockingQueue<>(); // очередь со всеми сокетными подключениями клиентов
    
    private int port;
    public Controller controllerPull; //обьект, обрабатывающий маршрутизацию контроллеров
    
    public Server(Controller controllerPull, int port) {
        this.controllerPull = controllerPull;
        this.port = port;
    }
    
    public void startServer() throws IOException, InterruptedException {        
        ServerSocket serverSocket = new ServerSocket(port); //серверный сокет
        
        JCoreMeta.logoRenderer(); //вывод ASCII логотипа фреймворка в консоль
        
        System.out.println("Сервер запущен на порту: " + port);
        
        // Запускаем 4 рабочих потока
        for (int i = 0; i < THREAD_POOL_SIZE; i++) {
            new Thread(() -> { // создаем поток (по циклу - 4 раза)
                while (true) {
                    try {
                        Socket client = queue.take(); // берем из очереди сокет с запросом клиента
                        handleClient(client); // вызываем метод обработки клиентского сокета сервером
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
        
        // Основной поток принимает подключения
        while (true) {
            Socket clientSocket = serverSocket.accept(); // принимаем подключение
            
            // Кладем новый сокет с запросом клиента в общую очередь на обработку одному из 4х потоков
            queue.put(clientSocket);
        }
    }
    
    /**
    * Обработка клиентского подключения в том же потоке
    */
   private void handleClient(Socket clientSocket) {
       try {
           System.out.println("Новое подключение: " + clientSocket.getInetAddress());

           // Читаем запрос непосредственно как набор байт.
           ClientRequest request = readClientRequest(clientSocket.getInputStream());

           if (request == null) {
               System.out.println("Получен пустой запрос");
               clientSocket.close();
               return;
           }

           System.out.println("Получен роут: " + request.route);

           // Передаем роут, текстовые параметры и бинарные файлы контроллеру.
           Object result = controllerPull.startMethodByUrl(
                   request.route,
                   request.params,
                   request.binaryFiles
           );

           // Отправляем результат клиенту.
           PrintWriter out = new PrintWriter(
                   clientSocket.getOutputStream(),
                   true
           );

           if (result != null) {
               out.println(result.toString());
           } else {
               out.println("ERROR: Controller returned null");
           }

           System.out.println("Ответ отправлен клиенту");

           clientSocket.close();
           System.out.println("Соединение закрыто");

       } catch (IOException e) {
           System.out.println(
                   "Ошибка при работе с клиентом: " + e.getMessage()
           );
       }
   }
   
   /**
    * Читает запрос клиента.
    *
    * Текстовая часть запроса имеет привычный формат:
    *
    * PersonController/createPersonAction<endl>helloWorld!<endl>JCore!<endl><BINARY>
    *
    * После <BINARY> начинаются бинарные файлы.
    *
    * Каждый бинарный файл передается следующим образом:
    *
    * 4 байта - размер файла
    * N байт  - содержимое файла
    *
    * Затем идет следующий файл.
    */
   private ClientRequest readClientRequest(InputStream inputStream)
           throws IOException {

       DataInputStream in = new DataInputStream(inputStream);

       // Сначала читаем текстовую часть до маркера <BINARY>.
       ByteArrayOutputStream textBuffer = new ByteArrayOutputStream();

       byte[] binaryMarker = "<BINARY>".getBytes(StandardCharsets.UTF_8);

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
                   // Убираем <BINARY> из текстовой части.
                   byte[] textData = textBuffer.toByteArray();

                   byte[] textWithoutBinaryMarker = new byte[
                           textData.length - binaryMarker.length
                   ];

                   System.arraycopy(
                           textData,
                           0,
                           textWithoutBinaryMarker,
                           0,
                           textWithoutBinaryMarker.length
                   );

                   String text = new String(
                           textWithoutBinaryMarker,
                           StandardCharsets.UTF_8
                   );

                   return createClientRequest(
                           text,
                           in
                   );
               }

           } else {
               markerIndex = 0;
           }
       }

       // Если <BINARY> не было, обрабатываем запрос только как текст.
       String text = new String(
               textBuffer.toByteArray(),
               StandardCharsets.UTF_8
       );

       return createClientRequest(
               text,
               null
       );
   }

   /**
    * Создает объект запроса из текстовой части и бинарных файлов.
    */
   private ClientRequest createClientRequest(
           String text,
           DataInputStream binaryInput
   ) throws IOException {

       ClientRequest request = new ClientRequest();

       // Разбираем обычную текстовую часть.
       String[] parsedData = parseClientQuery(text, "<endl>");

       if (parsedData.length < 1) {
           return null;
       }

       // Первый элемент - роут.
       request.route = parsedData[0];

       // Остальные элементы - обычные текстовые параметры.
       request.params = new String[parsedData.length - 1];

       for (int i = 1; i < parsedData.length; i++) {
           request.params[i - 1] = parsedData[i];
       }

       // Если бинарной части нет - создаем пустой массив.
       if (binaryInput == null) {
           request.binaryFiles = new byte[0][];
           return request;
       }

       // Читаем бинарные файлы.
       List<byte[]> files = new ArrayList<>();

       while (true) {

           /*
            * Читаем размер следующего файла.
            *
            * readInt() читает 4 байта.
            */
           int fileSize;

           try {
               fileSize = binaryInput.readInt();
           } catch (IOException e) {
               break;
           }

           /*
            * Значение 0 можно использовать как признак
            * окончания списка файлов.
            */
           if (fileSize == 0) {
               break;
           }

           if (fileSize < 0) {
               throw new IOException(
                       "Некорректный размер бинарного файла: " + fileSize
               );
           }

           byte[] fileData = new byte[fileSize];

           binaryInput.readFully(fileData);

           files.add(fileData);
       }

       request.binaryFiles = files.toArray(new byte[0][]);

       return request;
   }
   
    /**
     * Версия с обработкой нескольких запросов от одного клиента
     */
//    private void handleClientWithMultipleRequests(Socket clientSocket) {
//        try (
//            Scanner in = new Scanner(clientSocket.getInputStream());
//            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
//        ) {
//            System.out.println("Новое подключение: " + clientSocket.getInetAddress());
//            
//            while(in.hasNextLine()) {
//                String line = in.nextLine();
//                
//                // Проверка на команду завершения
//                if ("exit".equalsIgnoreCase(line.trim()) || "quit".equalsIgnoreCase(line.trim())) {
//                    System.out.println("Клиент запросил отключение");
//                    out.println("Connection closed");
//                    break;
//                }
//                
//                System.out.println("Получен запрос: " + line);
//                
//                Object result = serchAndExecuteActionFromControllerByQueryRoute(line);
//                
//                if (result != null) {
//                    out.println(result.toString());
//                } else {
//                    out.println("ERROR: Controller returned null");
//                }
//            }
//            
//        } catch (IOException e) {
//            System.out.println("Ошибка при работе с клиентом: " + e.getMessage());
//        } finally {
//            try {
//                clientSocket.close();
//                System.out.println("Соединение закрыто");
//            } catch (IOException e) {
//                System.out.println("Ошибка при закрытии сокета: " + e.getMessage());
//            }
//        }
//    }
    
    /**
     * Передаем запрос клиента на выполнение контроллеру, роут которого вписан в запросе.
     * @return Результат выполнения контроллера
     */
//    private Object serchAndExecuteActionFromControllerByQueryRoute(String clientQuery) {
//        if(!controllerPull.declaredControllers.isEmpty()) {
//            String[] parsedData = parseClientQuery(clientQuery, "<endl>");
//            
//            if (parsedData.length < 1) {
//                return "ERROR: Invalid query format";
//            }
//            
//            String patch = parsedData[0];
//            
//            // Создаем массив параметров (все кроме первого элемента - пути)
//            String[] params = new String[parsedData.length - 1];
//            for(int i = 1; i < parsedData.length; i++) {
//                params[i - 1] = parsedData[i];
//            }
//            
//            // Выполняем метод и возвращаем результат
//            Object result = controllerPull.startMethodByUrl(patch, params);
//            
//            // Если результат null, возвращаем информативное сообщение
//            return result != null ? result : "Result is null";
//            
//        } else {
//            String error = "CONTAINER_IS_EMPTY_ERROR: Вы не положили ни один контроллер в контейнер контроллеров - запрос клиента не может быть обработан!";
//            System.out.println(error);
//            return error;
//        }
//    }
    
    /**
     * Парсим запрос пользователя по разделителю.
     * @param query Запрос от клиента для парсинга.
     * @param delimeter Строка-дилиметор, по которому парсить.
     * @return Массив с готовыми данными для обработки.
     */
    private String[] parseClientQuery(String query, String delimeter) {
        return query.split(delimeter);
    }
}