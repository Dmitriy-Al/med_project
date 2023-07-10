package com.dev.mproject.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.sql.Timestamp;

@lombok.Setter
@lombok.Getter
@Entity(name="userTable")
public class User {

    @Id
    private long chatId;
    private String firstname;
    private String lastname;
    private String patronymic;
    private String userName;
    private Timestamp registeredAt;
    private long phoneNumber;


    @Override
    public String toString() {
        char pointer = 10033;
        return pointer + " " +lastname + " " + firstname + " " + patronymic + "\nтелефон: " + phoneNumber + "\nзарегистрирован: " + registeredAt +
                ";   id: " + chatId + ";   userName: " + userName;
    }

}
