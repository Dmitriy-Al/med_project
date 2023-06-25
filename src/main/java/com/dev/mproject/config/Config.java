package com.dev.mproject.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling // аннотация автоматического запуска метода рассылки сообщений по таймеру
@Data
@PropertySource("application.properties")
public class Config {

    @Value("${bot.name}")
    public String BotName;

    @Value("${bot.token}")
    public String botToken;

    @Value("${admin.validation}")
    public String adminValidation;

}
