package com.dev.mproject.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
@Entity(name="googleCalendarTable")
public class GoogleCalendar {

    @Id
    private long patientPhoneNumber;
    private String calendarScheduleText;


    public void addCalendarTable(long patientPhoneNumber, String calendarScheduleText){
        this.patientPhoneNumber = patientPhoneNumber;
        this.calendarScheduleText = calendarScheduleText;

    }

}
