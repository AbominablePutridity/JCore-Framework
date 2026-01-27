package com.mycompany.jcore.repository;

import com.mycompany.jcore.entities.Car;
import vendor.EntityOrm.Entity;
import vendor.EntityOrm.Repository;

/**
 * Пример репозитория сущности Car.
 * 
 * Репозитории всегда наследуются от Repository<ENTITY, DTO>
 * так как он имеет все необходимые свойства и методы для всех репозиториев
 * - создание таблиц на основании сущности в базе данных
 * - передача обьекта сущности для извлечения из нее данных и вставку в базу данных 
 * @author User
 */
public class CarRepository extends Repository<Car, Car>{
    
    public CarRepository(Car entityClass) {
        super(entityClass);
    }
    
    /**
     * Можете создавать свои методы с выборкой данных из этой сущности
     * (репозиторные функции на взятие данных из базы данных)
     */
}
