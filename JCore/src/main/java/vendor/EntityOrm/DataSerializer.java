package vendor.EntityOrm;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Класс для сериализации данных из ResultSet в List для комфортной работы с данными.
 * @author User
 */
public class DataSerializer {
    
    /**
     * Сериализует данные из формата ResultSet в формат List
     * @param resultSet
     * @return
     * @throws SQLException 
     */
    public static List<Object[]> serializeFromResultDataToList(ResultSet resultSet) throws SQLException {
        List<Object[]> list = new ArrayList<>();

        try {
            // Узнаем, сколько всего колонок в результате
            int columnCount = resultSet.getMetaData().getColumnCount();

            while (resultSet.next()) {
                // Создаем массив размером с количество колонок
                Object[] row = new Object[columnCount];

                // Заполняем массив данными из текущей строки
                for (int i = 0; i < columnCount; i++) {
                    row[i] = resultSet.getObject(i + 1); // В JDBC колонки с 1
                }

                // Кладем "строку" в наш список
                list.add(row);
            }
        } finally {
            // Вот тут закрываем вручную
            try {
                if (resultSet != null) {
                    resultSet.close();
                    // Если нужно закрыть и Statement, то: rs.getStatement().close();
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
    public static void printList(List<Object[]> list) {
        if (list == null || list.isEmpty()) {
            System.out.println("Список пуст.");
            return;
        }

        System.out.println("--- Содержимое листа ---");
        for (Object[] row : list) {
            // Стандартный метод Java для красивого вывода массива
            System.out.println(Arrays.toString(row));
        }
        System.out.println("--- Всего строк: " + list.size() + " ---");
    }
}
