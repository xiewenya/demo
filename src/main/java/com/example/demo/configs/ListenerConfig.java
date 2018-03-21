package com.example.demo.configs;

import org.opensolaris.opengrok.web.WebappListener;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author bresai
 */

@Configuration
public class ListenerConfig {
    @Bean
    public WebappListener webappListener(){
        return new WebappListener();
    }


    @Bean
    public ServletContextInitializer initializer() {
        return servletContext -> {
            servletContext.setInitParameter("CONFIGURATION", "/opt/opengrok/etc/configuration.xml");
            servletContext.setInitParameter("ConfigAddress", "localhost:9999");
        };
    }
}
