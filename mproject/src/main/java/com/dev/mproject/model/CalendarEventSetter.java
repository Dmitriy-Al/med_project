package com.dev.mproject.model;

import com.dev.mproject.config.Config;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.List;

public class CalendarEventSetter {
    /**
     * Метод setToCalendar() получает параметры - время события и фио врача и пациента, создаёт событие и помещает
     * его в Google Calendar
     */
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    public void setToCalendar(long time, String doctorAndPatientNames) throws IOException, GeneralSecurityException {

        GoogleCredential credential = GoogleCredential.fromStream(new FileInputStream(Config.PATH_FOR_IPI_SERVICE_KEY))
                .createScoped(List.of("https://www.googleapis.com/auth/calendar"));

        Calendar service = new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(Config.APPLICATION_NAME)
                .build();

        // Создание нового события
        Event event = new Event()
                .setSummary(doctorAndPatientNames)
                .setDescription("Пациент записался на приём в приложении");
        // Время начала события
        Date date = new Date();
        Date startDate = new Date(date.getTime() + time);
        EventDateTime start = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(startDate))
                .setTimeZone("UTC");
        event.setStart(start);

        // Время окончания события + 1.5. часа после времени начала
        Date endDate = new Date(startDate.getTime() + 5400000);
        EventDateTime end = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(endDate))
                .setTimeZone("UTC");
        event.setEnd(end);
        // Добавление события в календарь
        service.events().insert(Config.CALENDAR_ID, event).execute();
    }
}