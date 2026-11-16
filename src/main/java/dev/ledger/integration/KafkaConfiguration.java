package dev.ledger.integration;

import dev.ledger.operation.*;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfiguration {
    @Bean NewTopic commands() { return TopicBuilder.name("operation-status-commands").partitions(3).replicas(1).build(); }
    @Bean NewTopic events() { return TopicBuilder.name("operation-events").partitions(3).replicas(1).build(); }
    @Bean NewTopic deadLetters() { return TopicBuilder.name("operation-status-commands.DLT").partitions(3).replicas(1).build(); }
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000, 2));
        handler.addNotRetryableExceptions(IllegalArgumentException.class, ConflictException.class);
        return handler;
    }
}
