package com.dev.mproject.model;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import com.dev.mproject.config.GoogleAPIConnector;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;

public class GoogleCalendarReceiver {

    private List<Event> eventList;
    private String doctorSchedule;
    private String patientSchedule;


    public void getCalendarInfo() throws IOException, GeneralSecurityException {
        GoogleAPIConnector connector = new GoogleAPIConnector(); // Соединение с GoogleCalendar

        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = connector.getCredentials(HTTP_TRANSPORT);
        Calendar service = new Calendar.Builder(HTTP_TRANSPORT, connector.getJsonFactory(), credential)
                .setApplicationName(connector.getApplicationName())
                .build();
        Events events = service.events().list("primary")
                .setMaxResults(1000) // максимальное число заметок календаря, которые могут быть сохранены в коллекции List<Event> eventList
                .setTimeMin(new DateTime(System.currentTimeMillis()))
                .setOrderBy("startTime") // сортировка заметок по времени события
                .setSingleEvents(true)
                .execute();
        eventList = events.getItems();
    }


    public void receiveSchedule(String fullName) {
        try {
            this.getCalendarInfo();
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }

        if (!(eventList.isEmpty())) { // если коллекция List<Event> eventList не пуста, в цикле for эвенты календаря разбираются на фио врача, пациента и дату события
            StringBuilder doctorScheduleText = new StringBuilder();
            StringBuilder patientScheduleText = new StringBuilder();

            for (Event event : eventList) {
                DateTime eventTime = event.getStart().getDateTime(); // полная дата события с минутами, секундами, мс
                if (eventTime == null) {
                    eventTime = event.getStart().getDate();
                }
                String[] text = event.getSummary().split("-");
                String[] time = eventTime.toString().replace('T', ' ').replace(':', ' ').split(" "); // разбор строки даты и времени на составляющие

                String doctorFullName = text[0].trim(); // фио врача
                String patientFullName = text[1].trim(); // фио доктора
                String date = time[0]; // только дата события
                String hourAndMinute = time[1] + ":" + time[2]; // только часы и минуты события

                if (doctorFullName.equalsIgnoreCase(fullName)) {
                    doctorScheduleText.append(date).append(" в ").append(hourAndMinute).append(";  пациент ").append(patientFullName).append("\n"); // заполнение списка событий доктора
                } else if (patientFullName.equalsIgnoreCase(fullName)) {
                    patientScheduleText.append("у вас запланирован приём ").append(date).append(" в ").append(hourAndMinute) // заполнение списка событий пациента
                            .append(";  ваш доктор ").append(doctorFullName).append("\n");
                }
            }
            // сообщение списка событий доктора и пациента
            if(!doctorScheduleText.isEmpty())  doctorSchedule = doctorScheduleText.toString();
            if(!patientScheduleText.isEmpty())  patientSchedule = patientScheduleText.toString();

        } else { // сообщение, если список событий пуст
            patientSchedule = "список пациентов отсутствует";
            doctorSchedule = "список пациентов отсутствует";
        }
    }


    public String getPatientSchedule(){
        if(patientSchedule != null){
            return patientSchedule;
        }
        else return "У вас отсутствует запись к врачу";
    }

    public String getDoctorSchedule(){
        if(doctorSchedule != null){
            return doctorSchedule;
        }
        else return "У вас отсутствует запись к врачу";
    }

}


/*
    public void receiveDoctorSchedule(String doctorFullName) {
        try {
            this.getCalendarInfo();
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }

        if (!(eventList.isEmpty())) {
            StringBuilder stringBuilder = new StringBuilder();

            for (Event event : eventList) {
                DateTime eventTime = event.getStart().getDateTime();
                if (eventTime == null) {
                    eventTime = event.getStart().getDate();
                }
                String[] text = event.getSummary().split("-");
                String[] time = eventTime.toString().replace('T', ' ').replace(':', ' ').split(" ");

                String doctorName = text[0].trim();
                String patientName = text[1].trim();
                String date = time[0];
                String hourAndMinute = time[1] + ":" + time[2];

                if (doctorName.equalsIgnoreCase(doctorFullName)) {
                    stringBuilder.append(date).append(", в ").append(hourAndMinute).append(";  пациент ").append(patientName).append("\n");
                }
            }
            if(!stringBuilder.isEmpty())  doctorSchedule = stringBuilder.toString();

        } else doctorSchedule = "список пациентов отсутствует";
    }



    public void receivePatientSchedule(String patientFullName) {
        try {
            this.getCalendarInfo();
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
        if (!(eventList.isEmpty())) {
            StringBuilder stringBuilder = new StringBuilder();
            for (Event event : eventList) {
                DateTime eventTime = event.getStart().getDateTime();
                if (eventTime == null) {
                    eventTime = event.getStart().getDate();
                }
                String[] text = event.getSummary().split("-");
                String[] time = eventTime.toString().replace('T', ' ').replace(':', ' ').split(" ");

                String patientName = text[1].trim();
                String doctorName = text[0].trim();
                String date = time[0];
                String hourAndMinute = time[1] + ":" + time[2];

                if (patientName.equalsIgnoreCase(patientFullName)) {
                    stringBuilder.append("у вас запланирован приём ").append(date).append(", в ").append(hourAndMinute)
                            .append("; ваш доктор ").append(doctorName).append("\n");
                }
            }
            if(!stringBuilder.isEmpty())  patientSchedule = stringBuilder.toString();

        } else patientSchedule = "список пациентов отсутствует";
    }

*/