package vendor.EntityOrm;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Класс для сериализации данных из ResultSet в List для комфортной работы с данными.
 * @author User
 */
public class DataSerializer {
    
    /**
    * Сериализует данные из формата ResultSet в формат List с использованием Map для хранения данных с ключами
    * @param resultSet
    * @return List<Map<String, Object>> где ключ - имя колонки, значение - данные ячейки
    * @throws SQLException 
    */
   public static List<Map<String, Object>> serializeFromResultDataToList(ResultSet resultSet) throws SQLException {
       List<Map<String, Object>> list = new ArrayList<>();

       try {
           // Получаем метаданные результата
           ResultSetMetaData metaData = resultSet.getMetaData();
           int columnCount = metaData.getColumnCount();

           while (resultSet.next()) {
               // Создаем Map для текущей строки
               Map<String, Object> row = new HashMap<>();

               // Заполняем Map данными из текущей строки
               for (int i = 1; i <= columnCount; i++) {
                   String columnName = metaData.getColumnName(i);
                   Object value = resultSet.getObject(i);
                   row.put(columnName, value);
               }

               // Добавляем строку в список
               list.add(row);
           }
       } finally {
           // Закрываем ResultSet
           try {
               if (resultSet != null) {
                   resultSet.close();
               }
           } catch (SQLException e) {
               System.out.println("ОШИБКА СЕРИАЛИЗАЦИИ: " + e.getMessage());
               e.printStackTrace();
           }
       }

       return list;
   }

   /**
    * Метод для вывода сериализованных данных листа в консоль.
    * @param list 
    */
   public static void printList(List<Map<String, Object>> list) {
       if (list == null || list.isEmpty()) {
           System.out.println("Список пуст.");
           return;
       }

       System.out.println("--- Содержимое листа ---");
       int rowNumber = 1;
       for (Map<String, Object> row : list) {
           System.out.println("Строка " + rowNumber + ":");
           for (Map.Entry<String, Object> entry : row.entrySet()) {
               System.out.println("  " + entry.getKey() + ": " + entry.getValue());
           }
           System.out.println();
           rowNumber++;
       }
       System.out.println("--- Всего строк: " + list.size() + " ---");
   }

   /**
    * Формирует строку в формате JSON из списка данных
    * @param list список данных, где каждый элемент - Map с ключами (имена колонок) и значениями
    * @return строка в формате JSON
    */
   public static String convertToJson(List<Map<String, Object>> list) {
       if (list == null || list.isEmpty()) {
           return "[]";
       }

       StringBuilder jsonBuilder = new StringBuilder();
       jsonBuilder.append("[\n");

       for (int i = 0; i < list.size(); i++) {
           Map<String, Object> row = list.get(i);
           jsonBuilder.append("  {\n");

           int entryIndex = 0;
           for (Map.Entry<String, Object> entry : row.entrySet()) {
               jsonBuilder.append("    \"")
                         .append(entry.getKey())
                         .append("\": ");

               Object value = entry.getValue();
               if (value == null) {
                   jsonBuilder.append("null");
               } else if (value instanceof String) {
                   jsonBuilder.append("\"")
                             .append(escapeJsonString(value.toString()))
                             .append("\"");
               } else if (value instanceof Number || value instanceof Boolean) {
                   jsonBuilder.append(value);
               } else {
                   // Для других типов преобразуем в строку
                   jsonBuilder.append("\"")
                             .append(escapeJsonString(value.toString()))
                             .append("\"");
               }

               if (++entryIndex < row.size()) {
                   jsonBuilder.append(",");
               }
               jsonBuilder.append("\n");
           }

           jsonBuilder.append("  }");
           if (i < list.size() - 1) {
               jsonBuilder.append(",");
           }
           jsonBuilder.append("\n");
       }

       jsonBuilder.append("]");
       return jsonBuilder.toString();
   }

   /**
    * Экранирует специальные символы в строке для JSON
    * @param input входная строка
    * @return экранированная строка
    */
   private static String escapeJsonString(String input) {
       if (input == null) {
           return "";
       }

       StringBuilder sb = new StringBuilder();
       for (char c : input.toCharArray()) {
           switch (c) {
               case '\"':
                   sb.append("\\\"");
                   break;
               case '\\':
                   sb.append("\\\\");
                   break;
               case '/':
                   sb.append("\\/");
                   break;
               case '\b':
                   sb.append("\\b");
                   break;
               case '\f':
                   sb.append("\\f");
                   break;
               case '\n':
                   sb.append("\\n");
                   break;
               case '\r':
                   sb.append("\\r");
                   break;
               case '\t':
                   sb.append("\\t");
                   break;
               default:
                   sb.append(c);
           }
       }
       return sb.toString();
   }

   /**
    * Дополнительный метод для красивого вывода JSON в консоль
    * @param list список данных для конвертации в JSON
    */
   public static void printJson(List<Map<String, Object>> list) {
       System.out.println("--- JSON представление ---");
       System.out.println(convertToJson(list));
   }
}
