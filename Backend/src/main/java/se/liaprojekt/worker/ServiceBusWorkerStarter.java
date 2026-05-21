package se.liaprojekt.worker;

import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class ServiceBusWorkerStarter {

    private static final Logger log =
            LoggerFactory.getLogger(ServiceBusWorkerStarter.class);

    private final ServiceBusProcessorClient processorClient;

    public ServiceBusWorkerStarter(ServiceBusProcessorClient processorClient) {
        this.processorClient = processorClient;
    }

    @PostConstruct
    public void start() {
        try {
            log.info("🚀 Starting Service Bus worker in Azure...");

            processorClient.start();

            log.info("✅ Service Bus worker started successfully");

        } catch (Exception e) {
            log.error("❌ Failed to start Service Bus worker", e);
            throw e; // viktigt i Azure så App Service kan restartas
        }
    }

    @PreDestroy
    public void stop() {
        log.info("🛑 Stopping Service Bus worker...");
        processorClient.close();
    }
}