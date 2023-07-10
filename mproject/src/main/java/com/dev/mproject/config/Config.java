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

    public final static String CLIENT_ID = "";

    public final static String CLIENT_SECRET = ""; // поле устанавливается вручную

    public final static String AUTHORIZATION_CODE = "mpro"; // поле устанавливается вручную, содержит любое уникальное значение//

    public final static String CALENDAR_ID = "";

    public final static String APPLICATION_NAME = "Google Calendar API";

    public final static String TIME_SETTING = "0 0 14 * * *"; // TODO поменять на нужные

}
