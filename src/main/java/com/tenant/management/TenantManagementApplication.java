package com.tenant.management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class TenantManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(TenantManagementApplication.class, args);
    }
}
