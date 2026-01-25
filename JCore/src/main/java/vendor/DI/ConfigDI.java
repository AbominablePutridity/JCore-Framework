package vendor.DI;

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
        Ваши бины можете регестрировать здесь (только компоненты системы регистрируются как бины!!!)
        (Контроллеры (их обьекты) регестрируются в обьекте бина сервера, в обьекте Controller controllerPull,
        кладутся в List<Object> declaredControllers - это сделанно для роутинга)
        */
    }
}
