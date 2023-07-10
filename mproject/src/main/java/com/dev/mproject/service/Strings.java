package com.dev.mproject.service;

public class Strings {
    public final static char pointer = 10148;
    public final static String REGISTER = "@reg";
    public final static String REG_AS_DOCTOR = "@rad";
    public final static String REG_AS_PATIENT = "@rap";
    public final static String DO_NOT_REGISTER = "@dnr";
    public final static String BUTTON_FOR_DOCTOR = "@bfd";
    public final static String I_AM_SURE = "%iamsure";
    public final static String YES_BUTTON = "%yesbutton";
    public final static String CHOOSE_DAY = "%chooseday";
    public final static String EDITE_DOCTOR = "%editedoc";
    public final static String TIME_BUTTON = "%timebutton";
    public final static String DATE_BUTTON = "%datebutton";
    public final static String SAVE_SCHEDULE = "%schedule";
    public final static String CHOOSE_TIME = "%choosetime";
    public final static String CHOOSE_DOCTOR = "%choosedoc";
    public final static String DELETE_DOCTOR = "%deletedoc";
    public final static String SET_VACATION = "%setvacation";
    public final static String I_AM_NOT_SURE = "%iamnotsure";
    public final static String USER_MESSAGE = "%usermessage";
    public final static String OBSERVE_SCHEDULE = "%observe";
    public final static String CANCEL_BUTTON = "%cancelbutton";
    public final static String ALL_USER_LIST = "%alluserslist";
    public final static String FOUND_PATIENT = "%foundpatient";
    public final static String CHOOSE_ANOTHER = "%chooseanother";
    public final static String INFO_ABOUT_USER = "%infoaboutuser";
    public final static String DELETE_VACATION = "%deletevacation";
    public final static String FOR_ALL_USER_MESSAGE = "%forallusersmessage";
    public final static String COMMAND_DOES_NOT_EXIST = "Такая команда отсутствует";

    public final static String INTRODUCE_TEXT = "Введите свои данные следуя указаниям бота. Если в процессе ввода была допущена ошибка, завершите регистрацию, а затем удалите учётную запись " +
            "с помощью команды /deletedata и повторите заново процесс регистрации, используя команду /start " +
            "\nПожалуйста обратите внимание на то, что при вводе пользовательских данных буква <ё> прописывается как <е> \n\n\nВведите вашу фамилию и отправьте сообщение";

    public final static String MENU_DOCTOR_SCHEDULE_TEXT = "Меню редактирования расписания врача\n\n" + pointer + " Выбор дня недели - установка рабочих дней недели врача, устанавливаются только те дни, в которые происходит приём пациентов\n" + pointer +
            " Сохранить настройки расписания рабочей недели - клавиша нажимается после установки всех рабочих дней недели врача. Внимание - всегда необходимо сохранять вновь созданное расписание. Все " +
            "дни недели, для которых не было установлено время, сохраняются как не рабочие\n" + pointer + " Добавить дату отпуска - добавление даты отпуска/отгула/больничного. Внимание - во избежание " +
            "регистрации пациентов в период отпуска/отгула/больничного врача, необходимо своевременно добавлять в программу эти данные\n" + pointer + " Удалить даты отпуска - удаляет все " +
            "записи об отпусках/отгулах/больничных врача. Рекомендуется своевременно удалять записи о закончившихся отпусках для избежания путаницы в рабочем графике\n\nВыбор дня недели:";

    public final static String PATIENT_MENU_TEXT = "Меню для работы с зарегистрированными пользователями\n\n" + pointer +
            " Список всех пациентов - получить список с полными данными всех зарегистрированных пользователей\n" +
            pointer + " Написать сообщение пациенту - отправка сообщения пациенту, выбранному из списка\n" +
            pointer + " Сообщение для всех пациентов - отправка сообщения ВСЕМ зарегистрированным пользователям\n" +
            pointer + " Посмотреть информацию о пациенте - полная информация (телефонный номер, фио, дата регистрации\n";

}
