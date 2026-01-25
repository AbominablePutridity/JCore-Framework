package com.mycompany.jcore.controller;

import java.sql.Statement;
import vendor.Security.Security;

/**
 * Пример контроллера с выводом переданных параметров клиенту.
 * @author User
 */
public class PersonController extends Security {
    
    public PersonController(Statement statement) {
        super(statement);
    }
    
    /* telnet 127.0.0.1 8082
    запрос (через коммандную строку): PersonController/createPersonAction<endl>helloWorld!<endl>JCore!<endl>ivanov<security>pass<endl>
    */
    public String createPersonAction(String[] params)
    {
        String result = "";
        
        if(super.checkRole("Person", "login", "password", "role", "USER", params)) { //проверка пользователя (Security-модуль)   
            for(String param : params)
            {
                result += "param is -> " + param + "\r\n";
                System.out.println("param is -> " + param);
            }
            
            return result;
        } else {
            return super.returnException();
        }
    }
}
