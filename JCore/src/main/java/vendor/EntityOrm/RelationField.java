package vendor.EntityOrm;

/**
 *
 * @author maxim
 */
public class RelationField {
    public Class<?> refClass; //класс для ссылки на связь сущности
    public Object valueKey; //значение ключа для связи сущностей
    
    public RelationField(Class<?> refClass, Object valueKey) {
        this.refClass = refClass;
        this.valueKey = valueKey;
    }
}
