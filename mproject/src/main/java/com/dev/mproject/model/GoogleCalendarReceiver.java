package com.dev.mproject.model;

import com.dev.mproject.config.GoogleAPIConnector;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.TreeMap;

@Slf4j
public class GoogleCalendarReceiver {

    private List<Event> eventList;

    // Соединение с GoogleCalendar
    private void getCalendarInfo() throws IOException, GeneralSecurityException {
        GoogleAPIConnector connector = new GoogleAPIConnector();

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

    // Получение событий из Google Calendar
    public TreeMap<String, String> receiveFromGoogleCalendar(String userData) {
        try {
            this.getCalendarInfo();
        } catch (IOException e) {
            log.error("SendMessage execute error: " + e.getMessage());
        } catch (GeneralSecurityException exc) {
            log.error("SendMessage execute error: " + exc.getMessage());
            throw new RuntimeException(exc);
        }
        // Первая часть строки userData - фио пользователя, вторая часть строки - информация от кого (доктор, админ, пациент) исходит запрос
        String[] fullNameAndStatus = userData.split("-");
        TreeMap<String, String> dateTime = new TreeMap<>();
        // если коллекция List<Event> eventList не пуста, в цикле for события из календаря разбираются на фио врача, пациента и дату события
        if (!(eventList.isEmpty())) {
            StringBuilder doctorScheduleTime = new StringBuilder();

            for (Event event : eventList) {
                DateTime eventTime = event.getStart().getDateTime(); // полная дата события с минутами, секундами, мс
                if (eventTime == null) {
                    eventTime = event.getStart().getDate();
                }

                String[] text = event.getSummary().split("-");
                String[] time = eventTime.toString().replace('T', ' ').replace(':', ' ').split(" ");

                String doctorFullName = text[0].trim(); // фио врача
                String patientFullName = text[1].trim(); // фио пациента
                String date = time[0]; // дата события
                String hourAndMinute = time[1] + ":" + time[2] + " "; // часы и минуты события

                if(fullNameAndStatus[0].equalsIgnoreCase(doctorFullName) && fullNameAndStatus[1].equals("doctor")) {
                    if (dateTime.get(date) == null){
                        doctorScheduleTime.setLength(0);
                        dateTime.put(date, hourAndMinute);
                    } else {
                        dateTime.put(date, hourAndMinute);
                    }
                    doctorScheduleTime.append(dateTime.get(date));
                    dateTime.put(date, doctorScheduleTime.toString());

                } else if (fullNameAndStatus[0].equalsIgnoreCase(doctorFullName) && fullNameAndStatus[1].equals("doctorschadule")){
                    if (dateTime.get(date) == null){
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


