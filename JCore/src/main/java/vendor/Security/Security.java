package vendor.Security;

import at.favre.lib.crypto.bcrypt.BCrypt;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import vendor.EntityOrm.Entity;

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
        
        try {
            // берем по логину пользователя его хешированный пароль и роль одним запросом
            ResultSet passwordResultSet = Entity.executeSQL(
                "SELECT " + passwordField + ", " + roleField + " FROM " + tableName + 
                " WHERE " + loginField + " = ? LIMIT 1",
                new Object[]
                {
                    clientLoginAndPasswordByQuery[0]
                }    
            );
            
            // Если есть результат, берем роль и пароль сравниваем ее с той ролью, которая нам нужна
            if (passwordResultSet.next()) {
                
                String storedHash = passwordResultSet.getString(passwordField); // хешированный пароль из Базы Данных
                String actualRole = passwordResultSet.getString(roleField); // роль из Базы Данных
                
                boolean isCheckedPassword = checkHashedPassword(storedHash, clientLoginAndPasswordByQuery[1]); // проверка на соответствие паролей
            
                if(isCheckedPassword) {
                    if(actualRole.equals(roleForChecking)) { // проверка на соответствие ролей из Базы Данных
                        return true;
                    }
                }
            
                // Закрываем ResultSet
                passwordResultSet.close();
            }
            
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
    
    /**
     * Для предоставления данных пользователя из запроса
     * @param clientParams параметры запроса пользователя
     * @return Логин и Пароль пользователя
     */
    public String[] extractLoginAndPasswordFromClientQuery(String[] clientParams)
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
    
    /**
     * Захешировать пароль пользователя
     * @param password пароль, вводимый пользователем
     * @return захешированный пароль
     */
    public static String hashPassword(String password) {
        String hashedPassword = BCrypt.withDefaults().hashToString(12, password.toCharArray());
        
        System.out.println("\n \t HASHED_PASSWORD = " + hashedPassword + " \n");
        
        return hashedPassword;
    }
    
    /**
     * Сравнить хеш пароля с самим паролем
     * @param cryptedPassword хешированный пароль
     * @param password пароль без хеша
     * @return true - если пароли совпадают, иначе false
     */
    public static boolean checkHashedPassword(String cryptedPassword, String password) {
        BCrypt.Result result = BCrypt.verifyer()
                .verify(password.toCharArray(), cryptedPassword);
        
        System.out.println("\n \t PASSWORD_IS_VERIFIED = " + String.valueOf(result.verified) + " \n");
        
        return result.verified;
    }
}
