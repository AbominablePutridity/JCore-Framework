package vendor.ControllerComponent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author User
 */
public class Controller {

    // Лист со списком объектов-контроллеров с эндпоинтами.
    public List<Object> declaredControllers = new ArrayList<>();

    /**
     * Запускает метод класса, взятый из resultUrl через рефлексию.
     *
     * @param resultUrl Строка в формате "Класс/методДляЗапуска".
     * @param params Текстовые параметры запроса.
     * @param binaryFiles Бинарные файлы запроса.
     * @return Результат обработки метода контроллера.
     */
    public Object startMethodByUrl(
            String resultUrl,
            String[] params,
            byte[][] binaryFiles
    ) {

        String[] parts = resultUrl.split("/");

        for (Object controller : declaredControllers) {

            if (controller.getClass().getSimpleName().equals(parts[0])) {

                try {

                    Method method = controller
                            .getClass()
                            .getMethod(
                                    parts[1],
                                    String[].class,
                                    byte[][].class
                            );

                    // Вызываем метод контроллера
                    Object result = method.invoke(
                            controller,
                            params,
                            binaryFiles
                    );

                    return result;

                } catch (Exception e) {

                    System.out.println(
                            "CONTROLLER ERROR: " +
                            e.getMessage()
                    );

                    return null;
                }
            }
        }

        return null;
    }
}
