package com.dev.mproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MprojectApplication {

	public static void main(String[] args) {


		SpringApplication.run(MprojectApplication.class, args);


	}
}

/*

 private void doctorScheduleHoursButtonss(String choseDate, long chatId, long messageId) {/////////////////////////////////////
        LocalDate localDate = LocalDate.parse(choseDate);
        DateTimeFormatter dayOfWeek = DateTimeFormatter.ofPattern("e");
        googleCalendar = new GoogleCalendarReceiver();

        String fullName = tempChooseDoctor.get(chatId).getDoctorLastname() + " " + tempChooseDoctor.get(chatId).getDoctorFirstname() + " " + tempChooseDoctor.get(chatId).getDoctorPatronymic();

        HashMap<String, String> googleCalendarSchedule = googleCalendar.receiveFromGoogleCalendar(fullName);

        String calendarTime = googleCalendarSchedule.get(choseDate);
        String[] hours = new String[0];

        if (calendarTime != null) {
            hours = calendarTime.split(" ");
        }

        String[] workTime = new String[2];

        if (dayOfWeek.format(localDate).equals("1")) {
            workTime = tempChooseDoctor.get(chatId).getMondaySchedule().split("-");
        } else if (dayOfWeek.format(localDate).equals("2")) {
            workTime = tempChooseDoctor.get(chatId).getTuesdaySchedule().split("-");
        } else if (dayOfWeek.format(localDate).equals("3")) {
            workTime = tempChooseDoctor.get(chatId).getWednesdaySchedule().split("-");
        } else if (dayOfWeek.format(localDate).equals("4")) {
            workTime = tempChooseDoctor.get(chatId).getThursdaySchedule().split("-");
        } else if (dayOfWeek.format(localDate).equals("5")) {
            workTime = tempChooseDoctor.get(chatId).getFridaySchedule().split("-");
        } else if (dayOfWeek.format(localDate).equals("6")) {
            workTime = tempChooseDoctor.get(chatId).getSaturdaySchedule().split("-");
        } else if (dayOfWeek.format(localDate).equals("7")) {
            workTime = tempChooseDoctor.get(chatId).getSundaySchedule().split("-");
        }

        int beginOfWork = Integer.parseInt(workTime[0]);
        int endOfWork = Integer.parseInt(workTime[1]);

        EditMessageText editMessageText = receiveEditMessageText(chatId, messageId, "Выберите время для записи");

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>(); // коллекция коллекций с горизонтальным рядом кнопок, создаёт вертикальный ряд кнопок
        List<InlineKeyboardButton> firstRowInlineButton = new ArrayList<>(); // коллекция с горизонтальным рядом кнопок
        List<InlineKeyboardButton> secondRowInlineButton = new ArrayList<>();

        int iteration = 0;

        for (int i = beginOfWork; i < endOfWork; i++) {
            if (hours.length == iteration) {
                KeyboardWrapper keyboardWrapper = new KeyboardWrapper();
                keyboardWrapper.setButtonSetText(i + ":00");
                if (i < 10) { // для правильного вычисления миллисекунд
                    keyboardWrapper.setButtonSetCallbackText("%timebutton" + choseDate + "T0" + i);
                } else keyboardWrapper.setButtonSetCallbackText("%timebutton" + choseDate + "T" + i);

                firstRowInlineButton.add(keyboardWrapper.getKeyboardButton());
            } else if (i != Integer.parseInt(hours[iteration].trim().replaceAll(":00", ""))) {
                KeyboardWrapper keyboardWrapper = new KeyboardWrapper();
                keyboardWrapper.setButtonSetText(i + ":00");
                if (i < 10) { // для правильного вычисления миллисекунд
                    keyboardWrapper.setButtonSetCallbackText("%timebutton" + choseDate + "T0" + i);
                } else keyboardWrapper.setButtonSetCallbackText("%timebutton" + choseDate + "T" + i);

                firstRowInlineButton.add(keyboardWrapper.getKeyboardButton());
            } else {
                iteration++;
            }
        }

        InlineKeyboardButton secondButton = new InlineKeyboardButton();
        secondButton.setText("Выбрать другую дату");
        secondButton.setCallbackData("%chooseanother");
        secondRowInlineButton.add(secondButton);

        rowsInline.add(firstRowInlineButton); // размещение набора кнопок в ряду
        rowsInline.add(secondRowInlineButton);
        inlineKeyboardMarkup.setKeyboard(rowsInline);
        editMessageText.setReplyMarkup(inlineKeyboardMarkup);

        editMessageTextExecute(editMessageText);
    }




    private void createTimeButtonsForDoctor(long chatId, long messageId, String firstNameAndLastName, String date) {
        EditMessageText editMessageText = receiveEditMessageText(chatId, messageId, "Выберите время");
        Doctor doctor = receiveDoctorFromDb(firstNameAndLastName);
        String doctorFullName = doctor.getDoctorLastname() + " " + doctor.getDoctorFirstname() + " " + doctor.getDoctorPatronymic();
        GoogleCalendarReceiver calendarReceiver = new GoogleCalendarReceiver();

        TreeMap<String, String> dateTime = calendarReceiver.receiveFromGoogleCalendar(doctorFullName);

        String[] time = dateTime.get(date).split(" ");

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>(); // коллекция коллекций с горизонтальным рядом кнопок, создаёт вертикальный ряд кнопок
        List<InlineKeyboardButton> firstRowInlineButton = new ArrayList<>();
        List<InlineKeyboardButton> secondRowInlineButton = new ArrayList<>();
        List<InlineKeyboardButton> thirdRowInlineButton = new ArrayList<>();
        List<InlineKeyboardButton> fourthRowInlineButton = new ArrayList<>();

        for (int i = 0; i < time.length; i++) { // Динамически расширяемое меню выбора даты
            String buttonText = time[i];
            KeyboardWrapper keyboardWrapper = new KeyboardWrapper();
            keyboardWrapper.setButtonSetText(buttonText);
            keyboardWrapper.setButtonSetCallbackText("#t" + " " + time[i] + " " + date + " " + firstNameAndLastName);
            if (i < 5) {
                firstRowInlineButton.add(keyboardWrapper.getKeyboardButton());
            } else if (i < 10) {
                secondRowInlineButton.add(keyboardWrapper.getKeyboardButton());
            } else if (i < 15) {
                thirdRowInlineButton.add(keyboardWrapper.getKeyboardButton());
            } else if (i < 20) {
                fourthRowInlineButton.add(keyboardWrapper.getKeyboardButton());
            }
        }

        rowsInline.add(firstRowInlineButton); // размещение набора кнопок в вертикальном ряду
        rowsInline.add(secondRowInlineButton); // размещение набора кнопок в вертикальном ряду
        rowsInline.add(thirdRowInlineButton); // размещение набора кнопок в вертикальном ряду
        rowsInline.add(fourthRowInlineButton); // размещение набора кнопок в вертикальном ряду

        inlineKeyboardMarkup.setKeyboard(rowsInline);
        editMessageText.setReplyMarkup(inlineKeyboardMarkup);
        editMessageTextExecute(editMessageText);
    }

 */