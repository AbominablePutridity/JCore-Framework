package vendor.EntityOrm;

import vendor.EntityOrm.Entity;

/**
 * Абстракция над всеми репозиторными классами для их унификации
 * использовать как расширения.
 * @author User
 * @param <ENTITY> Ксасс сущности, с которой работаем
 * @param <DTO> Класс с данными для сохранения в сущность (пока что - класс сущности также)
 */
public abstract class Repository<ENTITY extends Entity, DTO extends Entity> {
    ENTITY entityClass;
    
    /*
    установка сущности для управления ею.
    */
    public Repository(ENTITY entityClass) {
        this.entityClass = entityClass;
    }
    
    /**
     * Метод инициализации (зоздания) таблицы в БД.
     * @throws Exception 
     */
    public void init() throws Exception {
        // Создание таблицы на основе entityClass
        // На основании описанных выше обьектов, создаем таблицы
        System.out.println(entityClass.createTable(entityClass.initializeChild()));
    }
    
    /**
     * Метод для установки значений в сущность
     * @param data Обьект с сущностями для сохранения.
     * @throws Exception 
     */
    public void setData(DTO data) throws Exception {
        // Вставка данных из DTO
        // Передаем данные обьекта таблице в БД
        System.out.println(entityClass.insertData(data.initializeChild()));
    }
    
    /**
     * геттер, для взятия обьекта управляемой сущности (для маипуляции ею извне)
     * @return 
     */
    public ENTITY getEntity()
    {
        return entityClass;
    }
}