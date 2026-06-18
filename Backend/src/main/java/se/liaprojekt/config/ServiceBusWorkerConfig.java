package se.liaprojekt.config;

import com.azure.core.amqp.AmqpTransportType;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import se.liaprojekt.worker.EmailMessageHandler;

@Configuration
public class ServiceBusWorkerConfig {

    @Value("${spring.cloud.azure.servicebus.connection-string}")
    private String connectionString;

    @Value("${spring.cloud.azure.servicebus.queue-name}")
    private String queueName;

    /**
     * Consumer / Worker client
     */
    @Bean
    public ServiceBusProcessorClient processorClient(EmailMessageHandler handler) {

        return new ServiceBusClientBuilder()
                .connectionString(connectionString)
                .transportType(AmqpTransportType.AMQP_WEB_SOCKETS)
                .processor()
                .queueName(queueName)
                .processMessage(handler::handleMessage)
                .processError(handler::handleError)
                .buildProcessorClient();
    }
}