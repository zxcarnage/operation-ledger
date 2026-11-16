package dev.ledger.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class StatusListener {
    private final ObjectMapper mapper;
    private final StatusMessageHandler handler;
    public StatusListener(ObjectMapper mapper, StatusMessageHandler handler) { this.mapper = mapper; this.handler = handler; }
    @KafkaListener(topics = "operation-status-commands")
    public void consume(String payload) throws JsonProcessingException {
        handler.handle(mapper.readValue(payload, StatusMessage.class));
    }
}
