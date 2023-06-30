package com.dev.mproject.model;

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


    private static final String APPLICATION_NAME = "Google Calendar API";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    public void setToCalendar(long time, String doctorAndPatientNames) throws IOException, GeneralSecurityException {
        // Load credentials from a file
        GoogleCredential credential = GoogleCredential.fromStream(new FileInputStream("C:\\api service key.json")) // "C:\\Users\\admit\\OneDrive\\Рабочий стол\\valiant.json"
                .createScoped(List.of("https://www.googleapis.com/auth/calendar"));

        // Build the Calendar service
        Calendar service = new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();

        // Create a new event
        Event event = new Event()
                .setSummary(doctorAndPatientNames)
                .setDescription("Пациент записался на приём в приложении");

        // Set the start time of the event
        Date date = new Date();
        Date startDate = new Date(date.getTime() + time);
        EventDateTime start = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(startDate))
                .setTimeZone("UTC");
        event.setStart(start);

        // Set the end time of the event
        Date endDate = new Date(startDate.getTime() + 5400000); // 1 hour after start time
        EventDateTime end = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(endDate))
                .setTimeZone("UTC");
        event.setEnd(end);


        // Insert the event into the calendar
        service.events().insert("alimov.developer@gmail.com", event).execute(); //TODO ВАЖНО! - "alimov.developer@gmail.com" - идентификатор календаря

    }
}