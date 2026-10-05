package space.ducvinh.ticket_booking_service.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "ticketing.exchange";
    public static final String DLX = "ticketing.dlx";

    public static final String NOTIFICATION_QUEUE = "notification.queue";
    public static final String TICKET_QUEUE = "ticket.queue";
    public static final String EMAIL_QUEUE = "email.queue";
    public static final String ANALYTICS_QUEUE = "analytics.queue";
    public static final String RESERVATION_DELAY_QUEUE = "reservation.delay.queue";
    public static final String RESERVATION_EXPIRE_QUEUE = "reservation.expire.queue";

    public static final String NOTIFICATION_DLQ = "notification.dlq";
    public static final String TICKET_DLQ = "ticket.dlq";
    public static final String EMAIL_DLQ = "email.dlq";
    public static final String ANALYTICS_DLQ = "analytics.dlq";
    public static final String RESERVATION_EXPIRE_DLQ = "reservation.expire.dlq";

    public static final String RK_NOTIFICATION = "notification.#";
    public static final String RK_TICKET = "ticket.#";
    public static final String RK_EMAIL = "email.#";
    public static final String RK_ANALYTICS = "analytics.#";
    public static final String RK_RESERVATION_DELAY = "reservation.delay";
    public static final String RK_RESERVATION_EXPIRE = "reservation.expire";

    @Bean
    public TopicExchange ticketingExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", NOTIFICATION_DLQ)
                .build();
    }

    @Bean
    public Queue ticketQueue() {
        return QueueBuilder.durable(TICKET_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", TICKET_DLQ)
                .build();
    }

    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(EMAIL_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", EMAIL_DLQ)
                .build();
    }

    @Bean
    public Queue analyticsQueue() {
        return QueueBuilder.durable(ANALYTICS_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", ANALYTICS_DLQ)
                .build();
    }

    /**
     * Native TTL + DLX delay queue: messages sit here for 10 minutes then dead-letter
     * back to ticketing.exchange with routing key reservation.expire.
     */
    @Bean
    public Queue reservationDelayQueue(AppProperties appProperties) {
        long ttlMs = appProperties.getBooking().getReservationTtlSeconds() * 1000L;
        return QueueBuilder.durable(RESERVATION_DELAY_QUEUE)
                .withArgument("x-message-ttl", ttlMs)
                .withArgument("x-dead-letter-exchange", EXCHANGE)
                .withArgument("x-dead-letter-routing-key", RK_RESERVATION_EXPIRE)
                .build();
    }

    @Bean
    public Queue reservationExpireQueue() {
        return QueueBuilder.durable(RESERVATION_EXPIRE_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", RESERVATION_EXPIRE_DLQ)
                .build();
    }

    @Bean public Queue notificationDlq() { return QueueBuilder.durable(NOTIFICATION_DLQ).build(); }
    @Bean public Queue ticketDlq() { return QueueBuilder.durable(TICKET_DLQ).build(); }
    @Bean public Queue emailDlq() { return QueueBuilder.durable(EMAIL_DLQ).build(); }
    @Bean public Queue analyticsDlq() { return QueueBuilder.durable(ANALYTICS_DLQ).build(); }
    @Bean public Queue reservationExpireDlq() { return QueueBuilder.durable(RESERVATION_EXPIRE_DLQ).build(); }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue()).to(ticketingExchange()).with(RK_NOTIFICATION);
    }

    @Bean
    public Binding ticketBinding() {
        return BindingBuilder.bind(ticketQueue()).to(ticketingExchange()).with(RK_TICKET);
    }

    @Bean
    public Binding emailBinding() {
        return BindingBuilder.bind(emailQueue()).to(ticketingExchange()).with(RK_EMAIL);
    }

    @Bean
    public Binding analyticsBinding() {
        return BindingBuilder.bind(analyticsQueue()).to(ticketingExchange()).with(RK_ANALYTICS);
    }

    @Bean
    public Binding reservationDelayBinding(AppProperties appProperties) {
        return BindingBuilder.bind(reservationDelayQueue(appProperties)).to(ticketingExchange()).with(RK_RESERVATION_DELAY);
    }

    @Bean
    public Binding reservationExpireBinding() {
        return BindingBuilder.bind(reservationExpireQueue()).to(ticketingExchange()).with(RK_RESERVATION_EXPIRE);
    }

    @Bean public Binding notificationDlqBinding() { return BindingBuilder.bind(notificationDlq()).to(deadLetterExchange()).with(NOTIFICATION_DLQ); }
    @Bean public Binding ticketDlqBinding() { return BindingBuilder.bind(ticketDlq()).to(deadLetterExchange()).with(TICKET_DLQ); }
    @Bean public Binding emailDlqBinding() { return BindingBuilder.bind(emailDlq()).to(deadLetterExchange()).with(EMAIL_DLQ); }
    @Bean public Binding analyticsDlqBinding() { return BindingBuilder.bind(analyticsDlq()).to(deadLetterExchange()).with(ANALYTICS_DLQ); }
    @Bean public Binding reservationExpireDlqBinding() { return BindingBuilder.bind(reservationExpireDlq()).to(deadLetterExchange()).with(RESERVATION_EXPIRE_DLQ); }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
