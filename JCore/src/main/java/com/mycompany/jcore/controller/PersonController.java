package com.mycompany.jcore.controller;

import java.io.File;
import java.io.IOException;
import java.sql.Statement;
import vendor.ControllerComponent.Connection.Exchange.ClientRequest;
import vendor.ControllerComponent.Connection.Exchange.Data;
import vendor.ControllerComponent.Connection.Exchange.ServerResponse;
import vendor.Security.Security;

/**
 * Пример контроллера с выводом переданных параметров клиенту
 * и обработкой полученных бинарных файлов.
 *
 * Изменения по сравнению с предыдущей версией:
 *
 *  1. Тип бинарного параметра изменён с {@code FileChunk[]} на {@code File[]}.
 *
 *     Раньше сервер собирал куски файла в цепочку FileChunk в памяти
 *     и передавал её контроллеру целиком. Теперь сервер пишет куски
 *     на диск по мере поступления (стримингово) и передаёт контроллеру
 *     массив {@link File} — путей к уже сохранённым файлам.
 *
 *     Каждый {@code File} — это ссылка на файл в файловой системе,
 *     ~100 байт в heap. Данных в нём нет. Размер самого файла на это
 *     не влияет: хоть 10 МБ, хоть 100 ГБ.
 *
 *  2. Контроллер больше НЕ вызывает {@code FileChunk.mergeAll} и НЕ пишет
 *     байты вручную. Файлы уже сохранены на диске сервером. Контроллеру
 *     остаётся только работать с путями: узнать размер, переместить,
 *     обработать, удалить.
 *
 *  3. Убраны вызовы {@code totalSize()} и {@code chunkCount()} — этих
 *     методов больше нет, потому что нет и цепочки кусков в памяти.
 *     Размер файла берётся через {@link File#length()}.
 *
 * @author User
 */
public class PersonController extends Security {

    public PersonController(Statement statement) {
        super(statement);
    }

    /*
     * Пример запроса через telnet (текстовая часть без бинарных файлов):
     *
     *   telnet 127.0.0.1 8082
     *   PersonController/createPersonAction<endl>helloWorld!<endl>JCore!<endl>ivanov<security>pass<endl>
     */
    public ServerResponse createPersonAction(
            ClientRequest request
    ) throws IOException {

        // 1) готовим строку с ответом
        StringBuilder result = new StringBuilder();
        
        // выводим результат параметров
        result.append(request.getData().showParams());
        // выводим результат файлов
        result.append(request.getData().showFiles());

        // 2) заполняем ответ от сервера и возвращаем его
        ServerResponse response = new ServerResponse();
        
        Data dataToResponse = new Data();
        dataToResponse.setParams(request.getData().getParams());
        
        response.setData(dataToResponse);
        
        // возвращаем результат клиенту
        return response;
    }
}