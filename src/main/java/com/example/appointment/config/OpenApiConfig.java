package com.example.appointment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI appointmentOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Appointment Management System API")
                        .version("1.0.0")
                        .description("""
                                REST API for a professional services firm.
                                Providers publish availability slots; customers search available
                                slots and manage the full appointment lifecycle.
                                """));
    }
}
