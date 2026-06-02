package se.liaprojekt.config;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureOpenAiConfig {

    @Value("${azure.openai.endpoint}")
    private String endpoint;

    @Bean
    public OpenAIClient openAIClient() {

        return new OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(
                        new DefaultAzureCredentialBuilder()
                                .build()
                )
                .buildClient();
    }
}