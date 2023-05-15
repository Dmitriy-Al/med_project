package com.dev.mproject.model;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import java.sql.Timestamp;

@Data
@Entity(name="userTable")
public class User {

    @Id
    private long chatId;
    private String firstname;
    private String lastname;
    private String patronymic;
    private String userName;
    private Timestamp registeredAt;
    private boolean isDoctor;
    private long phoneNumber;

    public long getChatId() {
        return chatId;
    }

    public void setChatId(long chatId) {
        this.chatId = chatId;
    }

    public String getFirstname() {
        return firstname;
    }

    public void setFirstname(String firstname) {
        this.firstname = firstname;
    }

    public String getLastname() {
        return lastname;
    }

    public void setLastname(String lastname) {
        this.lastname = lastname;
    }

    public String getPatronymic() {
        return patronymic;
    }

    public void setPatronymic(String patronymic) {
        this.patronymic = patronymic;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Timestamp getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(Timestamp registeredAt) {
        this.registeredAt = registeredAt;
    }

    public boolean getIsDoctor() {
        return isDoctor;
    }

    public void setIsDoctor(boolean doctor) {
        isDoctor = doctor;
    }

    public long getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(long phoneNumber) {
        this.phoneNumber = phoneNumber;
    }


    @Override
    public String toString() {
        return lastname + " " + firstname + " " + patronymic + " " + phoneNumber + "  зарегистрирован:" + registeredAt +
                "  является ли доктором:" + isDoctor + "  id:" + chatId + "  userName:" + userName + "> ";

    }

}
