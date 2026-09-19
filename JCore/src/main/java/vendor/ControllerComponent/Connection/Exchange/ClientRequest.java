package vendor.ControllerComponent.Connection.Exchange;

/**
 * Разобранный запрос клиента.
 *
 * Поле {@code binaryFiles} — массив путей к файлам, уже сохранённым
 * на диске методом {@link #readOneFileToDisk}. Это не данные, а ссылки:
 * каждый {@link File} занимает ~100 байт в heap, независимо от размера
 * самого файла.
 */
public class ClientRequest {
    private String route;
    private Data data;

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }
    
    public String[] getPartsByRoute() {
        return route.split("/");
    }
}
