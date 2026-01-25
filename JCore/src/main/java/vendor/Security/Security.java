package vendor.Security;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 *
 * @author User
 */
public class Security {
    private Statement statement; //обьект для выполнения запросов
    
    public Security(Statement statement) {
       this.statement = statement;
    }
    
     /**
     * Метод для проверки роли пользователя по логину и паролю
     * 
     * @param tableName имя таблицы в базе данных
     * @param loginField имя поля с логином
     * @param loginValue значение логина
     * @param passwordField имя поля с паролем
     * @param passwordValue значение пароля
     * @param roleField имя поля с ролью
     * @param roleForChecking роль для проверки
     * @param clientParams параметры из запроса клиента (для извлечения от туда логина и пароля)
     * @return если роль пользователя совпадает с текущей вписанной ролью(ROLE), то true - иначе false
     */
    public boolean checkRole(
            String tableName,
            String loginField,
            String passwordField,
            String roleField,
            String roleForChecking,
            String[] clientParams
    )
    {
        String[] clientLoginAndPasswordByQuery = extractLoginAndPasswordFromClientQuery(clientParams);
        
        // Исправленный SQL-запрос с правильным синтаксисом
        String sqlQuery = "SELECT " + roleField + " FROM " + tableName + 
                         " WHERE " + loginField + " = '" + clientLoginAndPasswordByQuery[0] + 
                         "' AND " + passwordField + " = '" + clientLoginAndPasswordByQuery[1] + "'";
        
        System.out.println("1090->() "+sqlQuery);
        
        try {
            // Выполняем запрос
            ResultSet resultSet = statement.executeQuery(sqlQuery);
            
            // Если есть результат, берем роль и сравниваем ее с той ролью, которая нам нужна
            if (resultSet.next()) {
                if(resultSet.getString(roleField).equals(roleForChecking)) {
                    return true;
                }
            }
            
            // Закрываем ResultSet
            resultSet.close();
            
        } catch (SQLException e) {
            System.err.println("Ошибка при выполнении SQL-запроса: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Если пользователь не найден или произошла ошибка
        return false;
    }
    
    public String returnException() {
        return "ACCESS_DENIED: ОШИБКА! Данные логина и пароля не верны, либо ваша роль не предусматривает получение данных по данному роуту!";
    }
    
    private String[] extractLoginAndPasswordFromClientQuery(String[] clientParams)
    {
        String[] loginAndPass = {null, null};
        
        for(String param : clientParams) {
            if(param.contains("<security>")) {
                loginAndPass[0] = param.split("<security>", 2)[0]; // часть до <login>
                loginAndPass[1] = param.split("<security>", 2)[1]; // часть до <password>
            }
        }
        
        System.out.println("логин: " + loginAndPass[0] + "\nпароль: " + loginAndPass[1]);
        
        if(loginAndPass[0] != null && loginAndPass[1] != null)
        {
            System.out.println("В КЛИЕНТСКОМ ЗАПРОСЕ ДАННЫЕ ДЛЯ ВХОДА НЕ НАЙДЕНЫ!");
        }
        
        return loginAndPass;
    }
}
