package vendor.DI;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author User
 */
public class ContainerDI {
    // Контейнер с бинами
    private static Map<Class<?>, Object> beans = new HashMap<>();
    
    /**
     * Регистрация бина в контейнере
     * @param type Тип обьекта бина
     * @param instance обьект бина
     */
    public static void register(Class<?> type, Object instance)
    {
        beans.put(type, instance);
    }
    
    public static <T> T getBean(Class<T> type)
    {
        return (T) beans.get(type); // просто достаем из мапы и возвращаем
    }
}
