package com.hadivo.attendance.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitMqConfig(private val props: AppProperties) {

    @Bean
    fun attendanceExchange(): TopicExchange = TopicExchange(props.messaging.exchange, true, false)

    @Bean
    fun notificationQueue(): Queue = Queue(props.messaging.notificationQueue, true)

    @Bean
    fun notificationEventsQueue(): Queue = Queue(props.messaging.notificationEventsQueue, true)

    @Bean
    fun notificationBinding(@Qualifier("notificationQueue") queue: Queue, exchange: TopicExchange): Binding =
        BindingBuilder.bind(queue).to(exchange).with("attendance.#")

    @Bean
    fun notificationEventsBinding(
        @Qualifier("notificationEventsQueue") notificationEventsQueue: Queue,
        exchange: TopicExchange,
    ): Binding =
        BindingBuilder.bind(notificationEventsQueue).to(exchange).with(props.messaging.notificationRoutingKey)

    @Bean
    fun messageConverter(objectMapper: ObjectMapper): MessageConverter =
        Jackson2JsonMessageConverter(objectMapper)

    @Bean
    fun rabbitTemplate(cf: ConnectionFactory, converter: MessageConverter): RabbitTemplate {
        val template = RabbitTemplate(cf)
        template.messageConverter = converter
        return template
    }
}
