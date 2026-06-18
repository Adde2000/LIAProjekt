package se.liaprojekt.config;

import com.azure.core.amqp.AmqpTransportType;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Service Bus konfiguration med connection string.
 */
@Configuration
public class ServiceBusProducerConfig {

    @Value("${spring.cloud.azure.servicebus.connection-string}")
    private String connectionString;

    @Value("${spring.cloud.azure.servicebus.queue-name}")
    private String queueName;

    /**
     * Sender (producer)
     */
    @Bean
    public ServiceBusSenderClient senderClient() {

        return new ServiceBusClientBuilder()
                .connectionString(connectionString)
                .transportType(AmqpTransportType.AMQP_WEB_SOCKETS)
                .sender()
                .queueName(queueName)
                .buildClient();
    }
}