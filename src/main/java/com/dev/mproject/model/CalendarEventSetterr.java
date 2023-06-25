package com.dev.mproject.model;

import com.dev.mproject.config.GoogleAPIConnector;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class CalendarEventSetterr {// TODO: попытка использовать код гугл
    /*

    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT)
            throws IOException {
        // Load client secrets.
        InputStream inputStream = CalendarEventSetterr.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (inputStream == null) {
            throw new FileNotFoundException("Resource not found ble: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(inputStream));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("use");
        //returns an authorized Credential object.
        return credential;
    }

    public static void main(String... args) throws IOException, GeneralSecurityException {
        // Build a new authorized API client service.
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Calendar service = new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();

        // List the next 10 events from the primary calendar.



        // Create a new event
        Event event = new Event()
                .setSummary("TEEEEEEEEEEEEST")
                .setDescription("Пациент записался на приём в приложении");

        // Set the start time of the event
        Date date = new Date();
        Date startDate = new Date(date.getTime() + 3600000);
        EventDateTime start = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(startDate))
                .setTimeZone("UTC");
        event.setStart(start);

        // Set the end time of the event
        Date endDate = new Date(startDate.getTime() + 3600000 + 3600000); // 1 hour after start time
        EventDateTime end = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(endDate))
                .setTimeZone("UTC");
        event.setEnd(end);


        // Insert the event into the calendar
        service.events().insert("alimov.developer@gmail.com", event).execute();System.out.println("Event created: " + event.toString()); //TODO ВАЖНО! - "alimov.developer@gmail.com" - идентификатор календаря
        System.out.println("Event 1 created: " + event.getStatus());



    }




        // List the next 10 events from the primary calendar.
        DateTime now = new DateTime(System.currentTimeMillis());
        Events events = service.events().list("primary")
                .setMaxResults(10)
                .setTimeMin(now)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute();
        List<Event> items = events.getItems();
        if (items.isEmpty()) {
            System.out.println("No upcoming events found.");
        } else {
            System.out.println("Upcoming events");
            for (Event event : items) {
                DateTime start = event.getStart().getDateTime();
                if (start == null) {
                    start = event.getStart().getDate();
                }
                System.out.printf("%s (%s)\n", event.getSummary(), start);
            }
        }






    private static final String APPLICATION_NAME = "Google Calendar API";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    public void setToCalendar(long time, String doctorAndPatientNames) throws IOException, GeneralSecurityException {





        System.out.println("TEEEEEEEEEEEEST");
        // Load credentials from a file
        GoogleCredential credential = GoogleCredential.fromStream(new FileInputStream("C:\\Users\\admit\\OneDrive\\Рабочий стол\\valiant.json"))
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
        Date endDate = new Date(startDate.getTime() + 3600000); // 1 hour after start time
        EventDateTime end = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(endDate))
                .setTimeZone("UTC");
        event.setEnd(end);


        // Insert the event into the calendar
    service.events().insert("alimov.developer@gmail.com", event).execute();System.out.println("Event created: " + event.toString()); //TODO ВАЖНО! - "alimov.developer@gmail.com" - идентификатор календаря
        System.out.println("Event 1 created: " + event.getStatus());




*/


    }

