package com.mycompany.jcore;

import com.mycompany.jcore.controller.PersonController;
import com.mycompany.jcore.entities.Car;
import com.mycompany.jcore.entities.Person;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import vendor.ControllerComponent.Connection.Server;
import vendor.ControllerComponent.Controller;
import vendor.DI.ConfigDI;
import vendor.DI.ContainerDI;
import vendor.EntityOrm.ConfigJDBC;
import vendor.JCoreMeta;

/**
 *
 * @author maxim
 */
public class JCore {

    public static void main(String[] args) throws SQLException, IllegalArgumentException, IllegalAccessException, IOException {
        //инициализация бинов в контейнере
        ConfigDI.setBeans();
        
        //запуск сервера
        Server server = ContainerDI.getBean(Server.class); //берем бин сервера из DI-контейнера
        
        // регестрируем все наши контроллеры на сервере (для роутинга)
        server.controllerPull.declaredControllers.add(new PersonController(ContainerDI.getBean(Statement.class)));
        
        server.startServer(); //запускаем сервер
        
        
        //пример работы с БД
        
        // Создаем сущности
        Person person1 = new Person(ContainerDI.getBean(Statement.class));
        person1.id = 1;
        person1.name = "Иван";
        person1.surname = "Иванов";
        person1.login = "ivanov";
        person1.password = "pass";
        person1.role = "USER";
        
        System.out.println(person1.initializeChild().toString());

        // На основании описанных выше обьектов, создаем таблицы
        System.out.println(person1.createTable(person1.initializeChild()));
        
        // Передаем данные обьекта таблице в БД
        System.out.println(person1.insertData(person1.initializeChild()));
        
        // тоже самое с машиной
        Car car1 = new Car(ContainerDI.getBean(Statement.class));
        car1.id = 1;
        car1.mark = "Таета";
        car1.color = "черный";
        car1.personId = Long.valueOf("1");
        
        System.out.println(car1.initializeChild().toString());
        
        // Передаем данные обьекта таблице в БД
        System.out.println(car1.insertData(car1.initializeChild()));
    }
}
