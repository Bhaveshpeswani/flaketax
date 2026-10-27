package com.flaketax;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FlakeTaxApp {
    public static void main(String[] args){
        SpringApplication.run(FlakeTaxApp.class,args);

    }
}
