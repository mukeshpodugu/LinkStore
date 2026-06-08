package com.linkstore.metadata.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "linkstore.exchange";
    public static final String REPLICATION_QUEUE = "linkstore.replication.queue";
    public static final String REPLICATION_ROUTING_KEY = "linkstore.replication.key";

    public static final String AUDIT_QUEUE = "linkstore.audit.queue";
    public static final String AUDIT_ROUTING_KEY = "linkstore.audit.key";

    public static final String DOWNLOAD_TRACKER_QUEUE = "linkstore.download.queue";
    public static final String DOWNLOAD_TRACKER_ROUTING_KEY = "linkstore.download.key";

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public Queue replicationQueue() {
        return QueueBuilder.durable(REPLICATION_QUEUE).build();
    }

    @Bean
    public Queue auditQueue() {
        return QueueBuilder.durable(AUDIT_QUEUE).build();
    }

    @Bean
    public Queue downloadQueue() {
        return QueueBuilder.durable(DOWNLOAD_TRACKER_QUEUE).build();
    }

    @Bean
    public Binding bindingReplication(Queue replicationQueue, DirectExchange exchange) {
        return BindingBuilder.bind(replicationQueue).to(exchange).with(REPLICATION_ROUTING_KEY);
    }

    @Bean
    public Binding bindingAudit(Queue auditQueue, DirectExchange exchange) {
        return BindingBuilder.bind(auditQueue).to(exchange).with(AUDIT_ROUTING_KEY);
    }

    @Bean
    public Binding bindingDownload(Queue downloadQueue, DirectExchange exchange) {
        return BindingBuilder.bind(downloadQueue).to(exchange).with(DOWNLOAD_TRACKER_ROUTING_KEY);
    }
}
