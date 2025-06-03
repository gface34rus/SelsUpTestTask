# API Честного Знака

Проект представляет собой реализацию клиента для работы с API Честного знака. 

## Описание
Класс `CrptApi` предоставляет возможность создания документов для ввода в оборот товара, произведенного в РФ. 
Реализация является потокобезопасной и поддерживает ограничение на количество запросов к API.

## Требования
- Java 11 или выше
- Maven

## Зависимости
- com.fasterxml.jackson.core:jackson-databind:2.15.2
- com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2
- org.apache.httpcomponents:httpclient:4.5.14

## Установка
```bash
mvn clean install
```

## Использование
```java
import java.util.concurrent.TimeUnit;

public class Example {
    public static void main(String[] args) {
        // Создание экземпляра API с ограничением 5 запросов в минуту
        try (CrptApi api = new CrptApi(TimeUnit.MINUTES, 5)) {
            // Создание документа
            CrptApi.Document document = new CrptApi.Document();
            // Заполнение документа...
            
            // Подпись документа (для примера)
            String signature = "signature";
            
            // Отправка документа
            api.createDocument(document, signature);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

## Особенности реализации
- Thread-safe реализация с использованием Semaphore
- Ограничение количества запросов в заданный интервал времени
- Автоматическое освобождение ресурсов через AutoCloseable
- Поддержка сериализации/десериализации дат (LocalDate)
- Корректная обработка ошибок и освобождение ресурсов

## Структура документа
```java
public class Document {
    private Description description;
    private String docId;
    private String docStatus;
    private String docType;
    private boolean importRequest;
    private String ownerInn;
    private String participantInn;
    private String producerInn;
    private LocalDate productionDate;
    private String productionType;
    private List<Product> products;
    private LocalDate regDate;
    private String regNumber;
    // ... геттеры и сеттеры
}
