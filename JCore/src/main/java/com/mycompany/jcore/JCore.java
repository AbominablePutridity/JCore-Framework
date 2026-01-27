package com.mycompany.jcore;

import com.mycompany.jcore.controller.PersonController;
import com.mycompany.jcore.entities.Car;
import com.mycompany.jcore.entities.Person;
import com.mycompany.jcore.repository.CarRepository;
import com.mycompany.jcore.repository.PersonRepository;
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

    public static void main(String[] args) throws SQLException, IllegalArgumentException, IllegalAccessException, IOException, Exception {
        //инициализация бинов в контейнере
        ConfigDI.setBeans();
        
        /*
        создаем таблицы в правильном порядке на основе сущностей (нужно делать при старе программы)
        (от справочников к связующим таблицам) - ВАЖНО!!!
        путем вызова метода init() в репозиторных бинах наших сущностей.
        */
        ContainerDI.getBean(PersonRepository.class).init();
        ContainerDI.getBean(CarRepository.class).init();
        
        
        /* 
            //пример работы с БД - вставка данных в сущности в формате обьекта с данными.
            //необязательно к реализации - использовать в зависимости от необходимости.
        
            // Создаем сущность c данными
            Person person1 = ContainerDI.getBean(Person.class); //берем сущность Person из контейнера

            //заполняем его данными
            //person1.id = 1; //устанавливать не обязательно - serial счетчик
            person1.name = "Иван";
            person1.surname = "Иванов";
            person1.login = "ivanov";
            person1.password = "pass";
            person1.role = "USER";

            //вызываем метод setData() и передаем данные в виде обьекта для создания записи в базе данных.
            ContainerDI.getBean(PersonRepository.class).setData(person1);

            // тоже самое с машиной
            Car car1 = ContainerDI.getBean(Car.class); //берем сущность Car из контейнера

            //заполняем ее данными
            //car1.id = 1; //устанавливать не обязательно - serial счетчик
            car1.mark = "Таета";
            car1.color = "черный";
            car1.personId = Long.valueOf("1");

            //вызываем метод setData() и передаем данные в виде обьекта для создания записи в базе данных.
            ContainerDI.getBean(CarRepository.class).setData(car1);
        */
        
        
        //запуск сервера
        Server server = ContainerDI.getBean(Server.class); //берем бин сервера из DI-контейнера
        
        // регестрируем все наши контроллеры на сервере (для роутинга)
        server.controllerPull.declaredControllers.add(new PersonController(ContainerDI.getBean(Statement.class)));
        
        server.startServer(); //запускаем сервер
    }
}
