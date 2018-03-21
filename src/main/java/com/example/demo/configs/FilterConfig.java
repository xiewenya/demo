package com.example.demo.configs;

import com.example.demo.filter.PathInfoFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.Filter;

@Configuration
public class FilterConfig {
    @Bean
    public FilterRegistrationBean someFilterRegistration() {

        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(pathInfoFilter());
        registration.addUrlPatterns("/*");
        registration.setName("pathInfoFilter");
        registration.setOrder(1);
        return registration;
    }

    public Filter pathInfoFilter() {
        return new PathInfoFilter();
    }
}
