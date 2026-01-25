package vendor.EntityOrm;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author maxim
 */
public abstract class Entity {
    public long id; // первичный ключ сущности
    public List<RelationField> refs = new ArrayList<RelationField>(); // связи между сущностями
    private Statement statement; //обьект для выполнения запросов
    
    public Entity(Statement statement) {
       this.statement = statement;
    }
    
    /**
     * Создание метаданных потомка.
     * @return Обьект с метаданными потомка.
     */
    public EntityInfo initializeChild() throws IllegalArgumentException, IllegalAccessException
    {
        Class<?> childClass = this.getClass();

        //Поля
        Field[] fields = childClass.getDeclaredFields();
  
        //Массив полей (название/тип данных)
        FieldNameWithType[] entityFields = new FieldNameWithType[fields.length];

        int i = 0;
        for(Field field : fields) {
            field.setAccessible(true); // Разрешаем доступ к приватному полю
            
            entityFields[i] = new FieldNameWithType(); 
            entityFields[i].fieldName = field.getName();
            entityFields[i].fieldType = field.getType();
            entityFields[i].fieldValue = field.get(this);
            //System.out.println("\n\n value - " + entityFields[i].fieldValue + " \n\n");
            i++;
        }
    
        return new EntityInfo(childClass, refs, entityFields);
    }

    /**
     * Создание таблиц на основании метаданных потомка.
     * @param entityInfo Метаданные потомка.
     * @return Результат выполнения создания.
     */
    public boolean createTable(EntityInfo entityInfo)
    {
        try {
            // Строим запрос на создание таблиц
            String sql = "CREATE TABLE IF NOT EXISTS " + entityInfo.classType.getSimpleName() +" (" +
                    "\n\t" + "id SERIAL PRIMARY KEY,";
            
            //перебираем поля
            for(int i = 0; i < entityInfo.entityFields.length; i++) {
                sql += "\n\t" + entityInfo.entityFields[i].fieldName + " " + convertTypeToSqlType(entityInfo.entityFields[i], null);
                
                if(i != entityInfo.entityFields.length - 1) {
                    sql += ", ";
                }
            }
            
            if(entityInfo.refs.size() > 0) {
                sql += ", ";
            }
            
            // Перебираем связи
            for(int i = 0; i < entityInfo.refs.size(); i++) {
                //sql += "\n\n\t" + entityInfo.refs.get(i).refClass.getSimpleName() + "Id INTEGER,";
                sql += "\n\t" + "FOREIGN KEY(" + 
                        entityInfo.refs.get(i).refClass.getSimpleName().substring(0, 1).toLowerCase() + entityInfo.refs.get(i).refClass.getSimpleName().substring(1) + "Id" + 
                        ") REFERENCES " + entityInfo.refs.get(i).refClass.getSimpleName() +
                        "(id)";
                
                if(i != entityInfo.refs.size() - 1) {
                    sql += ", ";
                }
            }
            
            sql += "\n);";
            
            System.out.println("QUERY FOR EXECUTION -> \n" + sql);
            //System.out.println(sql);
            
            return statement.execute(sql);
        } catch (SQLException e) {
            System.err.println("SQLError: " + e.getMessage());
            return false; // Возвращаем null при ошибке
        }
    }
    
    /**
     * Преобразовать java тип данных поля в sql тип данных поля (для внутренней реализации запросов).
     * @param field поле для преобразования.
     * @param size размер длинны для данных поля.
     * @return Строка с sql типом данных.
     */
    private String convertTypeToSqlType(FieldNameWithType field, Long size)
    {
        Class<?> type = (Class<?>) field.fieldType;
    
        if (type.equals(String.class)) {
            if (size != null && size > 0) {
                return "VARCHAR(" + size + ")";
            } else if (size != null && size <= 0) {
                return "TEXT";
            } else {
                return "VARCHAR(300)";
            }
        }
        else if (type.equals(Integer.class) || type.equals(int.class)) {
            return "INTEGER";
        }
        else if (type.equals(Long.class) || type.equals(long.class)) {
            return "BIGINT";
        }
        else if (type.equals(Short.class) || type.equals(short.class)) {
            return "SMALLINT";
        }
        else if (type.equals(Byte.class) || type.equals(byte.class)) {
            return "SMALLINT";
        }
        else if (type.equals(Float.class) || type.equals(float.class)) {
            return "REAL";
        }
        else if (type.equals(Double.class) || type.equals(double.class)) {
            return "DOUBLE PRECISION";
        }
        else if (type.equals(Boolean.class) || type.equals(boolean.class)) {
            return "BOOLEAN";
        }
        else if (type.equals(java.util.Date.class) || 
             type.equals(java.sql.Date.class) ||
             type.equals(java.time.LocalDate.class)) {
            return "DATE";
        }
        else if (type.equals(java.sql.Time.class) ||
             type.equals(java.time.LocalTime.class)) {
            return "TIME";
        }
        else if (type.equals(java.sql.Timestamp.class) ||
             type.equals(java.time.LocalDateTime.class)) {
            return "TIMESTAMP";
        }
        else if (type.equals(java.time.Instant.class)) {
            return "TIMESTAMPTZ";
        }
        else if (type.equals(byte[].class)) {
            return "BYTEA";
        }
        else if (type.equals(String[].class)) {
            return "TEXT[]";
        }
        else if (type.equals(Integer[].class)) {
            return "INTEGER[]";
        }
        else if (type.equals(Long[].class)) {
            return "BIGINT[]";
        }
        else {
            return "TEXT";
        }
    }
    
