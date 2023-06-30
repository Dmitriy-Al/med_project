package com.dev.mproject.model;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@lombok.Setter
@lombok.Getter
@Entity(name="doctorTable")
public class Doctor implements Cloneable{


    @Id
    //@GeneratedValue(strategy = GenerationType.AUTO) не применима, поскольку при регистрации доктора необходимо устанавливать новый Id, аннотация этого не позволяет
    private long chatId;
    private String doctorFirstname;
    private String doctorLastname;
    private String doctorPatronymic;
    private String speciality;

    private String mondaySchedule;
    private String tuesdaySchedule;
    private String wednesdaySchedule;
    private String ThursdaySchedule;
    private String fridaySchedule;
    private String SaturdaySchedule;
    private String SundaySchedule;
    private String vacation;

    @Override
    public Object clone(){
        try {
            return super.clone();
        } catch (CloneNotSupportedException exc){
            log.error("SendMessage execute error: " + exc.getMessage());
        }
        return null;
    }

}
