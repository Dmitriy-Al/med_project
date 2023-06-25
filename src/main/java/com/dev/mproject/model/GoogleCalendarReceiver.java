package com.dev.mproject.model;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

import com.dev.mproject.config.GoogleAPIConnector;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GoogleCalendarReceiver {
    public UserRepository userRepository;
    private List<Event> eventList;


    public void getCalendarInfo() throws IOException, GeneralSecurityException { // TODO сделать метод приватным
        GoogleAPIConnector connector = new GoogleAPIConnector(); // Соединение с GoogleCalendar

        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = connector.getCredentials(HTTP_TRANSPORT);
        Calendar service = new Calendar.Builder(HTTP_TRANSPORT, connector.getJsonFactory(), credential)
                .setApplicationName(connector.getApplicationName())
                .build();
        Events events = service.events().list("primary")
                .setMaxResults(10000) // максимальное число заметок календаря, которые могут быть сохранены в коллекции List<Event> eventList
                .setTimeMin(new DateTime(System.currentTimeMillis()))
                .setOrderBy("startTime") // сортировка заметок по времени события
                .setSingleEvents(true)
                .execute();
        eventList = events.getItems();
    }


    public TreeMap<String, String> receiveFromGoogleCalendar(String userData) {
        try {
            this.getCalendarInfo();
        } catch (IOException e) {
            log.error("SendMessage execute error: " + e.getMessage());
        } catch (GeneralSecurityException exc) {
            log.error("SendMessage execute error: " + exc.getMessage());
            throw new RuntimeException(exc);
        }

        String[] fullNameAndStatus = userData.split("-");
        TreeMap<String, String> dateTime = new TreeMap<>();

        if (!(eventList.isEmpty())) { // если коллекция List<Event> eventList не пуста, в цикле for эвенты календаря разбираются на фио врача, пациента и дату события
            StringBuilder doctorScheduleTime = new StringBuilder();

            for (Event event : eventList) {
                DateTime eventTime = event.getStart().getDateTime(); // полная дата события с минутами, секундами, мс
                if (eventTime == null) {
                    eventTime = event.getStart().getDate();
                }

                String[] text = event.getSummary().split("-");
                String[] time = eventTime.toString().replace('T', ' ').replace(':', ' ').split(" "); // разбор строки даты и времени на составляющие

                String doctorFullName = text[0].trim(); // фио врача
                String patientFullName = text[1].trim(); // фио пациента
                String date = time[0]; // только дата события
                String hourAndMinute = time[1] + ":" + time[2] + " "; // только часы и минуты события

                if(fullNameAndStatus[0].equalsIgnoreCase(doctorFullName) && fullNameAndStatus[1].equals("doctor")) {
                    if (dateTime.get(date) == null){ // логика заполнения Map
                        doctorScheduleTime.setLength(0);
                        dateTime.put(date, hourAndMinute);
                    } else {
                        dateTime.put(date, hourAndMinute);
                    }
                    doctorScheduleTime.append(dateTime.get(date));
                    dateTime.put(date, doctorScheduleTime.toString());

                } else if (fullNameAndStatus[0].equalsIgnoreCase(doctorFullName) && fullNameAndStatus[1].equals("doctorschadule")){
                    if (dateTime.get(date) == null){ // логика заполнения Map
                        doctorScheduleTime.setLength(0);
                        dateTime.put(date, hourAndMinute + "  пациент " + patientFullName + "-");
                    } else {
                        dateTime.put(date, hourAndMinute + "   пациент  " + patientFullName + "-");
                    }
                    doctorScheduleTime.append(dateTime.get(date));
                    dateTime.put(date, doctorScheduleTime.toString());

                } else if (fullNameAndStatus[0].equalsIgnoreCase(patientFullName) && fullNameAndStatus[1].equals("patient")){
                    doctorScheduleTime.append(doctorFullName).append(", приём запланирован ").append(date).append("  в ").append(hourAndMinute).append(";\n");
                    dateTime.put(patientFullName, doctorScheduleTime.toString());
                }
            }
        }
        return dateTime;
    }

}