    /**
     * Вставить данные в таблицу из обьекта сущности.
     * @param entityInfo Обьект с метаданными сущности.
     * @return Результат выполнения вставки данных в таблицу БД.
     */
    public boolean insertData(EntityInfo entityInfo)
    {
        try {
            String childClassName = entityInfo.classType.getSimpleName();
        
            String sql = "INSERT INTO " + childClassName + " (id, ";
        
            //добавляем поля в sql запрос
            for(int i = 0; i < entityInfo.entityFields.length; i++) {
                sql += entityInfo.entityFields[i].fieldName;
            
                if(i != entityInfo.entityFields.length - 1) {
                    sql += ", ";
                } else {
                    sql += ") VALUES";
                }
            }
        
            sql += "\n\t" + "(" + id + ", ";
        
            // задаем значения полям в запросе
            for(int i = 0; i < entityInfo.entityFields.length; i++) {
                sql += formatDataValue(entityInfo.entityFields[i]);  
            
                if(i != entityInfo.entityFields.length - 1) {
                    sql += ", ";
                }
            }
        
            sql += ")";
        
            System.out.println("QUERY FOR EXECUTION -> \n" + sql);
            //System.out.println(sql);
        
            return statement.execute(sql);
        } catch (SQLException e) {
            System.err.println("SQLError: " + e.getMessage());
            return false; // Возвращаем null при ошибке
        }
    }
    
    /**
     * Вставить данные в таблицу из обьекта сущности.
     * @param entityInfo Обьект с метаданными сущности.
     * @return Результат выполнения вставки данных в таблицу БД.
     */
    private String formatDataValue(FieldNameWithType field)
    {
        if (field.fieldValue == null) {
            return "NULL";
        }
    
        Object value = field.fieldValue;
    
        if (value instanceof String) {
            return "'" + value.toString().replace("'", "''") + "'";
        }
        else if (value instanceof Number) {
            return value.toString();
        }
        else if (value instanceof Boolean) {
            return ((Boolean) value) ? "TRUE" : "FALSE";
        }
        else if (value instanceof java.util.Date || 
             value instanceof java.sql.Date || 
             value instanceof java.sql.Timestamp ||
             value instanceof java.time.LocalDate ||
             value instanceof java.time.LocalDateTime) {
        return "TIMESTAMP '" + value.toString() + "'";
        }
        else {
            return "'" + value.toString().replace("'", "''") + "'";
        }
    }
    
    /**
     * Получить данные
     * @param dataToGet Что получить.
     * @param conditionForGet Условие для получения.
     * @return Результат запроса получения.
     */
    public ResultSet getData(String dataToGet, String conditionForGet) {
        ResultSet resultSet = null;
        
        try {
            //Выполняем запрос
            String sql = "SELECT " + dataToGet + " FROM " + this.getClass().getSimpleName() + " WHERE " + conditionForGet;
            resultSet = statement.executeQuery(sql);
            
            return resultSet;
        } catch (SQLException e) {
            System.err.println("SQLError: " + e.getMessage());
            
            return resultSet;
        }
    }
    
    /**
    * Просто выводит данные из ResultSet в консоль.
     * @param rs Обьект таблицы ResultSet для вывода в консоль.
    */
    public static void printResultSetSimple(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
    
        // Заголовки
        for (int i = 1; i <= cols; i++) {
            System.out.print(meta.getColumnName(i) + "\t");
        }
        System.out.println();
    
        // Данные
        int count = 0;
        while (rs.next()) {
            count++;
            for (int i = 1; i <= cols; i++) {
                System.out.print(rs.getObject(i) + "\t");
            }
            System.out.println();
        }
    
        System.out.println("Rows: " + count);
    }
    
    /*
    -- Обновить конкретную запись
    UPDATE products 
    SET price = 2999.99, 
        stock = stock - 1
    WHERE id = 15;
    */
    
    /**
     * Обновить данные в базе данных по условию.
     * @param dataToUpdate Данные которые нужно обновить.
     * @param conditionForUpdate Условие для обновления.
     * @return Результат обновления.
     */
    public Integer updateData(String dataToUpdate, String conditionForUpdate)
    {
        Integer rowsAffected = null;
        
        try {
            String sql = "UPDATE " + this.getClass().getSimpleName() + "\n" +
                    "SET " + dataToUpdate + "\n" + 
                    "WHERE " + conditionForUpdate;
            
            rowsAffected = statement.executeUpdate(sql);
            
        } catch (SQLException e) {
            System.err.println("SQLError: " + e.getMessage());
            
            return rowsAffected;
        }
        
        return rowsAffected;
    }
    
    /*
    -- Удалить одну запись
    DELETE FROM users WHERE id = 1;
    */
    
    /**
     * Удалить данные в базе данных по условию.
     * @param conditionForUpdate Условие для удаления.
     * @return Результат удаления.
     */
    public Integer deleteData(String conditionForDelete)
    {
        Integer rowsAffected = null;
        
        try {
            String sql = "DELETE FROM " + this.getClass().getSimpleName() + "\n" +
                    "WHERE " + conditionForDelete;
            
            rowsAffected = statement.executeUpdate(sql);
            
        } catch (SQLException e) {
            System.err.println("SQLError: " + e.getMessage());
            
            return rowsAffected;
        }
        
        return rowsAffected;
    }
}