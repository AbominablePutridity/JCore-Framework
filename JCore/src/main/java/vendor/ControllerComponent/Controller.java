package vendor.ControllerComponent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author User
 */
public class Controller {
    //лист со списком обьектов-контроллеров с Эндпоинтами.
    public List<Object> declaredControllers = new ArrayList<>();
    
    /**
     * Запускает метод класса, взятый resultUrl из через рефлексию.
     * @param resultUrl Строка в формате "Класс/методДляЗапуска".
     * @return Результат обработки метода контроллера
     */
    public Object startMethodByUrl(String resultUrl, String[] params) {
        String[] parts = resultUrl.split("/");
        
        for (Object controller : declaredControllers) {
            if (controller.getClass().getSimpleName().equals(parts[0])) {
                try {
                    Method method = controller.getClass().getMethod(parts[1], String[].class);
                    
                    // Вызываем метод и получаем результат
                    Object result = method.invoke(controller, (Object) params);
                    return result;
                } catch (Exception e) {
                    System.out.println("CONTROLLER ERROR: " + e.getMessage());
                    
                    return null;
                }
            }
        }
        
        return null;
    }
}
