package dev.ledger;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import dev.ledger.integration.*;
import dev.ledger.operation.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"ledger.outbox-enabled=false", "spring.jpa.properties.hibernate.generate_statistics=true"})
class LedgerIT {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.6-alpine");
    @Container static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");
    static final String ACCOUNT = "11111111-1111-1111-1111-111111111111";
    static final AtomicInteger accountResponse = new AtomicInteger(200);
    static final HttpServer accounts = accountServer();
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("ledger.accounts-url", () -> "http://localhost:" + accounts.getAddress().getPort());
    }
    static HttpServer accountServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/accounts/", exchange -> {
                byte[] body = ("{\"id\":\"" + ACCOUNT + "\",\"active\":true,\"currency\":\"EUR\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(accountResponse.get(), body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired KafkaTemplate<String, String> producer;
    @Autowired StatusMessageHandler handler;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
    @AfterAll static void stopAccountServer() { accounts.stop(0); }
    @BeforeEach void reset() {
        accountResponse.set(200);
        jdbc.execute("truncate status_history,outbox,processed_messages,operations restart identity cascade");
    }
    Map<String,Object> request() { return new HashMap<>(Map.of("accountId", ACCOUNT, "amount", "125.50", "currency", "EUR", "description", "Invoice INV-1042")); }
    ResponseEntity<JsonNode> create(UUID key, Map<String,Object> body) {
        var headers = new HttpHeaders();
        headers.set("Idempotency-Key", key.toString());
        return rest.exchange("/api/v1/operations", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }
    String createId() { return create(UUID.randomUUID(), request()).getBody().path("id").asText(); }
    ResponseEntity<JsonNode> change(String id, String status) {
        return rest.exchange("/api/v1/operations/" + id + "/status", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", status, "reason", "Processing result")), JsonNode.class);
    }
    ResponseEntity<JsonNode> get(String path) { return rest.getForEntity("/api/v1/operations" + path, JsonNode.class); }
    long count(String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }

    @Test void registersOperation() {
        var response = create(UUID.randomUUID(), request());
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getBody().path("status").asText()).isEqualTo("NEW");
        assertThat(count("outbox")).isEqualTo(1);
        assertThat(count("status_history")).isEqualTo(1);
    }
    @Test void replaysRegistration() {
        var key = UUID.randomUUID();
        var first = create(key, request());
        var replay = create(key, request());
        assertThat(replay.getStatusCode().value()).isEqualTo(200);
        assertThat(replay.getBody().path("id")).isEqualTo(first.getBody().path("id"));
        assertThat(count("operations")).isEqualTo(1);
        assertThat(count("outbox")).isEqualTo(1);
    }
    @Test void rejectsChangedIdempotentPayload() {
        var key = UUID.randomUUID(); create(key, request());
        var changed = request(); changed.put("amount", "130.00");
        assertThat(create(key, changed).getStatusCode().value()).isEqualTo(409);
    }
    @Test void rejectsNegativeAmount() {
        var body = request(); body.put("amount", "-1");
        assertThat(create(UUID.randomUUID(), body).getStatusCode().value()).isEqualTo(400);
        assertThat(count("operations")).isZero();
    }
    @Test void requiresIdempotencyKey() {
        assertThat(rest.postForEntity("/api/v1/operations", request(), JsonNode.class).getStatusCode().value()).isEqualTo(400);
    }
    @Test void rejectsUnsupportedCurrency() {
        var body = request(); body.put("currency", "BTC");
        assertThat(create(UUID.randomUUID(), body).getStatusCode().value()).isEqualTo(400);
    }
    @Test void returnsOperation() {
        var id = createId(); var response = get("/" + id);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().path("id").asText()).isEqualTo(id);
    }
    @Test void unknownOperationReturns404() { assertThat(get("/" + UUID.randomUUID()).getStatusCode().value()).isEqualTo(404); }
    @Test void malformedIdReturns400() { assertThat(get("/bad-id").getStatusCode().value()).isEqualTo(400); }
    @Test void listsAccountOperations() {
        createId(); createId();
        assertThat(get("?accountId=" + ACCOUNT).getBody().size()).isEqualTo(2);
    }
    @Test void paginatesWithoutOverlap() {
        createId(); createId();
        var first = get("?accountId=" + ACCOUNT + "&size=1&page=0").getBody();
        var second = get("?accountId=" + ACCOUNT + "&size=1&page=1").getBody();
        assertThat(first.size()).isEqualTo(1);
        assertThat(first.get(0).path("id")).isNotEqualTo(second.get(0).path("id"));
    }
    @Test void isolatesAccounts() {
        createId();
        assertThat(get("?accountId=" + UUID.randomUUID()).getBody().size()).isZero();
    }
    @Test void rejectsOversizedPage() { assertThat(get("?accountId=" + ACCOUNT + "&size=101").getStatusCode().value()).isEqualTo(400); }
    @Test void changesStatus() {
        var id = createId(); var response = change(id, "PROCESSING");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().path("version").asLong()).isEqualTo(1);
        assertThat(count("status_history")).isEqualTo(2);
        assertThat(count("outbox")).isEqualTo(2);
    }
    @Test void rejectsForbiddenTransition() {
        var id = createId();
        assertThat(change(id, "COMPLETED").getStatusCode().value()).isEqualTo(409);
        assertThat(get("/" + id).getBody().path("status").asText()).isEqualTo("NEW");
        assertThat(count("outbox")).isEqualTo(1);
    }
    @Test void rejectsMissingStatus() {
        var id = createId();
        var response = rest.exchange("/api/v1/operations/" + id + "/status", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("reason", "No status")), JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }
    @Test void cancelsNewOperation() {
        var response = rest.postForEntity("/api/v1/operations/" + createId() + "/cancel", Map.of("reason", "Customer request"), JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().path("status").asText()).isEqualTo("CANCELLED");
    }
    @Test void cannotCancelProcessingOperation() {
        var id = createId(); change(id, "PROCESSING");
        assertThat(rest.postForEntity("/api/v1/operations/" + id + "/cancel", Map.of("reason", "Too late"), JsonNode.class).getStatusCode().value()).isEqualTo(409);
    }
    @Test void returnsOrderedStatusHistory() {
        var id = createId(); change(id, "PROCESSING"); change(id, "COMPLETED");
        var history = get("/" + id + "/history").getBody();
        assertThat(history.size()).isEqualTo(3);
        assertThat(history.get(0).path("toStatus").asText()).isEqualTo("NEW");
        assertThat(history.get(2).path("fromStatus").asText()).isEqualTo("PROCESSING");
    }
    @Test void missingHistoryReturns404() { assertThat(get("/" + UUID.randomUUID() + "/history").getStatusCode().value()).isEqualTo(404); }
    @Test void returnsAllowedTransitions() {
        var response = get("/" + createId() + "/transitions");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().toString()).contains("PROCESSING", "CANCELLED");
    }
    @Test void terminalOperationHasNoTransitions() {
        var id = createId(); change(id, "PROCESSING"); change(id, "FAILED");
        assertThat(get("/" + id + "/transitions").getBody().size()).isZero();
    }
    @Test void summarizesAccount() {
        createId(); var id = createId(); change(id, "PROCESSING");
        var summary = get("/summary?accountId=" + ACCOUNT).getBody();
        assertThat(summary.size()).isEqualTo(2);
        assertThat(summary.get(0).path("count").asLong()).isEqualTo(1);
    }
    @Test void emptyAccountHasEmptySummary() {
        createId(); assertThat(get("/summary?accountId=" + UUID.randomUUID()).getBody().size()).isZero();
    }

    @Test void upstreamFailureReturns503WithoutWrite() {
        accountResponse.set(500);
        assertThat(create(UUID.randomUUID(), request()).getStatusCode().value()).isEqualTo(503);
        assertThat(count("operations")).isZero();
    }
    @Test void replayWorksDuringUpstreamOutage() {
        var key = UUID.randomUUID(); create(key, request()); accountResponse.set(500);
        assertThat(create(key, request()).getStatusCode().value()).isEqualTo(200);
    }
    @Test void concurrentRegistrationCreatesOneOperation() throws Exception {
        var key = UUID.randomUUID(); var pool = Executors.newFixedThreadPool(2);
        try {
            var responses = pool.invokeAll(List.<Callable<ResponseEntity<JsonNode>>>of(() -> create(key, request()), () -> create(key, request())));
            var codes = new ArrayList<Integer>();
            for (var response : responses) codes.add(response.get(15, TimeUnit.SECONDS).getStatusCode().value());
            assertThat(codes).containsExactlyInAnyOrder(200, 201);
            assertThat(count("operations")).isEqualTo(1);
            assertThat(count("outbox")).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void concurrentTransitionsHaveOneWinner() throws Exception {
        var id = createId(); var pool = Executors.newFixedThreadPool(2);
        try {
            var responses = pool.invokeAll(List.<Callable<ResponseEntity<JsonNode>>>of(() -> change(id, "PROCESSING"), () -> change(id, "CANCELLED")));
            var codes = new ArrayList<Integer>();
            for (var response : responses) codes.add(response.get(15, TimeUnit.SECONDS).getStatusCode().value());
            assertThat(codes).containsExactlyInAnyOrder(200, 409);
            assertThat(count("status_history")).isEqualTo(2);
        } finally { pool.shutdownNow(); }
    }
    @Test void duplicateKafkaDeliveryAppliesOnce() throws Exception {
        var id = createId();
        var event = new StatusMessage(UUID.randomUUID(), UUID.fromString(id), Status.PROCESSING, "Processor accepted");
        var json = mapper.writeValueAsString(event);
        producer.send("operation-status-commands", id, json).get(10, TimeUnit.SECONDS);
        producer.send("operation-status-commands", id, json).get(10, TimeUnit.SECONDS);
        // A following event in the same partition proves both duplicates were consumed.
        var completed = new StatusMessage(UUID.randomUUID(), UUID.fromString(id), Status.COMPLETED, "Processor completed");
        producer.send("operation-status-commands", id, mapper.writeValueAsString(completed)).get(10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(get("/" + id).getBody().path("status").asText()).isEqualTo("COMPLETED");
            assertThat(count("processed_messages")).isEqualTo(2);
            assertThat(count("status_history")).isEqualTo(3);
        });
    }
    @Test void rejectedEventRollsBackInboxMarker() {
        var id = UUID.fromString(createId());
        assertThatThrownBy(() -> handler.handle(new StatusMessage(UUID.randomUUID(), id, Status.COMPLETED, "Invalid")))
                .isInstanceOf(ConflictException.class);
        assertThat(count("processed_messages")).isZero();
        assertThat(count("outbox")).isEqualTo(1);
    }
    @Test void reusedEventIdCannotChangePayload() {
        var id = UUID.fromString(createId()); var eventId = UUID.randomUUID();
        handler.handle(new StatusMessage(eventId, id, Status.PROCESSING, "Accepted"));
        assertThatThrownBy(() -> handler.handle(new StatusMessage(eventId, id, Status.COMPLETED, "Changed payload")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(count("processed_messages")).isEqualTo(1);
        assertThat(count("status_history")).isEqualTo(2);
    }
    @Test void malformedKafkaMessageGoesToDlt() throws Exception {
        var properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<String,String>(properties, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("operation-status-commands.DLT"));
            String malformed = "bad-json-" + UUID.randomUUID();
            producer.send("operation-status-commands", "malformed", malformed).get(10, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                for (var record : consumer.poll(Duration.ofMillis(300))) if (record.value().equals(malformed)) return true;
                return false;
            });
            assertThat(count("operations")).isZero();
        }
    }

    @Test void publishesCommittedOutboxToKafka() {
        var id = createId();
        var publisher = new OutboxPublisher(jdbc, producer);
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(tx -> publisher.publish());
        assertThat(jdbc.queryForObject("select count(*) from outbox where published_at is not null", Long.class)).isEqualTo(1);
        var properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<String,String>(properties, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("operation-events"));
            await().atMost(Duration.ofSeconds(20)).until(() -> {
                for (var record : consumer.poll(Duration.ofMillis(300))) if (id.equals(record.key())) return true;
                return false;
            });
        }
    }
    @Test void historyUsesOneProjectionQuery() {
        for (int i = 0; i < 5; i++) createId();
        var stats = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        stats.clear();
        assertThat(get("?accountId=" + ACCOUNT).getBody().size()).isEqualTo(5);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
        assertThat(stats.getEntityLoadCount()).isZero();
    }
}
