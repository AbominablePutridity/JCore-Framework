package com.mycompany.jcore.entities;
import java.sql.Statement;
import vendor.EntityOrm.Entity;
import vendor.EntityOrm.RelationField;

/**
 * Пример:
 * 
 * Сущность Car. (модель данных для работы с БД)
 * @author User
 */
public class Car extends Entity {
    public String mark;
    public String color;
    public Long personId; //ключи - всегда называем в loverCamelCase, имя связующего класса + постфикс "Id" в конце названия
    
    //связь
    public Car(Statement statement) {
        super(statement); // передаем обьект для создания запросов родителю
        
        refs.add(new RelationField(Person.class, personId)); //связь с персоной машины
    }
}
