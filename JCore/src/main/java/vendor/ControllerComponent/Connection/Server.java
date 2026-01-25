package vendor.ControllerComponent.Connection;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import vendor.ControllerComponent.Controller;
import vendor.JCoreMeta;

/**
 *
 * @author User
 */
public class Server {
    private int port;
    public Controller controllerPull; //обьект, обрабатывающий маршрутизацию контроллеров
    
    public Server(Controller controllerPull, int port) {
        this.controllerPull = controllerPull;
        this.port = port;
    }
    
    public void startServer() throws IOException {        
        ServerSocket serverSocket = new ServerSocket(port); //серверный сокет
        
        JCoreMeta.logoRenderer(); //вывод ASCII логотипа фреймворка в консоль
        
        System.out.println("Сервер запущен на порту: " + port);
        
        // Бесконечный цикл в одном потоке
        while (true) {
            Socket clientSocket = serverSocket.accept(); // принимаем подключение
            
            // Обрабатываем клиента в том же потоке
            handleClient(clientSocket);
        }
    }
    
    /**
     * Обработка клиентского подключения в том же потоке
     */
    private void handleClient(Socket clientSocket) {
        try {
            Scanner in = new Scanner(clientSocket.getInputStream());
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            
            System.out.println("Новое подключение: " + clientSocket.getInetAddress());
            
            // Обрабатываем только один запрос от клиента
            if (in.hasNextLine()) {
                String line = in.nextLine(); // Читаем строку
                
                System.out.println("Получен запрос: " + line);
                
                // Обрабатываем запрос и получаем результат
                Object result = serchAndExecuteActionFromControllerByQueryRoute(line);
                
                // Отправляем результат клиенту
                if (result != null) {
                    out.println(result.toString());
                } else {
                    out.println("ERROR: Controller returned null");
                }
                
                System.out.println("Ответ отправлен клиенту");
            }
            
            // Закрываем соединение после обработки одного запроса
            clientSocket.close();
            System.out.println("Соединение закрыто");
            
        } catch (IOException e) {
            System.out.println("Ошибка при работе с клиентом: " + e.getMessage());
        }
    }
    
    /**
     * Версия с обработкой нескольких запросов от одного клиента
     */
    private void handleClientWithMultipleRequests(Socket clientSocket) {
        try (
            Scanner in = new Scanner(clientSocket.getInputStream());
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            System.out.println("Новое подключение: " + clientSocket.getInetAddress());
            
            while(in.hasNextLine()) {
                String line = in.nextLine();
                
                // Проверка на команду завершения
                if ("exit".equalsIgnoreCase(line.trim()) || "quit".equalsIgnoreCase(line.trim())) {
                    System.out.println("Клиент запросил отключение");
                    out.println("Connection closed");
                    break;
                }
                
                System.out.println("Получен запрос: " + line);
                
                Object result = serchAndExecuteActionFromControllerByQueryRoute(line);
                
                if (result != null) {
                    out.println(result.toString());
                } else {
                    out.println("ERROR: Controller returned null");
                }
            }
            
        } catch (IOException e) {
            System.out.println("Ошибка при работе с клиентом: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
                System.out.println("Соединение закрыто");
            } catch (IOException e) {
                System.out.println("Ошибка при закрытии сокета: " + e.getMessage());
            }
        }
    }
    
    /**
     * Передаем запрос клиента на выполнение контроллеру, роут которого вписан в запросе.
     * @return Результат выполнения контроллера
     */
    private Object serchAndExecuteActionFromControllerByQueryRoute(String clientQuery) {
        if(!controllerPull.declaredControllers.isEmpty()) {
            String[] parsedData = parseClientQuery(clientQuery, "<endl>");
            
            if (parsedData.length < 1) {
                return "ERROR: Invalid query format";
            }
            
            String patch = parsedData[0];
            
            // Создаем массив параметров (все кроме первого элемента - пути)
            String[] params = new String[parsedData.length - 1];
            for(int i = 1; i < parsedData.length; i++) {
                params[i - 1] = parsedData[i];
            }
            
            // Выполняем метод и возвращаем результат
            Object result = controllerPull.startMethodByUrl(patch, params);
            
            // Если результат null, возвращаем информативное сообщение
            return result != null ? result : "Result is null";
            
        } else {
            String error = "CONTAINER_IS_EMPTY_ERROR: Вы не положили ни один контроллер в контейнер контроллеров - запрос клиента не может быть обработан!";
            System.out.println(error);
            return error;
        }
    }
    
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