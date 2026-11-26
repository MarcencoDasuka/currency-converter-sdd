package com.converter.common.events;

public final class RabbitTopologyConstants {

    private RabbitTopologyConstants() {}

    public static final String EXCHANGE_EVENTS = "currency.events";
    public static final String EXCHANGE_DLX = "currency.events.dlx";
    public static final String QUEUE_NOTIFICATIONS = "currency.notifications";
    public static final String QUEUE_NOTIFICATIONS_DLQ = "currency.notifications.dlq";
    public static final String ROUTING_CONVERSION_COMPLETED = "conversion.completed";
    public static final String ROUTING_ALERT_TRIGGERED = "alert.triggered";
    public static final String ROUTING_DLQ = "notifications.dead";
    public static final String EVENT_CONVERSION_COMPLETED = "ConversionCompleted";
    public static final String EVENT_RATE_ALERT_TRIGGERED = "RateAlertTriggered";
}
