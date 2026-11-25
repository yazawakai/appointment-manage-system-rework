package com.example.appointment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring Boot 3.x no longer matches trailing slashes by default (Spring Framework 6
 * removed the implicit {@code useTrailingSlashMatch} behaviour). As a result
 * {@code POST /api/appointments/} would return 404 even though {@code /api/appointments}
 * is mapped. Re-enable optional trailing-slash matching so clients that append a trailing
 * slash (a common default in Postman collections) keep working.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    @SuppressWarnings("deprecation")
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.setUseTrailingSlashMatch(true);
    }
}
