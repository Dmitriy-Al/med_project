package com.dev.mproject.model;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class TablesCreator {

    @Autowired
    public GoogleCalendarRepository googleCalendarRepository;

    @Autowired
    public UserRepository userRepository;


    private long patientPhoneNumber;
    private String doctorFirstname;
    private String doctorLastname;
    private String doctorPatronymic;
    private String patientFirstname;
    private String patientLastname;
    private String patientPatronymic;
    private String calendarScheduleText;

    public void create() {
        String createText = "Жопин Аркадий Кешевич 10.30 осмотр Головач Лена Анусовна 89213332211";

        List<String> wordsList = Arrays.asList(createText.split(" "));

        calendarScheduleText = createText;
        doctorLastname = wordsList.get(0);
        doctorFirstname = wordsList.get(1);
        doctorPatronymic = wordsList.get(2);
        patientLastname = wordsList.get(5);
        patientFirstname = wordsList.get(6);
        patientPatronymic = wordsList.get(7);
        patientPhoneNumber = Long.parseLong(wordsList.get(8));

        GoogleCalendar googleCalendar = new GoogleCalendar();
        googleCalendar.addCalendarTable(patientPhoneNumber, calendarScheduleText);
        googleCalendarRepository.save(googleCalendar);

        if (userRepository.findById(patientPhoneNumber).isEmpty()) {
            User user = new User();
            userRepository.save(user);
        }



    }


    public void add(GoogleCalendarRepository repository, long patientPhoneNumber, String calendarScheduleText){
        GoogleCalendar googleCalendar = new GoogleCalendar();
        googleCalendar.addCalendarTable(patientPhoneNumber, calendarScheduleText);
        repository.save(googleCalendar);
        System.out.println("test creator - ебучая таблица была создана");
    }
}
