package vendor.DI;

import com.mycompany.jcore.entities.Car;
import com.mycompany.jcore.entities.Person;
import com.mycompany.jcore.repository.CarRepository;
import com.mycompany.jcore.repository.PersonRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import vendor.ControllerComponent.Connection.Server;
import vendor.ControllerComponent.Controller;
import vendor.EntityOrm.ConfigJDBC;

/**
 *  Это конфиг для регистрации бинов в проекте для удобного доступа к зависимостям извне
 * @author User
 */
public class ConfigDI {

    /**
     * Конфиг, для создания и выкладки бинов в контейнер
     * 
     * Задаем базовые бины в контейнер (для дальнейшего внедрения).
     * @throws SQLException 
     */
    public static void setBeans() throws SQLException
    {
        //регестрируем бин для маршрутизации контроллеров в приложении
        ContainerDI.register(Controller.class, new Controller());
        
        //регестрируем бин сервера
        ContainerDI.register(Server.class, new Server(ContainerDI.getBean(Controller.class), 8082)); //на порту 8082
        
        //регестрируем бин для подключения БД
        ContainerDI.register(Connection.class, new ConfigJDBC().getConnectionDB());
        
        //регестрируем бин Statement для выполнения им SQL запросов в сущностях (для внедрения его в конструктор сущности)
        ContainerDI.register(Statement.class, ContainerDI.getBean(Connection.class).createStatement()); //создается на основе взятия обьекта подключения
        
        /*
        Ваши бины можете регестрировать здесь (сущности, репозитории и тд)
        (Контроллеры (их обьекты) регестрируются в обьекте бина сервера, в обьекте Controller controllerPull,
        кладутся в List<Object> declaredControllers - это сделанно для роутинга)
        */
        
        //регестрируем наши сущности (наши машину и пользователя, как пример)
        ContainerDI.register(Car.class, new Car(ContainerDI.getBean(Statement.class)));
        ContainerDI.register(Person.class, new Person(ContainerDI.getBean(Statement.class)));
        
        //регестрируем наши репозитории (наши машину и пользователя, как пример)
        ContainerDI.register(CarRepository.class, new CarRepository(ContainerDI.getBean(Car.class)));
        ContainerDI.register(PersonRepository.class, new PersonRepository(ContainerDI.getBean(Person.class)));
    }
}
