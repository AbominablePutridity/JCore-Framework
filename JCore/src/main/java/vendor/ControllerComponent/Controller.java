package vendor.ControllerComponent;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import vendor.ControllerComponent.Connection.Exchange.ClientRequest;

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
     * @param request Запрос с данными от клиента.
     */
    public Object startMethodByUrl(
        ClientRequest request
    ) {

        String[] parts = request.getPartsByRoute();

        for (Object controller : declaredControllers) {

            if (controller.getClass().getSimpleName().equals(
                    parts[0]
                )
            ) {

                try {

                    Method method = controller
                            .getClass()
                            .getMethod(
                                    parts[1],
                                    ClientRequest.class
                            );

                    return method.invoke(
                            controller,
                            request
                    );

                } catch (java.lang.reflect.InvocationTargetException e) {
                    // Настоящая причина — внутри e.getCause()
                    System.out.println("CONTROLLER ERROR, real cause:");
                    Throwable cause = e.getCause();
                    if (cause != null) {
                        cause.printStackTrace();
                    } else {
                        e.printStackTrace();
                    }
                    return null;

                } catch (Exception e) {
                    System.out.println("CONTROLLER ERROR:");
                    e.printStackTrace();
                    return null;
                }
            }
        }

        return null;
    }
}
