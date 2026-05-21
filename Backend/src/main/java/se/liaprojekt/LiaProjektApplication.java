package se.liaprojekt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LiaProjektApplication {

    public static void main(String[] args) {
        SpringApplication.run(LiaProjektApplication.class, args);
    }

}
