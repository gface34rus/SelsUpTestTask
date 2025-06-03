import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CrptApi implements AutoCloseable {
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    
    private final ScheduledExecutorService scheduler;
    private final Semaphore requestSemaphore;
    private final AtomicInteger requestCount;
    private final long timeIntervalMillis;
    private final int requestLimit;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("Request limit must be positive");
        }

        this.requestLimit = requestLimit;
        this.timeIntervalMillis = timeUnit.toMillis(1);
        this.requestSemaphore = new Semaphore(requestLimit, true);
        this.requestCount = new AtomicInteger(0);
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
        this.httpClient = HttpClients.custom()
                .setMaxConnTotal(requestLimit)
                .setMaxConnPerRoute(requestLimit)
                .build();

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "CrptApi-Scheduler");
            thread.setDaemon(true);
            return thread;
        });

        // Планируем периодический сброс счетчика
        scheduler.scheduleAtFixedRate(() -> {
            int current = requestCount.get();
            requestCount.addAndGet(-current);
            int releasedPermits = Math.min(current, requestLimit);
            requestSemaphore.release(releasedPermits);
        }, timeIntervalMillis, timeIntervalMillis, TimeUnit.MILLISECONDS);
    }

    public void createDocument(Document document, String signature) throws InterruptedException, IOException {
        if (document == null || signature == null || signature.isEmpty()) {
            throw new IllegalArgumentException("Document and signature must not be null or empty");
        }

        // Ожидаем разрешения на выполнение запроса
        if (!requestSemaphore.tryAcquire(1, TimeUnit.MINUTES)) {
            throw new RuntimeException("Timeout waiting for request limit");
        }

        try {
            requestCount.incrementAndGet();

            // Подготовка запроса
            DocumentRequest request = new DocumentRequest();
            request.setProductDocument(objectMapper.writeValueAsString(document));
            request.setDocumentFormat("MANUAL");
            request.setType("LP_INTRODUCE_GOODS");
            request.setSignature(signature);

            String jsonRequest = objectMapper.writeValueAsString(request);

            // Отправка запроса
            HttpPost httpPost = new HttpPost(API_URL);
            httpPost.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            httpPost.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + signature);
            httpPost.setEntity(new StringEntity(jsonRequest, "UTF-8"));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != 200) {
                    throw new IOException("API request failed with status: " + statusCode);
                }
            }
        } catch (Exception e) {
            // В случае ошибки освобождаем семафор
            requestSemaphore.release();
            requestCount.decrementAndGet();
            throw e;
        }
    }

    @Override
    public void close() {
        try {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            httpClient.close();
        } catch (Exception e) {
            // Игнорируем ошибки при закрытии
        }
    }

    // Классы для представления документа и запроса
    public static class Document {
        @JsonProperty("description")
        private Description description;
        @JsonProperty("doc_id")
        private String docId;
        @JsonProperty("doc_status")
        private String docStatus;
        @JsonProperty("doc_type")
        private String docType;
        @JsonProperty("import_request")
        private boolean importRequest;
        @JsonProperty("owner_inn")
        private String ownerInn;
        @JsonProperty("participant_inn")
        private String participantInn;
        @JsonProperty("producer_inn")
        private String producerInn;
        @JsonProperty("production_date")
        private LocalDate productionDate;
        @JsonProperty("production_type")
        private String productionType;
        @JsonProperty("products")
        private List<Product> products;
        @JsonProperty("reg_date")
        private LocalDate regDate;
        @JsonProperty("reg_number")
        private String regNumber;

        public Description getDescription() {
            return description;
        }

        public void setDescription(Description description) {
            this.description = description;
        }

        public String getDocId() {
            return docId;
        }

        public void setDocId(String docId) {
            this.docId = docId;
        }

        public String getDocStatus() {
            return docStatus;
        }

        public void setDocStatus(String docStatus) {
            this.docStatus = docStatus;
        }

        public String getDocType() {
            return docType;
        }

        public void setDocType(String docType) {
            this.docType = docType;
        }

        public boolean isImportRequest() {
            return importRequest;
        }

        public void setImportRequest(boolean importRequest) {
            this.importRequest = importRequest;
        }

        public String getOwnerInn() {
            return ownerInn;
        }

        public void setOwnerInn(String ownerInn) {
            this.ownerInn = ownerInn;
        }

        public String getParticipantInn() {
            return participantInn;
        }

        public void setParticipantInn(String participantInn) {
            this.participantInn = participantInn;
        }

        public String getProducerInn() {
            return producerInn;
        }

        public void setProducerInn(String producerInn) {
            this.producerInn = producerInn;
        }

        public LocalDate getProductionDate() {
            return productionDate;
        }

        public void setProductionDate(LocalDate productionDate) {
            this.productionDate = productionDate;
        }

        public String getProductionType() {
            return productionType;
        }

        public void setProductionType(String productionType) {
            this.productionType = productionType;
        }

        public List<Product> getProducts() {
            return products;
        }

        public void setProducts(List<Product> products) {
            this.products = products;
        }

        public LocalDate getRegDate() {
            return regDate;
        }

        public void setRegDate(LocalDate regDate) {
            this.regDate = regDate;
        }

        public String getRegNumber() {
            return regNumber;
        }

        public void setRegNumber(String regNumber) {
            this.regNumber = regNumber;
        }
    }

    public static class Description {
        @JsonProperty("participant_inn")
        private String participantInn;

        public String getParticipantInn() {
            return participantInn;
        }

        public void setParticipantInn(String participantInn) {
            this.participantInn = participantInn;
        }
    }

    public static class Product {
        @JsonProperty("certificate_document")
        private String certificateDocument;
        @JsonProperty("certificate_document_date")
        private LocalDate certificateDocumentDate;
        @JsonProperty("certificate_document_number")
        private String certificateDocumentNumber;
        @JsonProperty("owner_inn")
        private String ownerInn;
        @JsonProperty("producer_inn")
        private String producerInn;
        @JsonProperty("production_date")
        private LocalDate productionDate;
        @JsonProperty("tnved_code")
        private String tnvedCode;
        @JsonProperty("uit_code")
        private String uitCode;
        @JsonProperty("uitu_code")
        private String uituCode;

        public String getCertificateDocument() {
            return certificateDocument;
        }

        public void setCertificateDocument(String certificateDocument) {
            this.certificateDocument = certificateDocument;
        }

        public LocalDate getCertificateDocumentDate() {
            return certificateDocumentDate;
        }

        public void setCertificateDocumentDate(LocalDate certificateDocumentDate) {
            this.certificateDocumentDate = certificateDocumentDate;
        }

        public String getCertificateDocumentNumber() {
            return certificateDocumentNumber;
        }

        public void setCertificateDocumentNumber(String certificateDocumentNumber) {
            this.certificateDocumentNumber = certificateDocumentNumber;
        }

        public String getOwnerInn() {
            return ownerInn;
        }

        public void setOwnerInn(String ownerInn) {
            this.ownerInn = ownerInn;
        }

        public String getProducerInn() {
            return producerInn;
        }

        public void setProducerInn(String producerInn) {
            this.producerInn = producerInn;
        }

        public LocalDate getProductionDate() {
            return productionDate;
        }

        public void setProductionDate(LocalDate productionDate) {
            this.productionDate = productionDate;
        }

        public String getTnvedCode() {
            return tnvedCode;
        }

        public void setTnvedCode(String tnvedCode) {
            this.tnvedCode = tnvedCode;
        }

        public String getUitCode() {
            return uitCode;
        }

        public void setUitCode(String uitCode) {
            this.uitCode = uitCode;
        }

        public String getUituCode() {
            return uituCode;
        }

        public void setUituCode(String uituCode) {
            this.uituCode = uituCode;
        }
    }

    private static class DocumentRequest {
        @JsonProperty("product_document")
        private String productDocument;

        @JsonProperty("document_format")
        private String documentFormat;

        @JsonProperty("type")
        private String type;

        @JsonProperty("signature")
        private String signature;

        public String getProductDocument() {
            return productDocument;
        }

        public void setProductDocument(String productDocument) {
            this.productDocument = productDocument;
        }

        public String getDocumentFormat() {
            return documentFormat;
        }

        public void setDocumentFormat(String documentFormat) {
            this.documentFormat = documentFormat;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getSignature() {
            return signature;
        }

        public void setSignature(String signature) {
            this.signature = signature;
        }
    }
} 