package com.converter.notification;

import com.converter.common.events.RabbitTopologyConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }

    @Bean
    public TopicExchange currencyEventsExchange() {
        return new TopicExchange(RabbitTopologyConstants.EXCHANGE_EVENTS, true, false);
    }

    @Bean
    public DirectExchange currencyEventsDlx() {
        return new DirectExchange(RabbitTopologyConstants.EXCHANGE_DLX, true, false);
    }

    @Bean
    public Queue notificationsQueue() {
        return QueueBuilder.durable(RabbitTopologyConstants.QUEUE_NOTIFICATIONS)
                .withArgument("x-dead-letter-exchange", RabbitTopologyConstants.EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", RabbitTopologyConstants.ROUTING_DLQ)
                .build();
    }

    @Bean
    public Queue notificationsDlq() {
        return QueueBuilder.durable(RabbitTopologyConstants.QUEUE_NOTIFICATIONS_DLQ).build();
    }

    @Bean
    public Binding conversionBinding(Queue notificationsQueue, TopicExchange currencyEventsExchange) {
        return BindingBuilder.bind(notificationsQueue)
                .to(currencyEventsExchange)
                .with(RabbitTopologyConstants.ROUTING_CONVERSION_COMPLETED);
    }

    @Bean
    public Binding alertBinding(Queue notificationsQueue, TopicExchange currencyEventsExchange) {
        return BindingBuilder.bind(notificationsQueue)
                .to(currencyEventsExchange)
                .with(RabbitTopologyConstants.ROUTING_ALERT_TRIGGERED);
    }

    @Bean
    public Binding dlqBinding(Queue notificationsDlq, DirectExchange currencyEventsDlx) {
        return BindingBuilder.bind(notificationsDlq)
                .to(currencyEventsDlx)
                .with(RabbitTopologyConstants.ROUTING_DLQ);
    }
}
