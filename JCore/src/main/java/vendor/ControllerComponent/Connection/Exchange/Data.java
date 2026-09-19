package vendor.ControllerComponent.Connection.Exchange;

import java.io.File;

/**
 * Пакет данных, пересылаемый между клиентом и сервером
 * 
 * @author User
 */
public class Data {
    private String[] params;
    private File[] binaryFiles;

    public String[] getParams() {
        return params;
    }

    public void setParams(String[] params) {
        this.params = params;
    }

    public File[] getBinaryFiles() {
        return binaryFiles;
    }

    public void setBinaryFiles(File[] binaryFiles) {
        this.binaryFiles = binaryFiles;
    }
    
    public StringBuilder showParams()
    {
        StringBuilder result = new StringBuilder();
        
        for (String param : params) {

            result.append("param is -> ")
                  .append(param)
                  .append("\r\n");

            System.out.println("param is -> " + param);
        }
        
        return result;
    }
    
    public StringBuilder showFiles()
    {
        StringBuilder result = new StringBuilder();
        
        // ------------------------------------------------------------------
        // Обработка бинарных файлов.
        //
        // Каждый элемент binaryFiles — это File, то есть путь к файлу,
        // который сервер уже сохранил на диск во время чтения запроса.
        //
        // Контроллеру НЕ нужно ничего склеивать, переименовывать или
        // копировать: файл уже лежит по своему пути. Всё, что здесь
        // делается — это работа с метаданными (размер, путь) и,
        // при необходимости, дальнейшая обработка.
        //
        // В этом примере мы просто выводим информацию о каждом файле
        // и добавляем строку в ответ клиенту.
        // ------------------------------------------------------------------
        for (int i = 0; i < binaryFiles.length; i++) {

            File file = binaryFiles[i];

            // Если по какой-то причине файла нет — пропускаем.
            // (На практике сервер гарантирует существование, но проверка
            //  не помешает: файл мог быть удалён параллельно.)
            if (file == null || !file.exists()) {
                //System.out.println("Файл #" + i + " отсутствует: " + file);
                result.append("file #").append(i)
                      .append(" -> MISSING\r\n");
                continue;
            }

            // Размер файла — берётся из метаданных файловой системы,
            // без чтения содержимого. Для файла в 100 ГБ это O(1).
            long size = file.length();

            System.out.println(
                    "Получен файл #" + i +
                    ", путь: " + file.getAbsolutePath() +
                    ", размер: " + size + " байт"
            );

            result.append("file #").append(i)
                  .append(" saved as -> ").append(file.getAbsolutePath())
                  .append(", size -> ").append(size)
                  .append(" bytes\r\n");
        }
        
        return result;
    }
}
