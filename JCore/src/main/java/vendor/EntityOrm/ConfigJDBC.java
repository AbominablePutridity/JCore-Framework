package vendor.EntityOrm;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;


public class ConfigJDBC {
    // MySQL
    //String urlConnection = "jdbc:mysql://localhost:3306/база_данных"

    // PostgreSQL
    //String urlConnection = "jdbc:postgresql://localhost:5432/база_данных"

    // Oracle
    //String urlConnection = "jdbc:oracle:thin:@localhost:1521:XE"
    
    private String urlConnection = "jdbc:postgresql://localhost:5432/test1"; // строка подключения к бд
    private String userName = "postgres"; //пользователь бд
    private String password = "root"; //пароль бд
    
    public Connection getConnectionDB()
    {
        Connection connection = null;
                
        try {
            System.out.println("Connect to DB...");
            connection = DriverManager.getConnection(urlConnection, userName, password);
            System.out.println("Connection success!");
            //connection.close();
        } catch (SQLException e) {
            System.err.println("SQL ERROR: " + e.getMessage());
        }
        
        return connection;
    }

    public String getUrlConnection() {
        return urlConnection;
    }

    public void setUrlConnection(String urlConnection) {
        this.urlConnection = urlConnection;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
