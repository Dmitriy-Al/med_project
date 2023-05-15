package com.dev.mproject.model;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class TablesCreator {

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


        if (userRepository.findById(patientPhoneNumber).isEmpty()) {
            User user = new User();
            userRepository.save(user);
        }

    }

}
