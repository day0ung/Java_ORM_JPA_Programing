package com.http.connection.net.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpClientConfiguration {
    @Value("${proxy.group-names:}")
    private String groupNames;

}
