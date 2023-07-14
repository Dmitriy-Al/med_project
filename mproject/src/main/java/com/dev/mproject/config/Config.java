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
    public String adminValidation; // строка с фамилией, именем, отчеством администраторской учётной записи для регистрации пользователя как администратора
    public final static String CLIENT_ID = "1018072133282-bbpievlb23diem0rfur38d66sjsc5hs9.apps.googleusercontent.com"; // client id из Google API credentials
    public final static String CLIENT_SECRET = "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"; // client secret из Google API credentials
    public final static String AUTHORIZATION_CODE = "mpro"; // поле устанавливается вручную, содержит любое уникальное значение
    public final static String CALENDAR_ID = "alimov.developer@gmail.com"; // calendar id получен из настроек Google calendar
    public final static String APPLICATION_NAME = "Google Calendar API";
    public final static String PATH_FOR_IPI_SERVICE_KEY = "C:\\api service key.json"; // путь к API service key
    public final static String TIME_SETTING = "0 0 14 * * *"; // установка времени суток, в которое происходит оповещение пациента перед приёмом у врача TODO поменять на нужные

}
