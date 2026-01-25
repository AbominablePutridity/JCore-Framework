package vendor.EntityOrm;

import java.util.List;

/**
 * Класс для хранения данных метаданных потомка класса сущности.
 * @author User
 */
public class EntityInfo {
    public Class<?> classType; //Имя дочернего класса
    public List<RelationField> refs; //Имена ключей
    public FieldNameWithType[] entityFields; //Поля с типами данных
   
    public EntityInfo(Class<?> classType, List<RelationField> refs, FieldNameWithType entityFields[])
    {
        this.classType = classType;
        this.refs = refs;
        this.entityFields = entityFields;
    }

    @Override
    public String toString() {
        String refStr = "";
        String fieldsStr = "";
        
        if(refs != null) {
            for(RelationField relation : refs) {
                refStr += relation.refClass.getSimpleName() + ", ";
            }
        }
        
        if(entityFields != null) {
            for(FieldNameWithType field : entityFields) {
                fieldsStr += (String) field.fieldName.toString() + ", ";
            }
        }
        
        return "[className = " + classType.getSimpleName() + "; refNames = [" + refStr + "]; fields = [" + fieldsStr +"]]";
    }
    
    
}
