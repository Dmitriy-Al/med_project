package com.dev.mproject.service;

import com.dev.mproject.config.Config;
import com.dev.mproject.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    /**
     * Реализованы возможности:
     * регистрация пользователя в роли администратора, врвча, пациента (пользователя).
     * Учётная запись администратора позволяет добавлять врачей в базу данных,
     * устанавливать рабочий график врача, записывать к врачу пациентов.
     * Также администратор получает возможность просмотра базы пациентов, информации о
     * конкретном пациенте, отправки сообщений пациенту/всем зарегистрированным пациентам
     * Учётная запись врача может быть получена только с одобрения администратора и позволяет
     * просматривать врачу просматривать свою запись и рабочий график.
     * Учётная запись пациента (пользователя) позволяет пользователю записываться на приём к
     * врачу и просматривать свою запись, а так же получать сообщенния о предстоящем приёме.
     *
     * Разрешение коллизий при одновременной работе с приложением нескольких пользователей
     * реализовано с использованием HashMap, где ключом Long является chatId пользователя.
     * Поэтапные действия (ввод фио) и действия связанные с отправкой сообщений, для которых
     * отсутствуют зарезервированные строки-команды (вызывающие дефолт-сообщения программы
     * "Такая команда отсутствует"), осуществляются путём блокировки дефолтных сообщений
     * установкой не null значения map BLOCK_DEFAULT_MESSAGE_VALUE. Числовое значение также
     * определяет то, какой метод/методы будут вызваны в процессе выполнения команд исходящих
     * от пользователя.
     */

    @Autowired
    public UserRepository userRepository;
    private final Config config;
    private GoogleCalendarReceiver googleCalendar;
    private final DoctorRepository doctorRepository;
    private int tempDayOfWeek;
    private String tempData = null;

    private final HashMap<Long, String> LASTNAME = new HashMap<>();
    private final HashMap<Long, String> FIRSTNAME = new HashMap<>();
    private final HashMap<Long, String> PATRONYMIC = new HashMap<>();
    private final HashMap<Long, String> PHONE_NUMBER = new HashMap<>();
    private final HashMap<Long, Doctor> TEMP_CHOOSE_DOCTOR = new HashMap<>();
    private final HashMap<Integer, String> DOCTOR_SCHEDULE_LIST = new HashMap<>(8);
    private final HashMap<Long, String> AVOID_REGISTER_COLLISION = new HashMap<>(); // если дата и время записи к врачу уже находится в map, регистрация на это время становится невозможной
    private final HashMap<Long, Integer> BLOCK_DEFAULT_MESSAGE_VALUE = new HashMap<>(); // если значение != null, блокируется отправка сообщений COMMAND_DOES_NOT_EXIST и запускает методы соответствующие заданному числовому значению


    public TelegramBot(Config config, DoctorRepository doctorRepository) {
        super(config.botToken);
        this.config = config;
        this.doctorRepository = doctorRepository;
        // Команды меню
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "Меню регистрации пользователя"));
        listOfCommands.add(new BotCommand("/mydata", "Посмотреть данные пользователя"));
        listOfCommands.add(new BotCommand("/deletedata", "Удалить данные пользователя"));
        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("BotCommand execute error: " + e.getMessage());
        }
    }

    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            switch (messageText) {
                case "/start" -> { // запуск процесса регистрации пользователя
                    Optional<User> user = userRepository.findById(update.getMessage().getChatId());
                    Optional<Doctor> doctor = doctorRepository.findById(update.getMessage().getChatId());
                    if (user.isEmpty() && doctor.isEmpty()) {
                        BLOCK_DEFAULT_MESSAGE_VALUE.remove(chatId); // если по какой-то причине регистрация была прервана и начата заново, процесс начинается с начала
                        greetMessage(chatId);
                    } else {
                        sendMessageWithScreenKeyboard(chatId, "Вы являетесь зарегистрированным пользователем");
                    }
                }
                case "/mydata" -> { // информация о пользователе
                    Optional<User> user = userRepository.findById(chatId);
                    String userData = user.toString().replace("Optional[", "").replace("]", "");
                    sendMessageWithScreenKeyboard(chatId, userData);
                }
                case "/deletedata" -> { // удалить пользователя из бд
                    Optional<Doctor> doctor = doctorRepository.findById(chatId);
                    if (doctor.isPresent()) {
                        sendMessageWithScreenKeyboard(chatId, "Удалить учётную запись врача может только администратор");
                    } else {
                        Optional<User> user = userRepository.findById(chatId);
                        user.ifPresent(value -> userRepository.delete(value));
                        sendMessageExecute(receiveCreatedMessage(chatId, "Вся информация о пользователе была удалена"));
                    }
                }
                case "/settings" -> {
                    sendMessageWithScreenKeyboard(chatId, "Меню настроек для данного профиля отсутствует");
                }
                case "Записаться на приём к врачу" -> {
                    if (userRepository.findById(chatId).isPresent()) {
                        setDoctorsOnButtons(chatId, Strings.CHOOSE_DOCTOR);
                    } else
                        sendMessageWithScreenKeyboard(chatId, "Пожалуйста зарегистрируйтесь, чтобы иметь возможность записаться к врачу");
                }
                case "Посмотреть мою запись" -> {
                    Optional<User> user = userRepository.findById(chatId);
                    if (user.isEmpty()) {
                        sendMessageWithScreenKeyboard(chatId, "Пожалуйста зарегистрируйтесь, чтобы иметь возможность просмотра записи");
                        return;
                    } else {
                        String userData = user.get().getLastname() + " " + user.get().getFirstname() + " " + user.get().getPatronymic() + "-" + "patient";
                        String fullName = user.get().getLastname() + " " + user.get().getFirstname() + " " + user.get().getPatronymic();
                        googleCalendar = new GoogleCalendarReceiver();
                        TreeMap<String, String> schedule = googleCalendar.receiveFromGoogleCalendar(userData);

                        if (schedule.get(fullName) == null) {
                            sendMessageWithScreenKeyboard(chatId, "Здравствуйте! У вас отсутствует приём у врача");
                        } else {
                            String text = "Здравствуйте! Ваш доктор: \n" + schedule.get(fullName);
                            sendMessageWithScreenKeyboard(chatId, text);
                        }
                    }
                }
                case "Удалить врача" -> {
                    if (isAdmin(chatId)) {
                        setDoctorsOnButtons(chatId, Strings.DELETE_DOCTOR);
                    } else sendMessageWithScreenKeyboard(chatId, Strings.COMMAND_DOES_NOT_EXIST);
                }
                case "Просмотр расписания врачей" -> {
                    if (isAdmin(chatId)) {
                        setDoctorsOnButtons(chatId, Strings.OBSERVE_SCHEDULE);
                    } else sendMessageWithScreenKeyboard(chatId, Strings.COMMAND_DOES_NOT_EXIST);
                }
                case "Редактировать расписание врачей" -> {
                    if (isAdmin(chatId)) {
                        setDoctorsOnButtons(chatId, Strings.EDITE_DOCTOR);
                    } else sendMessageWithScreenKeyboard(chatId, Strings.COMMAND_DOES_NOT_EXIST);
                }
                case "Меню для работы с базой пациентов" -> {
                    if (isAdmin(chatId)) {
                        workWithPatientMenu(chatId);
                    } else sendMessageWithScreenKeyboard(chatId, Strings.COMMAND_DOES_NOT_EXIST);
                }
                case "Мой рабочий график" -> {
                    Optional<Doctor> doctor = doctorRepository.findById(chatId);
                    if (doctor.isPresent()) {
                        String doctorScheduleFromDb = receiveDoctorScheduleFromDb(Objects.requireNonNull(receiveDoctorFromDb(chatId)));
                        sendMessageWithScreenKeyboard(chatId, doctorScheduleFromDb);
                    } else {
                        sendMessageWithScreenKeyboard(chatId, Strings.COMMAND_DOES_NOT_EXIST);
                    }
                }
                case "Моя запись" -> {
                    Optional<Doctor> doctor = doctorRepository.findById(chatId);
                    if (doctor.isPresent()) {
                        createDateButtonsForDoctor(chatId);
                    } else {
                        String text = "Такая команда отсутствует";
                        sendMessageWithScreenKeyboard(chatId, text);
                    }
                }
                case "Добавить врача" -> {
                    if (isAdmin(chatId)) {
                        BLOCK_DEFAULT_MESSAGE_VALUE.put(chatId, 5);
                    } else sendMessageWithScreenKeyboard(chatId, Strings.COMMAND_DOES_NOT_EXIST);
                }
                case "Записать пациента к врачу" -> {
                    if (isAdmin(chatId)) {
                        BLOCK_DEFAULT_MESSAGE_VALUE.put(chatId, 10);
                    } else sendMessageWithScreenKeyboard(chatId, Strings.COMMAND_DOES_NOT_EXIST);
                }
                default -> {
                    if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == null) {
                        sendMessageWithScreenKeyboard(chatId, Strings.COMMAND_DOES_NOT_EXIST);
                    }
                }
            }

            /**
             * Не null значение BLOCK_DEFAULT_MESSAGE_VALUE блокирует отправку COMMAND_DOES_NOT_EXIST сообщений ("Такая команда отсутствует"),
             * кроме того, значение Integer определяет логику запуска соответствующих методов
             */
            if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 1) {
                LASTNAME.put(chatId, messageText);
                BLOCK_DEFAULT_MESSAGE_VALUE.replace(chatId, 2);
                sendMessageWithScreenKeyboard(chatId, "Введите ваше имя и отправьте сообщение");
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 2) {
                FIRSTNAME.put(chatId, messageText);
                BLOCK_DEFAULT_MESSAGE_VALUE.replace(chatId, 3);
                sendMessageWithScreenKeyboard(chatId, "Введите ваше отчество и отправьте сообщение");
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 3) {
                PATRONYMIC.put(chatId, messageText);
                BLOCK_DEFAULT_MESSAGE_VALUE.replace(chatId, 4);
                sendMessageWithScreenKeyboard(chatId, "Введите ваш номер телефона в формате 8xxxxxxxxxx и отправьте сообщение");
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 4) {
                PHONE_NUMBER.put(chatId, messageText);
                registerNewUser(update.getMessage());
                clearData(chatId);
                Optional<User> user = userRepository.findById(chatId);
                user.ifPresent(value -> sendMessageWithScreenKeyboard(chatId, value.getFirstname() + " " + value.getPatronymic() + ", спасибо за регистрацию!"));
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 5) { // для регистрации доктора в базе данных в процессе ввода фио и специализации блокируется отправка дефолтных сообщений
                sendMessageWithScreenKeyboard(chatId, "Введите фамилию врача, затем отправьте сообщение");
                BLOCK_DEFAULT_MESSAGE_VALUE.replace(chatId, 6);
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 6) {
                LASTNAME.put(chatId, messageText);
                BLOCK_DEFAULT_MESSAGE_VALUE.replace(chatId, 7);
                sendMessageWithScreenKeyboard(chatId, "Введите имя врача, затем отправьте сообщение");
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 7) {
                FIRSTNAME.put(chatId, messageText);
                BLOCK_DEFAULT_MESSAGE_VALUE.replace(chatId, 8);
                sendMessageWithScreenKeyboard(chatId, "Введите отчество врача, затем отправьте сообщение");
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 8) {
                PATRONYMIC.put(chatId, messageText);
                BLOCK_DEFAULT_MESSAGE_VALUE.replace(chatId, 9);
                sendMessageWithScreenKeyboard(chatId, "Введите специализацию врача, затем отправьте сообщение");
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 9) {
                tempData = messageText.toLowerCase(); // doctorSpeciality = messageText.toLowerCase();
                if (tempData.length() > 2 && LASTNAME.get(chatId).length() > 2 && FIRSTNAME.get(chatId).length() > 2 && PATRONYMIC.get(chatId).length() > 2) { // doctorSpeciality
                    addDoctorInDb(chatId);
                    sendMessageWithScreenKeyboard(chatId, "Доктор зарегистрирован в базе данных");
                } else {
                    sendMessageWithScreenKeyboard(chatId, "Ошибка ввода, ФИО не должны быть короче трёх символов");
                }
                clearData(chatId);
                tempData = null;  // специализация врача
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 10) {  // запись пациента на приём к врачу мз меню администратора
                sendMessageWithScreenKeyboard(chatId, "Введите ФИО пациента (пример: Иванов Иван Иванович) и отправьте сообщение ");
                BLOCK_DEFAULT_MESSAGE_VALUE.put(chatId, 11);

            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 11) {
                BLOCK_DEFAULT_MESSAGE_VALUE.remove(chatId);
                if (messageText.split(" ").length != 3) {
                    sendMessageWithScreenKeyboard(chatId, "Ошибка при вводе ФИО пациента");
                } else {
                    tempData = messageText;
                    setDoctorsOnButtons(chatId, Strings.CHOOSE_DOCTOR);
                }
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 12) { // поиск пациента по фамилии мз меню администратора
                findUser(chatId, messageText);
                BLOCK_DEFAULT_MESSAGE_VALUE.remove(chatId);
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 13) { // сообщение зарегистрированному пользователю мз меню администратора
                long chatIdFromTempData = Long.parseLong(tempData);
                sendMessageWithScreenKeyboard(chatIdFromTempData, "\uD83D\uDCCC Сообщение от администратора " + "\n" + messageText);
                sendMessageWithScreenKeyboard(chatId, "Сообщение отправлено");
                tempData = null;
                BLOCK_DEFAULT_MESSAGE_VALUE.remove(chatId);
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 14) { // сообщение всем зарегистрированымпользователям мз меню администратора
                messageForAllUsers(messageText);
                sendMessageWithScreenKeyboard(chatId, "Это сообщение отправлено всем зарегистрированным пользователям");
                BLOCK_DEFAULT_MESSAGE_VALUE.remove(chatId);
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 15) { // установка даты отпуска врача мз меню администратора
                tempData = messageText;
                sendMessageWithScreenKeyboard(chatId, "Введите дату выхода врача на работу - год-месяц-число в формате 2023-05-09");
                BLOCK_DEFAULT_MESSAGE_VALUE.put(chatId, 16);
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 16) {
                setDoctorVacation(chatId, messageText);
                tempData = null;
                DOCTOR_SCHEDULE_LIST.clear();
                BLOCK_DEFAULT_MESSAGE_VALUE.remove(chatId);
            }

            // CallbackQuery() - ответ на клик по кнопке InlineKeyboardButton, прикреплённой к сообщению
        } else if (update.hasCallbackQuery()) {
            long messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            String callbackData = update.getCallbackQuery().getData(); // строка с информацией CallbackData закреплённой за кнопкой InlineKeyboardButton

            if (callbackData.contains(Strings.DELETE_DOCTOR)) {
                long doctorId = Long.parseLong(callbackData.replace(Strings.DELETE_DOCTOR, ""));
                doctorRepository.delete(Objects.requireNonNull(receiveDoctorFromDb(doctorId)));
                editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Доктор удалён из базы данных")); // для завершения метода вызывается метод

            } else if (callbackData.contains(Strings.EDITE_DOCTOR)) { // если нажата кнопка %редактировать расписание, запускается меню выбора дня недели приёма, а выбранный доктор помещается в хэш-мап под индексом 0
                String doctorId = callbackData.replace(Strings.EDITE_DOCTOR, "");
                doctorWorkWeekKeyboard(chatId, messageId, doctorId);

            } else if (callbackData.contains(Strings.CHOOSE_DAY)) {// из меню выбора дня недели запускается меню установки времени приёма ("%chooseday" + i + " " + doctorId)
                String[] dayAndDoctorId = callbackData.replace(Strings.CHOOSE_DAY, "").split(" ");
                tempDayOfWeek = Integer.parseInt(dayAndDoctorId[0]) + 1;
                String doctorId = dayAndDoctorId[1];
                doctorWorkHourKeyboard(chatId, messageId, doctorId);

            } else if (callbackData.contains(Strings.CHOOSE_TIME)) { // запускает метод doctorWorkHourKeyboard для установки времени приёма врача в формате "00ч-00ч"
                String[] timeAndDoctorId = callbackData.replace(Strings.CHOOSE_TIME, "").split(" ");
                String doctorId = timeAndDoctorId[1];

                if (tempData == null) { // tempHour
                    tempData = timeAndDoctorId[0]; //  "-" + timeAndDoctorId[0];  // tempHour
                    editMessageTextExecute(receiveEditMessageText(chatId, messageId, ""));
                    doctorWorkHourKeyboard(chatId, messageId, doctorId);
                } else {
                    if (Integer.parseInt(tempData) > Integer.parseInt(timeAndDoctorId[0])) { // tempHour
                        tempData = null; // tempHour
                        DOCTOR_SCHEDULE_LIST.clear();
                        editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Время начала приёма не может быть позднее времени окончания приёма!"));
                    } else {
                        tempData += "-" + timeAndDoctorId[0]; // tempHour
                        doctorWorkWeekKeyboard(chatId, messageId, doctorId);
                        DOCTOR_SCHEDULE_LIST.put(tempDayOfWeek, tempData); // tempHour
                        tempData = null; // tempHour
                    }
                }

            } else if (callbackData.contains(Strings.OBSERVE_SCHEDULE)) { // запускает метод просмотра расписания врача
                long doctorId = Long.parseLong(callbackData.replace(Strings.OBSERVE_SCHEDULE, ""));
                String doctorScheduleFromDb = receiveDoctorScheduleFromDb(Objects.requireNonNull(receiveDoctorFromDb(doctorId)));
                editMessageTextExecute(receiveEditMessageText(chatId, messageId, doctorScheduleFromDb));

            } else if (callbackData.contains(Strings.CHOOSE_DOCTOR)) {
                long doctorId = Long.parseLong(callbackData.replace(Strings.CHOOSE_DOCTOR, ""));
                doctorScheduleDaysButtons(Objects.requireNonNull(receiveDoctorFromDb(doctorId)), chatId, messageId);

            } else if (callbackData.contains(Strings.DATE_BUTTON)) { // %datebutton2023-06-26 doctorId
                String[] choseDayAndDoctorId = callbackData.replace(Strings.DATE_BUTTON, "").split(" "); // choseDayAndDoctorId = 2023-06-05, doctorId
                doctorScheduleHoursButtons(choseDayAndDoctorId[0], chatId, messageId, choseDayAndDoctorId[1]);

            } else if (callbackData.contains(Strings.TIME_BUTTON)) {
                String[] choseDateTimeAndDoctorId = callbackData.replace(Strings.TIME_BUTTON, "").split("="); // "%timebutton" + 2023-06-15 + "T" + 10.30  + "=" + doctorId
                if (!AVOID_REGISTER_COLLISION.containsValue(choseDateTimeAndDoctorId[0])) { // коллизия выбора времени записи
                    AVOID_REGISTER_COLLISION.put(chatId, choseDateTimeAndDoctorId[0]);
                    controlQuestionButton(choseDateTimeAndDoctorId[0], chatId, messageId, choseDateTimeAndDoctorId[1]);
                } else {
                    editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Извините, это время уже недоступно"));
                }

            } else if (callbackData.contains(Strings.I_AM_SURE)) {
                if (AVOID_REGISTER_COLLISION.get(chatId) == null) {
                    editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Время подтверждения истекло, попробуйте снова"));
                } else {
                    String[] dateTimeAndDoctorId = callbackData.replace(Strings.I_AM_SURE, "").split(" ");//dateTimeForCalendar = 2023-06-12T10:00 doctorId
                    String dateTime = dateTimeAndDoctorId[0];
                    long doctorId = Long.parseLong(dateTimeAndDoctorId[1]);
                    if (isAdmin(chatId)) {
                        String fullName = tempData; // данные в поле tempData добавлены в строке 264 в блоке if blockDefaultMessageValue.get(chatId) == 16
                        addNoteIntoGoogleCalendar(chatId, dateTime, doctorId, fullName);
                        tempData = null;
                        editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Запись добавлена в календарь"));
                        AVOID_REGISTER_COLLISION.remove(chatId);
                    } else {
                        addNoteIntoGoogleCalendar(chatId, dateTime, doctorId, "");
                        editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Спасибо, мы будем ждать вас! Для уточнения деталей записи с вами может связаться администратор"));
                        AVOID_REGISTER_COLLISION.remove(chatId);
                    }
                }

            } else if (callbackData.contains(Strings.FOUND_PATIENT) && tempData == null) {
                tempData = callbackData.replace(Strings.FOUND_PATIENT, "");
                BLOCK_DEFAULT_MESSAGE_VALUE.put(chatId, 13);
                editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Введите текст сообщения, затем отправьте его получателю"));

            } else if (callbackData.contains(Strings.SET_VACATION)) {
                long doctorId = Long.parseLong(callbackData.replace(Strings.SET_VACATION, ""));
                TEMP_CHOOSE_DOCTOR.put(chatId, receiveDoctorFromDb(doctorId));
                editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Введите дату начала отпуска (больничного/отгула) год-месяц-число в формате 2023-05-09"));
                BLOCK_DEFAULT_MESSAGE_VALUE.put(chatId, 15);

            } else if (callbackData.contains(Strings.FOUND_PATIENT) && tempData.equals("find user for getting info about him")) {
                long chatIdFromString = Long.parseLong(callbackData.replace(Strings.FOUND_PATIENT, ""));
                Optional<User> user = userRepository.findById(chatIdFromString);
                String infoAboutUser = user.toString().replace("Optional[", "").replace("]", "");
                editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Информация о пациенте:"));
                sendMessageWithScreenKeyboard(chatId, infoAboutUser);
                tempData = null;
                BLOCK_DEFAULT_MESSAGE_VALUE.remove(chatId);

            } else if (callbackData.contains(Strings.BUTTON_FOR_DOCTOR)) { // "#d" + map.getKey()
                String date = callbackData.replace(Strings.BUTTON_FOR_DOCTOR, "");
                createEventTextForDoctor(chatId, messageId, date);

            } else if (callbackData.contains(Strings.REGISTER)) { // doctorId + " " + chatId
                String[] userData = callbackData.replace(Strings.REGISTER, "").split(" ");
                long doctorId = Long.parseLong(userData[0]);
                long userChatId = Long.parseLong(userData[1]);
                Doctor doctorFromDb = receiveDoctorFromDb(doctorId);
                Doctor newDoctor = (Doctor) Objects.requireNonNull(doctorFromDb).clone();
                doctorRepository.delete(Objects.requireNonNull(doctorFromDb));
                newDoctor.setChatId(userChatId);
                doctorRepository.save(newDoctor);
                String fullName = newDoctor.getDoctorLastname() + " " + newDoctor.getDoctorFirstname() + " " + newDoctor.getDoctorPatronymic();
                editMessageTextExecute(receiveEditMessageText(chatId, messageId, fullName + " зарегистрирован как врач"));
                sendMessageWithScreenKeyboard(userChatId, newDoctor.getDoctorFirstname() + " " + newDoctor.getDoctorPatronymic() + ", вы зарегистрированы как врач");

            } else if (callbackData.contains(Strings.SAVE_SCHEDULE)) {
                long doctorId = Long.parseLong(callbackData.replace(Strings.SAVE_SCHEDULE, ""));
                setDoctorScheduleInDB(Objects.requireNonNull(receiveDoctorFromDb(doctorId)));
                String doctorScheduleFromDb = receiveDoctorScheduleFromDb(Objects.requireNonNull(receiveDoctorFromDb(doctorId)));
                DOCTOR_SCHEDULE_LIST.clear();
                editMessageTextExecute(receiveEditMessageText(chatId, messageId, doctorScheduleFromDb));

            } else if (callbackData.contains(Strings.DO_NOT_REGISTER)) {
                long userChatId = Long.parseLong(callbackData.replace(Strings.DO_NOT_REGISTER, ""));
                sendMessageWithScreenKeyboard(userChatId, "Ваша заявка на регистрацию отклонена");
                editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Заявка отклонена"));

            } else if (callbackData.contains(Strings.REG_AS_DOCTOR)) {
                String userData = callbackData.replace(Strings.REG_AS_DOCTOR, ""); // doctorId chatId
                requestToAdmin(userData);
                editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Пожалуйста, дождитесь одобрения заявки администратором"));

            } else if (callbackData.contains(Strings.REG_AS_PATIENT)) { // doctor.getChatId() + " " + phoneNumber
                String[] userData = callbackData.replace(Strings.REG_AS_PATIENT, "").split(" ");
                String phoneNumber = userData[1];
                Doctor doctor = receiveDoctorFromDb(Long.parseLong(userData[0]));
                String fullName = Objects.requireNonNull(doctor).getDoctorLastname() + " " + doctor.getDoctorFirstname() + " " + doctor.getDoctorPatronymic();
                setUserInDB(chatId, fullName, phoneNumber);
                clearData(chatId);
                editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Спасибо за регистрацию!"));

            } else if (callbackData.contains(Strings.DELETE_VACATION)) {
                long doctorId = Long.parseLong(callbackData.replace(Strings.DELETE_VACATION, ""));
                Doctor doctor = receiveDoctorFromDb(doctorId);
                Objects.requireNonNull(doctor).setVacation(null);
                doctorRepository.save(doctor);
                editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Даты отпусков удалены"));

            } else if (callbackData.contains(Strings.CHOOSE_ANOTHER)) {
                long doctorId = Long.parseLong(callbackData.replace(Strings.CHOOSE_ANOTHER, "")); // "%chooseanother" + doctorId
                doctorScheduleDaysButtons(Objects.requireNonNull(receiveDoctorFromDb(doctorId)), chatId, messageId);
            }

            switch (callbackData) {
                case Strings.YES_BUTTON -> {
                    editMessageTextExecute(receiveEditMessageText(chatId, messageId, Strings.INTRODUCE_TEXT));
                    BLOCK_DEFAULT_MESSAGE_VALUE.put(chatId, 1);
                }
                case Strings.USER_MESSAGE -> {
                    BLOCK_DEFAULT_MESSAGE_VALUE.put(chatId, 12);
                    editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Введите фамилию/часть фамилии пациента и отправьте сообщение"));
                }
                case Strings.INFO_ABOUT_USER -> {
                    editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Введите фамилию пациента и отправьте сообщение"));
                    tempData = "find user for getting info about him";
                    BLOCK_DEFAULT_MESSAGE_VALUE.put(chatId, 12);
                }
                case Strings.FOR_ALL_USER_MESSAGE -> {
                    editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Введите текст сообщения и отправьте его"));
                    BLOCK_DEFAULT_MESSAGE_VALUE.put(chatId, 14);
                }
                case Strings.CANCEL_BUTTON -> {
                    String text = "Регистрация отменена, в дальнейшем вы можете зарегистрироваться с помощью команды /start";
                    editMessageTextExecute(receiveEditMessageText(chatId, messageId, text));
                }
                case Strings.I_AM_NOT_SURE -> {
                    AVOID_REGISTER_COLLISION.remove(chatId);
                    editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Запись отменена"));
                }
                case Strings.ALL_USER_LIST -> {
                    Optional<User> user = userRepository.findById(chatId);
                    if (user.isPresent() && config.adminValidation.equals(user.get().getLastname() + user.get().getFirstname() + user.get().getPatronymic())) {
                        StringBuilder stringBuilder = new StringBuilder();
                        Iterable<User> users = userRepository.findAll();
                        for (User u : users) {
                            stringBuilder.append(u.toString()).append("\n\n");
                        }
                        long count = userRepository.count();
                        stringBuilder.append("Всего пользователей = ").append(count);
                        editMessageTextExecute(receiveEditMessageText(chatId, messageId, stringBuilder.toString()));
                    } else sendMessageWithScreenKeyboard(chatId, "Такая команда отсутствует");
                }
            }
        }
    }


    @Override
    public String getBotUsername() {
        return config.BotName;
    }


    private void greetMessage(long chatId) {
        String text = "Здравствуйте! Зарегистрированные пользователи имеют возможность записи к врачу из меню и просмотр своей записи. Желаете зарегистрироваться?";
        SendMessage sendMessage = receiveCreatedMessage(chatId, text);
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInlineButton = new ArrayList<>();

        InlineKeyboardButton yesButton = new InlineKeyboardButton();
        yesButton.setText("Да");
        yesButton.setCallbackData(Strings.YES_BUTTON);

        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("Отмена");
        cancelButton.setCallbackData(Strings.CANCEL_BUTTON);

        rowInlineButton.add(yesButton);
        rowInlineButton.add(cancelButton);
        rowsInline.add(rowInlineButton);
        inlineKeyboardMarkup.setKeyboard(rowsInline);
        sendMessage.setReplyMarkup(inlineKeyboardMarkup);

        sendMessageExecute(sendMessage);
    }


    private void registerNewUser(Message message) {
        if (userRepository.findById(message.getChatId()).isEmpty()) {
            Chat chat = message.getChat();
            long chatId = message.getChatId();
            long phone = 0;

            if (PHONE_NUMBER.get(chatId).length() > 15) {
                sendMessageWithScreenKeyboard(chatId, "Ошибка ввода номера телефона");
                clearData(chatId);
                return;
            }

            try {
                phone = Long.parseLong(PHONE_NUMBER.get(chatId));
            } catch (NumberFormatException e) {
                sendMessageWithScreenKeyboard(chatId, "Ошибка ввода номера телефона");
                clearData(chatId);
                return;
            }

            Iterable<User> users = userRepository.findAll();
            for (User userFromDb : users) {
                if (FIRSTNAME.get(chatId).length() < 3 || LASTNAME.get(chatId).length() < 3 || PATRONYMIC.get(chatId).length() < 3 || userFromDb.getLastname().equals("Admin") &&
                        LASTNAME.get(chatId).equals("Admin") || userFromDb.getFirstname().equals("Admin") &&
                        FIRSTNAME.get(chatId).equals("Admin") || userFromDb.getPatronymic().equals("Admin") && PATRONYMIC.get(chatId).equals("Admin")) {
                    String text = "Регистрация с такими личными данными запрещена. Длинна фамилии, имени, отчества должны быть не " +
                            "менее трёх символов";
                    sendMessageWithScreenKeyboard(chatId, text);
                    clearData(chatId);
                    return;
                } else if (userFromDb.getLastname().equals(LASTNAME.get(chatId)) && userFromDb.getFirstname().equals(FIRSTNAME.get(chatId))
                        && userFromDb.getPatronymic().equals(PATRONYMIC.get(chatId))) {
                    String temp = PATRONYMIC.get(chatId);
                    PATRONYMIC.put(chatId, temp + " " + PHONE_NUMBER.get(chatId));
                }
            }

            Iterable<Doctor> doctors = doctorRepository.findAll();
            String fullUserName = LASTNAME.get(chatId) + " " + FIRSTNAME.get(chatId) + " " + PATRONYMIC.get(chatId);
            for (Doctor doctorFromDb : doctors) {
                String doctorUserName = doctorFromDb.getDoctorLastname() + " " + doctorFromDb.getDoctorFirstname() + " " + doctorFromDb.getDoctorPatronymic();

                if (doctorUserName.equals(fullUserName) && doctorFromDb.getChatId() < 100) {
                    userWantsToRegisterAs(chatId, PHONE_NUMBER.get(chatId), doctorFromDb);
                    BLOCK_DEFAULT_MESSAGE_VALUE.remove(chatId);
                    return;
                }
            }
            setUserInDB(chatId, chat.getFirstName(), PHONE_NUMBER.get(chatId));
            clearData(chatId);
        }
    }

    // кнопки экранной клавиатуры врача
    private void doctorKeyBoard(SendMessage sendMessage) {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setResizeKeyboard(true); // уменьшение кнопок экранной клавиатуры
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add("Моя запись");
        row.add("Мой рабочий график");
        keyboardRows.add(row);
        replyKeyboardMarkup.setKeyboard(keyboardRows);
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
    }

    // кнопки экранной клавиатуры пациента
    private void userKeyBoard(SendMessage sendMessage) {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setResizeKeyboard(true);
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add("Записаться на приём к врачу");
        keyboardRows.add(row);
        row = new KeyboardRow();
        row.add("Посмотреть мою запись");
        keyboardRows.add(row);
        replyKeyboardMarkup.setKeyboard(keyboardRows);
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
    }

    // кнопки экранной клавиатуры администратора
    private void adminKeyBoard(SendMessage sendMessage) {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setResizeKeyboard(true);
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow fourthRow = new KeyboardRow();
        fourthRow.add("Записать пациента к врачу");
        keyboardRows.add(fourthRow);
        KeyboardRow fifthRow = new KeyboardRow();
        fifthRow.add("Меню для работы с базой пациентов");
        keyboardRows.add(fifthRow);
        KeyboardRow firstRow = new KeyboardRow();
        firstRow.add("Добавить врача ");
        firstRow.add("Удалить врача");
        keyboardRows.add(firstRow);
        KeyboardRow secondRow = new KeyboardRow();
        secondRow.add("Просмотр расписания врачей");
        secondRow.add("Редактировать расписание врачей");
        keyboardRows.add(secondRow);

        replyKeyboardMarkup.setKeyboard(keyboardRows);
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
    }

    // метод отправляет сообщения и устанавливает разную экранную клавиатуру разным группам пользователей
    private void sendMessageWithScreenKeyboard(long chatId, String text) {
        SendMessage sendMessage = receiveCreatedMessage(chatId, text);

        Iterable<User> users = userRepository.findAll();
        for (User user : users) {
            if (user.getChatId() == chatId && !(config.adminValidation.equals(user.getLastname() + user.getFirstname() + user.getPatronymic()))) {
                userKeyBoard(sendMessage); // экранная клавиатура для зарегистрированных пользователей
            } else if (user.getChatId() == chatId && config.adminValidation.equals(user.getLastname() + user.getFirstname() + user.getPatronymic())) {
                adminKeyBoard(sendMessage); // экранная клавиатура для администратора
            }
        }
        Optional<Doctor> doctor = doctorRepository.findById(chatId);
        if (doctor.isPresent()) { // экранная клавиатура для врачей
            doctorKeyBoard(sendMessage);
        }
        sendMessageExecute(sendMessage);
    }

    // Метод создает прикреплённые к сообщению кнопки с ФИО врачей, запускаемый методом процесс зависит от содержания строки orderText, которая переотправляется далее в CallbackData
    private void setDoctorsOnButtons(long chatId, String orderText) { // String orderText переотправляется два типа: для удаления врача "%deletedoctor" и для редактирования графика - "%editedoctor"
        Iterable<Doctor> doctors = doctorRepository.findAll();
        if (doctorRepository.count() == 0) {
            sendMessageWithScreenKeyboard(chatId, "Нет врачей для выбора");
        } else {
            String chooseOrderText = switch (orderText) {
                case Strings.DELETE_DOCTOR -> "Выберите врача, которого хотите удалить из базы";
                case Strings.EDITE_DOCTOR -> "Выберите врача, расписание которого хотите редактировать";
                case Strings.OBSERVE_SCHEDULE -> "Выберите врача, расписание которого хотите посмотреть";
                case Strings.CHOOSE_DOCTOR -> "Выберите врача, к которому желаете записаться";
                default -> "Err";
            };

            SendMessage sendMessage = receiveCreatedMessage(chatId, chooseOrderText);

            InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>(); // коллекция коллекций с горизонтальным рядом кнопок, создаёт вертикальный ряд кнопок

            for (Doctor doctor : doctors) { // Динамически расширяемое меню выбора врачей
                String buttonText = doctor.getDoctorLastname() + " " + doctor.getDoctorFirstname() + " " + doctor.getDoctorPatronymic() + ", " + doctor.getSpeciality();
                long doctorId = doctor.getChatId();

                InlineKeyboardButton keyboardButton = new InlineKeyboardButton();
                keyboardButton.setText(buttonText);
                keyboardButton.setCallbackData(orderText + doctorId); // orderText == DELETE_DOCTOR / EDITE_DOCTOR / OBSERVE_SCHEDULE / CHOOSE_DOCTOR + doctor id
                List<InlineKeyboardButton> list = new ArrayList<>(1);
                list.add(keyboardButton);
                rowsInline.add(list);
            }

            inlineKeyboardMarkup.setKeyboard(rowsInline);
            sendMessage.setReplyMarkup(inlineKeyboardMarkup);
            sendMessageExecute(sendMessage);
        }
    }

    // Установка рабочего расписания врача
    private void doctorWorkWeekKeyboard(long chatId, long messageId, String doctorId) {
        EditMessageText editMessageText = receiveEditMessageText(chatId, messageId, Strings.MENU_DOCTOR_SCHEDULE_TEXT); // решение тестовое
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>(); // коллекция коллекций с горизонтальным рядом кнопок, создаёт вертикальный ряд кнопок
        List<InlineKeyboardButton> firstRowInlineButton = new ArrayList<>(); // коллекция с горизонтальным рядом кнопок
        List<InlineKeyboardButton> secondRowInlineButton = new ArrayList<>(); // коллекция с горизонтальным рядом кнопок
        List<InlineKeyboardButton> thirdRowInlineButton = new ArrayList<>(); // коллекция с горизонтальным рядом кнопок

        String[] daysOfWeek = {"пн", "вт", "ср", "чт", "пт", "сб", "вс"};

        for (int i = 0; i < 7; i++) {
            InlineKeyboardButton keyboardButton = new InlineKeyboardButton();
            keyboardButton.setText((daysOfWeek[i]));
            keyboardButton.setCallbackData(Strings.CHOOSE_DAY + i + " " + doctorId);
            firstRowInlineButton.add(keyboardButton);
        }

        InlineKeyboardButton saveButton = new InlineKeyboardButton();
        saveButton.setText("Сохранить настройки расписания рабочей недели");
        saveButton.setCallbackData(Strings.SAVE_SCHEDULE + doctorId); // запись мз поля tempSetDoctor, для быстрого возврата к дате в расписании доктора из метода chooseTime()
        secondRowInlineButton.add(saveButton);

        InlineKeyboardButton setVacationButton = new InlineKeyboardButton();
        setVacationButton.setText("Добавить дату отпуска");
        setVacationButton.setCallbackData(Strings.SET_VACATION + doctorId);
        thirdRowInlineButton.add(setVacationButton);

        InlineKeyboardButton deleteVacationButton = new InlineKeyboardButton();
        deleteVacationButton.setText("Удалить даты отпуска");
        deleteVacationButton.setCallbackData(Strings.DELETE_VACATION + doctorId);
        thirdRowInlineButton.add(deleteVacationButton);

        rowsInline.add(firstRowInlineButton); // размещение набора кнопок в вертикальном ряду
        rowsInline.add(secondRowInlineButton); // размещение набора кнопок в вертикальном ряду
        rowsInline.add(thirdRowInlineButton);

        inlineKeyboardMarkup.setKeyboard(rowsInline);
        editMessageText.setReplyMarkup(inlineKeyboardMarkup);

        editMessageTextExecute(editMessageText);
    }

    // Установка часов работы врача
    private void doctorWorkHourKeyboard(long chatId, long messageId, String doctorId) {
        String doctorWorkDayText;

        if (tempData == null) { // tempHour
            doctorWorkDayText = "Выберите время начала рабочего дня врача (час)";
        } else {
            doctorWorkDayText = "Выберите время окончания рабочего дня врача (час)";
        }

        EditMessageText editMessageText = receiveEditMessageText(chatId, messageId, doctorWorkDayText); // решение тестовое
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> firstRowInlineButton = new ArrayList<>();
        List<InlineKeyboardButton> secondRowInlineButton = new ArrayList<>();

        for (int i = 0; i < 14; i++) {
            InlineKeyboardButton keyboardButton = new InlineKeyboardButton();
            keyboardButton.setText((String.valueOf(i + 8))); // часы работы врача берутся из цикла и выводится на кнопки, отсчёт с 8 часов
            keyboardButton.setCallbackData(Strings.CHOOSE_TIME + (i + 8) + " " + doctorId);
            if (i < 7) {
                firstRowInlineButton.add(keyboardButton);
            } else secondRowInlineButton.add(keyboardButton);
        }

        rowsInline.add(firstRowInlineButton);
        rowsInline.add(secondRowInlineButton);
        inlineKeyboardMarkup.setKeyboard(rowsInline);
        editMessageText.setReplyMarkup(inlineKeyboardMarkup);

        editMessageTextExecute(editMessageText);
    }

    // Добавление врача в бд
    private void addDoctorInDb(long chatId) {
        Doctor newDoctor = new Doctor();
        long doctorChatId = 1;

        Iterable<Doctor> doctors = doctorRepository.findAll();
        for (Doctor doctor : doctors) {
            if (doctorChatId != doctor.getChatId()) break;
            doctorChatId++;
        }

        newDoctor.setChatId(doctorChatId);
        newDoctor.setDoctorLastname(LASTNAME.get(chatId));
        newDoctor.setDoctorFirstname(FIRSTNAME.get(chatId));
        newDoctor.setDoctorPatronymic(PATRONYMIC.get(chatId));
        newDoctor.setSpeciality(tempData);
        doctorRepository.save(newDoctor);
    }

    // Установка расписание врача в бд
    private void setDoctorScheduleInDB(Doctor doctor) {
        doctor.setMondaySchedule(DOCTOR_SCHEDULE_LIST.get(1));
        doctor.setTuesdaySchedule(DOCTOR_SCHEDULE_LIST.get(2));
        doctor.setWednesdaySchedule(DOCTOR_SCHEDULE_LIST.get(3));
        doctor.setThursdaySchedule(DOCTOR_SCHEDULE_LIST.get(4));
        doctor.setFridaySchedule(DOCTOR_SCHEDULE_LIST.get(5));
        doctor.setSaturdaySchedule(DOCTOR_SCHEDULE_LIST.get(6));
        doctor.setSundaySchedule(DOCTOR_SCHEDULE_LIST.get(7));
        doctorRepository.save(doctor);
    }

    // Получить расписание врача из бд
    private String receiveDoctorScheduleFromDb(Doctor doctor) {
        return "Доктор " +
                doctor.getDoctorLastname() + " " +
                doctor.getDoctorFirstname() + " " +
                doctor.getDoctorPatronymic() + ", недельное расписание приёма пациентов: \nпонедельник: " +
                doctor.getMondaySchedule() + " ч. \nвторник: " +
                doctor.getTuesdaySchedule() + " ч. \nсреда: " +
                doctor.getWednesdaySchedule() + " ч. \nчетверг: " +
                doctor.getThursdaySchedule() + " ч. \nпятница: " +
                doctor.getFridaySchedule() + " ч. \nсуббота: " +
                doctor.getSaturdaySchedule() + " ч. \nвоскресенье: " +
                doctor.getSundaySchedule() + " ч. \n\nОтпуск - дата ухода : дата выхода на работу - " +
                doctor.getVacation();
    }

    // Метод запрашивает подтверждение записи к врачу от пользователя
    private void controlQuestionButton(String dateTime, long chatId, long messageId, String doctorId) {
        EditMessageText editMessageText = receiveEditMessageText(chatId, messageId, "Подтвердите, пожалуйста, запись к врачу");
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> firstRowInlineButton = new ArrayList<>();

        InlineKeyboardButton sureButton = new InlineKeyboardButton();
        sureButton.setText("Подтвердить");
        sureButton.setCallbackData(Strings.I_AM_SURE + dateTime + " " + doctorId); // запись мз поля tempSetDoctor, для быстрого возврата к дате в расписании доктора из метода chooseTime()
        firstRowInlineButton.add(sureButton);

        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("Отменить");
        cancelButton.setCallbackData(Strings.I_AM_NOT_SURE);
        firstRowInlineButton.add(cancelButton);
        rowsInline.add(firstRowInlineButton);

        inlineKeyboardMarkup.setKeyboard(rowsInline);
        editMessageText.setReplyMarkup(inlineKeyboardMarkup);

        editMessageTextExecute(editMessageText);
    }

    // Кнопки прикреплённые к сообщению для администратора для работы с базой пациентов
    private void workWithPatientMenu(long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(Strings.PATIENT_MENU_TEXT);

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        List<InlineKeyboardButton> firstRowInlineButton = new ArrayList<>();
        List<InlineKeyboardButton> secondRowInlineButton = new ArrayList<>();
        List<InlineKeyboardButton> thirdRowInlineButton = new ArrayList<>();
        List<InlineKeyboardButton> fourthRowInlineButton = new ArrayList<>();

        InlineKeyboardButton firstButton = new InlineKeyboardButton();
        firstButton.setText("Список всех пациентов");
        firstButton.setCallbackData(Strings.ALL_USER_LIST);

        InlineKeyboardButton secondButton = new InlineKeyboardButton();
        secondButton.setText("Написать сообщение пациенту");
        secondButton.setCallbackData(Strings.USER_MESSAGE);

        InlineKeyboardButton thirdButton = new InlineKeyboardButton();
        thirdButton.setText("Сообщение для всех пациентов");
        thirdButton.setCallbackData(Strings.FOR_ALL_USER_MESSAGE);

        InlineKeyboardButton fourthButton = new InlineKeyboardButton();
        fourthButton.setText("Посмотреть информацию о пациенте");
        fourthButton.setCallbackData(Strings.INFO_ABOUT_USER);


        firstRowInlineButton.add(firstButton);
        secondRowInlineButton.add(secondButton);
        thirdRowInlineButton.add(thirdButton);
        fourthRowInlineButton.add(fourthButton);

        rowsInline.add(firstRowInlineButton);
        rowsInline.add(secondRowInlineButton);
        rowsInline.add(thirdRowInlineButton);
        rowsInline.add(fourthRowInlineButton);

        inlineKeyboardMarkup.setKeyboard(rowsInline);
        sendMessage.setReplyMarkup(inlineKeyboardMarkup);

        sendMessageExecute(sendMessage);
    }

    // Метод сопоставляет рабочий график врача и запись к врачум из Google Calendar, предоставляя возможность выбора свободной для записи к врачу даты
    private void doctorScheduleDaysButtons(Doctor doctor, long chatId, long messageId) {
        int arrayListLength = 15;
        ArrayList<String> scheduleDays = new ArrayList<>(arrayListLength);
        LocalDate localDate = LocalDate.now();
        googleCalendar = new GoogleCalendarReceiver();

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd"); // формат даты для сравнения с датами календаря Google
        DateTimeFormatter weekFormatter = DateTimeFormatter.ofPattern("e"); // формат даты для работы с днём недели в цикле

        TreeMap<String, String> googleCalendarSchedule = new TreeMap<>();
        TreeMap<String, String> tempSchedule = googleCalendar.receiveFromGoogleCalendar(doctor.getDoctorLastname() + " " + doctor.getDoctorFirstname() + " " + doctor.getDoctorPatronymic() + "-" + "doctor");

        // реализация возможности добавлять 2 пациента на одно и то же время в календаре, оставляя в map только одно дубль-время
        for (Map.Entry<String, String> map : tempSchedule.entrySet()) {
            String[] time = map.getValue().split(" ");
            for (int i = 0; i < time.length - 1; i++) {
                if (!time[i].equals(time[i + 1])) {
                    time[i] = "";
                }
            }
            time[time.length - 1] = "";
            map.setValue(Arrays.toString(time).replace(",", "").replace("[", "").replace("]", "").replaceAll("  ", " "));
        }

        googleCalendarSchedule.putAll(tempSchedule);
        googleCalendarSchedule.putAll(doctorVacationDaysCheck(doctor));

        // строка длинной более x символов добавляется в HashMap для того, чтобы не отображать текущий день на кнопке
        String setTodayText = "<------------------------the string with length over doctors work time---------------------------->";
        googleCalendarSchedule.put(dateTimeFormatter.format(localDate), setTodayText);

        int dayOfWeek = Integer.parseInt(weekFormatter.format(localDate));
        int day = 0;

        // Цикл сверяет недельный график доктора с записью в календаре Google и находит свободные для записи дни, помещая их в ArrayList
        for (int i = 0; scheduleDays.size() < arrayListLength; i++) {
            // соответствие дня недели в цикле и текущего/итерационного && если в этот день недели есть приём у доктора
            if (dayOfWeek == 1 && doctor.getMondaySchedule() != null) {
                String[] workHours = doctor.getMondaySchedule().split("-");
                // workTime - продолжительность рабочего дня врача
                int workTime = Integer.parseInt(workHours[1]) - Integer.parseInt(workHours[0]);

                // если рабочий день полностью свободен, он записывается в ArrayList
                if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))) == null) {
                    scheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                    // каждая запись в расписании календаря == 6 символам, если длинна строки с расписанием меньше рабочего времени * 4 (6 символов в строке это эквивалент 1.5 часа приёма запиисанного пациента),
                    // рабочий день записывается в ArrayList
                } else if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))).length() < workTime * 4) {
                    scheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                }
            }
            if (dayOfWeek == 1) {
                day++;
                dayOfWeek++;
            }

            if (dayOfWeek == 2 && doctor.getTuesdaySchedule() != null) {
                String[] workHours = doctor.getTuesdaySchedule().split("-");
                int workTime = Integer.parseInt(workHours[1]) - Integer.parseInt(workHours[0]);

                if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))) == null) {
                    scheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                } else if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))).length() < workTime * 4) {
                    scheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                }
            }
            if (dayOfWeek == 2) {
                day++;
                dayOfWeek++;
            }

            if (dayOfWeek == 3 && doctor.getWednesdaySchedule() != null) {
                String[] workHours = doctor.getWednesdaySchedule().split("-");
                int workTime = Integer.parseInt(workHours[1]) - Integer.parseInt(workHours[0]);

                if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))) == null) {
                    scheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                } else if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))).length() < workTime * 4) {
                    scheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                }
            }
            if (dayOfWeek == 3) {
                day++;
                dayOfWeek++;
            }

            if (dayOfWeek == 4 && doctor.getThursdaySchedule() != null) {
                String[] workHours = doctor.getThursdaySchedule().split("-");
                int workTime = Integer.parseInt(workHours[1]) - Integer.parseInt(workHours[0]);

                if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))) == null) {
                    scheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                } else if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))).length() < workTime * 4) {
                    scheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                }
            }
            if (dayOfWeek == 4) {
                day++;
                dayOfWeek++;
            }

            if (dayOfWeek == 5 && doctor.getFridaySchedule() != null) {
                String[] workHours = doctor.getFridaySchedule().split("-");
                int workTime = Integer.parseInt(workHours[1]) - Integer.parseInt(workHours[0]);

                if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))) == null) {
                    scheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                } else if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))).length() < workTime * 4) {
                    scheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                }
            }
            if (dayOfWeek == 5) {
                day++;
                dayOfWeek++;
            }

            if (dayOfWeek == 6 && doctor.getSaturdaySchedule() != null) {
                String[] workHours = doctor.getSaturdaySchedule().split("-");
                int workTime = Integer.parseInt(workHours[1]) - Integer.parseInt(workHours[0]);

                if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))) == null) {
                    scheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                } else if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))).length() < workTime * 4) {
                    scheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                }
            }
            if (dayOfWeek == 6) {
                day++;
                dayOfWeek++;
            }

            if (dayOfWeek == 7 && doctor.getSundaySchedule() != null) {
                String[] workHours = doctor.getSundaySchedule().split("-");
                int workTime = Integer.parseInt(workHours[1]) - Integer.parseInt(workHours[0]);

                if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))) == null) {
                    scheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                } else if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))).length() < workTime * 4) {
                    scheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                }
            }
            if (dayOfWeek == 7) {
                day++;
                dayOfWeek = 1;
            }
        }

        EditMessageText editMessageText = receiveEditMessageText(chatId, messageId, "Выберите дату для записи");

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>(); // коллекция коллекций с горизонтальным рядом кнопок, создаёт вертикальный ряд кнопок

        List<InlineKeyboardButton> firstRowInlineButton = new ArrayList<>(); // коллекция с горизонтальным рядом кнопок
        List<InlineKeyboardButton> secondRowInlineButton = new ArrayList<>(); // коллекция с горизонтальным рядом кнопок
        List<InlineKeyboardButton> thirdRowInlineButton = new ArrayList<>(); // коллекция с горизонтальным рядом кнопок


        LocalDate dateFromList;
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("d MMM"); // формат даты для подписи кнопок экранной клавиатуры

        for (int i = 0; i < scheduleDays.size(); i++) {
            dateFromList = LocalDate.parse(scheduleDays.get(i));

            InlineKeyboardButton keyboardButton = new InlineKeyboardButton();
            keyboardButton.setText(dateFormatter.format(dateFromList));
            keyboardButton.setCallbackData(Strings.DATE_BUTTON + scheduleDays.get(i) + " " + doctor.getChatId());

            // размещение по 5 кнопок в каждом горизонтальном ряду
            if (i < 5) {
                firstRowInlineButton.add(keyboardButton);
            } else if (i < 10) {
                secondRowInlineButton.add(keyboardButton);
            } else if (i < 15) {
                thirdRowInlineButton.add(keyboardButton);
            }
        }

        rowsInline.add(firstRowInlineButton); // размещение набора кнопок в вертикальном ряду
        rowsInline.add(secondRowInlineButton); // размещение набора кнопок в вертикальном ряду
        rowsInline.add(thirdRowInlineButton); // размещение набора кнопок в вертикальном ряду

        inlineKeyboardMarkup.setKeyboard(rowsInline);
        editMessageText.setReplyMarkup(inlineKeyboardMarkup);
        editMessageTextExecute(editMessageText);
    }

    // Метод сопоставляет рабочий график врача и запись к врачум из Google Calendar, предоставляя возможность выбора свободного для записи к врачу времени
    private void doctorScheduleHoursButtons(String choseDate, long chatId, long messageId, String doctorId) {
        LocalDate localDate = LocalDate.parse(choseDate); // 2023-06-15
        DateTimeFormatter dayOfWeek = DateTimeFormatter.ofPattern("e"); // 4
        googleCalendar = new GoogleCalendarReceiver();
        Doctor doctor = receiveDoctorFromDb(Long.parseLong(doctorId));

        String doctorData = Objects.requireNonNull(doctor).getDoctorLastname() + " " + doctor.getDoctorFirstname() + " " + doctor.getDoctorPatronymic() + "-" + "doctor";

        TreeMap<String, String> googleCalendarSchedule = googleCalendar.receiveFromGoogleCalendar(doctorData); // формат записи ключа и значения == "2023-06-16" "08:00 09:30 12:00 13:30"

        String calendarTime = googleCalendarSchedule.get(choseDate);

        String[] hours = new String[0];

        if (calendarTime != null) { // реализация возможности добавлять 2 пациента на одно и то же время в календаре, оставляя в массиве только одно дубль-время
            hours = calendarTime.split(" ");
            for (int i = 0; i < hours.length - 1; i++) {
                if (!hours[i].equals(hours[i + 1])) {
                    hours[i] = "";
                }
            }
            hours[hours.length - 1] = "";
            String temp = Arrays.toString(hours).replace(",", "").replace("[", "").replace("]", "")
                    .replaceAll("  ", " ").replaceAll("  ", " ").trim();

            hours = temp.split(" ");
        } // реализация возможности добавлять 2 пациента на одно и то же время в календаре, оставляя в массиве только одно дубль-время

        String[] workTime = new String[2]; // workTime[0] = c 8:00   -   workTime[1] = до 16:00

        if (dayOfWeek.format(localDate).equals("1")) {
            workTime = doctor.getMondaySchedule().split("-");
        } else if (dayOfWeek.format(localDate).equals("2")) {
            workTime = doctor.getTuesdaySchedule().split("-");
        } else if (dayOfWeek.format(localDate).equals("3")) {
            workTime = doctor.getWednesdaySchedule().split("-");
        } else if (dayOfWeek.format(localDate).equals("4")) {
            workTime = doctor.getThursdaySchedule().split("-");
        } else if (dayOfWeek.format(localDate).equals("5")) {
            workTime = doctor.getFridaySchedule().split("-");
        } else if (dayOfWeek.format(localDate).equals("6")) {
            workTime = doctor.getSaturdaySchedule().split("-");
        } else if (dayOfWeek.format(localDate).equals("7")) {
            workTime = doctor.getSundaySchedule().split("-");
        }

        double beginOfWork = Double.parseDouble(workTime[0]); // workTime[0] = c 8
        double endOfWork = Double.parseDouble(workTime[1]); //  workTime[1] = до 16

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("H");
        LocalTime beginOfWorkTime = LocalTime.parse(workTime[0], dateTimeFormatter); // workTime[0] = c 8:00

        EditMessageText editMessageText = receiveEditMessageText(chatId, messageId, "Выберите время для записи");

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>(); // коллекция коллекций с горизонтальным рядом кнопок, создаёт вертикальный ряд кнопок
        List<InlineKeyboardButton> firstRowInlineButton = new ArrayList<>(); // коллекция с горизонтальным рядом кнопок
        List<InlineKeyboardButton> secondRowInlineButton = new ArrayList<>();

        int iteration = 0;

        for (double i = beginOfWork; i < endOfWork; i += 1.5) {
            if (i != beginOfWork) { // если i не равно времени начала рабочего дня врача, каждую итерацию ко времени начала рабочего дня добавляется 90 минут
                beginOfWorkTime = beginOfWorkTime.plusMinutes(90);
            }

            if (iteration == hours.length) { // если количество ячеек массива с зарезервированными часами == количеству итераций
                InlineKeyboardButton keyboardFirstButton = new InlineKeyboardButton();
                keyboardFirstButton.setText(beginOfWorkTime.toString());
                if (i < 10) {
                    keyboardFirstButton.setCallbackData(Strings.TIME_BUTTON + choseDate + "T" + beginOfWorkTime + "=" + doctorId); // "%timebutton" + 2023-06-15 + "T" + 10.30  + "=" + 123

                } else
                    keyboardFirstButton.setCallbackData(Strings.TIME_BUTTON + choseDate + "T" + beginOfWorkTime + "=" + doctorId);
                firstRowInlineButton.add(keyboardFirstButton);

            } else if (!beginOfWorkTime.toString().equals(hours[iteration])) { // массив hours = "08:00 , 10:30 , 12:00 , 13:30"
                InlineKeyboardButton keyboardSecondButton = new InlineKeyboardButton();
                keyboardSecondButton.setText(beginOfWorkTime.toString());

                if (i < 10) {
                    keyboardSecondButton.setCallbackData(Strings.TIME_BUTTON + choseDate + "T" + beginOfWorkTime + "=" + doctorId); // "%timebutton" + 2023-06-15 + "T" + 10.30  + "=" + 123

                } else
                    keyboardSecondButton.setCallbackData(Strings.TIME_BUTTON + choseDate + "T" + beginOfWorkTime + "=" + doctorId);
                firstRowInlineButton.add(keyboardSecondButton);

            } else {
                iteration++;
            }
        }
        InlineKeyboardButton secondButton = new InlineKeyboardButton();
        secondButton.setText("Выбрать другую дату");
        secondButton.setCallbackData(Strings.CHOOSE_ANOTHER + doctorId);
        secondRowInlineButton.add(secondButton);

        rowsInline.add(firstRowInlineButton); // размещение набора кнопок в ряду
        rowsInline.add(secondRowInlineButton);
        inlineKeyboardMarkup.setKeyboard(rowsInline);
        editMessageText.setReplyMarkup(inlineKeyboardMarkup);

        editMessageTextExecute(editMessageText);
    }

    // Сообщение для всех пациентов
    private void messageForAllUsers(String text) {
        Iterable<User> users = userRepository.findAll();
        for (User user : users) {
            sendMessageWithScreenKeyboard(user.getChatId(), text);
        }
    }

    // Список из кнопок с ФИО пользователей
    private void findUser(long chatId, String lastName) {
        SendMessage sendMessage = receiveCreatedMessage(chatId, "Выберите пациента из списка");
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>(); // коллекция коллекций с горизонтальным рядом кнопок, создаёт вертикальный ряд кнопок

        Iterable<User> users = userRepository.findAll();

        for (User user : users) { // Динамически расширяемое меню поиска зарегистрированных пользователей
            if (user.getLastname().toLowerCase().contains(lastName.toLowerCase())) {
                String fullUserName = user.getLastname() + " " + user.getFirstname() + " " + user.getPatronymic();
                long userChatId = user.getChatId();
                InlineKeyboardButton keyboardButton = new InlineKeyboardButton();
                keyboardButton.setText(fullUserName);
                keyboardButton.setCallbackData(Strings.FOUND_PATIENT + userChatId);
                List<InlineKeyboardButton> list = new ArrayList<>(1);
                list.add(keyboardButton);
                rowsInline.add(list);
            }
        }
        if (rowsInline.size() == 0) {
            sendMessageWithScreenKeyboard(chatId, "Пользователей с фамилией " + lastName + " не найдено");
        } else {
            inlineKeyboardMarkup.setKeyboard(rowsInline);
            sendMessage.setReplyMarkup(inlineKeyboardMarkup);
            sendMessageExecute(sendMessage);
        }
    }

    // Метод отмечает дни отпуска как нерабочие в методе doctorScheduleDaysButtons
    private HashMap<String, String> doctorVacationDaysCheck(Doctor doctor) {
        // Длинна строки setText больше длинны строки дневной записи к врачу с полностью заполненным временем записи. Т.о, строка setText не позволяет отображать день, как свободный для записи
        String setText = "<------------------------the string with length over doctors work time---------------------------->";
        String[] doctorVacation;
        HashMap<String, String> vacationTime = new HashMap<>();
        LocalDate localDate = LocalDate.now();
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        if (doctor.getVacation() != null) {
            doctorVacation = doctor.getVacation().split("    ");
        } else return vacationTime;

        for (String s : doctorVacation) {
            String[] startAndVacation = s.split(":");
            LocalDate vacationStart = LocalDate.parse(startAndVacation[0]);
            LocalDate vacationAnd = LocalDate.parse(startAndVacation[1]);

            int daysCounter;
            for (daysCounter = 0; !startAndVacation[1].equals(dateTimeFormatter.format(localDate.plusDays(daysCounter))) ^ vacationAnd.isBefore(localDate); daysCounter++) {
                if (startAndVacation[0].equals(dateTimeFormatter.format(localDate.plusDays(daysCounter))) || vacationStart.isBefore(localDate)) {
                    startAndVacation[0] = dateTimeFormatter.format(localDate.plusDays(daysCounter + 1));
                    vacationTime.put(dateTimeFormatter.format(localDate.plusDays(daysCounter)), setText);
                }
            }
        }
        return vacationTime;
    }

    // Добавление отпуска врача
    private void setDoctorVacation(long chatId, String messageText) {
        try {
            LocalDate firstDate = LocalDate.parse(tempData);
            LocalDate secondDate = LocalDate.parse(messageText);
            if (firstDate.isAfter(secondDate) || secondDate.isBefore(LocalDate.now())) {
                sendMessageWithScreenKeyboard(chatId, "Дата окончания отпуска не может быть раньше текущей даты/даты начала отпуска");
                TEMP_CHOOSE_DOCTOR.clear();
                tempData = null;
                return;
            }
        } catch (DateTimeParseException e) {
            sendMessageWithScreenKeyboard(chatId, "Неправильный ввод даты, отпуск врача не был установлен");
            TEMP_CHOOSE_DOCTOR.clear();
            tempData = null;
            return;
        }

        tempData += ":" + messageText + "    ";

        Doctor doctor = TEMP_CHOOSE_DOCTOR.get(chatId);
        if (Objects.requireNonNull(doctor).getVacation() != null) tempData += doctor.getVacation();

        doctor.setVacation(tempData);
        doctorRepository.save(doctor);
        String schedule = receiveDoctorScheduleFromDb(doctor);
        sendMessageWithScreenKeyboard(chatId, "Отпуск был добавлен.\n" + schedule);
    }

    // Запрос у администратора разрешения на регистрацию пользователя как врача
    private void requestToAdmin(String userData) {
        String[] splitUserData = userData.split(" "); // doctorId chatId
        String chatId = splitUserData[1];
        long doctorId = Long.parseLong(splitUserData[0]);
        Doctor doctor = receiveDoctorFromDb(doctorId);

        String fullName = Objects.requireNonNull(doctor).getDoctorLastname() + " " + doctor.getDoctorFirstname() + " " + doctor.getDoctorPatronymic();

        Iterable<User> users = userRepository.findAll();
        for (User admin : users) {
            if (config.adminValidation.equals(admin.getLastname() + admin.getFirstname() + admin.getPatronymic())) {

                SendMessage sendMessage = receiveCreatedMessage(admin.getChatId(), "Пользователь " + fullName + " может быть зарегистрирован как врач. Зарегистрировать пользователя?");

                InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>(); // коллекция коллекций с горизонтальным рядом кнопок, создаёт вертикальный ряд кнопок
                List<InlineKeyboardButton> firstRowInlineButton = new ArrayList<>(); // коллекция с горизонтальным рядом кнопок

                InlineKeyboardButton yesButton = new InlineKeyboardButton(); //385
                yesButton.setText("Зарегистрировать");
                yesButton.setCallbackData(Strings.REGISTER + userData); // doctorId chatId
                firstRowInlineButton.add(yesButton);

                InlineKeyboardButton noButton = new InlineKeyboardButton();
                noButton.setText("Не регистрировать");
                noButton.setCallbackData(Strings.DO_NOT_REGISTER + chatId);
                firstRowInlineButton.add(noButton);

                rowsInline.add(firstRowInlineButton); // размещение набора кнопок в ряду
                inlineKeyboardMarkup.setKeyboard(rowsInline);
                sendMessage.setReplyMarkup(inlineKeyboardMarkup);

                sendMessageExecute(sendMessage);
            }
        }
    }

    // Метод предоставляет возможность врачу видеть пациентскую запись к нему
    private void createDateButtonsForDoctor(long chatId) {
        SendMessage sendMessage;
        GoogleCalendarReceiver calendarReceiver = new GoogleCalendarReceiver();
        Doctor doctor = receiveDoctorFromDb(chatId);
        String fullDoctorName = Objects.requireNonNull(doctor).getDoctorLastname() + " " + doctor.getDoctorFirstname() + " " + doctor.getDoctorPatronymic();

        TreeMap<String, String> dateTime = calendarReceiver.receiveFromGoogleCalendar(fullDoctorName + "-doctor");
        LocalDate currentDate = LocalDate.now();

        if (dateTime.size() == 0) {
            sendMessage = receiveCreatedMessage(chatId, "Запись отсутствует");
        } else {
            InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>(); // коллекция коллекций с горизонтальным рядом кнопок, создаёт вертикальный ряд кнопок

            double iterations = 0;
            List<InlineKeyboardButton> list = new ArrayList<>();

            for (Map.Entry<String, String> map : dateTime.entrySet()) { // Динамически расширяемое меню выбора даты

                if (map.getKey().equals(currentDate.toString()) || currentDate.isBefore(LocalDate.parse(map.getKey()))) {
                    String buttonText = map.getKey();
                    InlineKeyboardButton keyboardButton = new InlineKeyboardButton();
                    keyboardButton.setText(buttonText);
                    keyboardButton.setCallbackData(Strings.BUTTON_FOR_DOCTOR + map.getKey());
                    if (iterations % 3 == 0) {
                        list = new ArrayList<>();
                        rowsInline.add(list);
                    }
                    list.add(keyboardButton);
                }
                iterations++;
            }
            sendMessage = receiveCreatedMessage(chatId, "Выберите дату для просмотра записи");
            inlineKeyboardMarkup.setKeyboard(rowsInline);
            sendMessage.setReplyMarkup(inlineKeyboardMarkup);
        }
        sendMessageExecute(sendMessage);
    }


    private void createEventTextForDoctor(long chatId, long messageId, String date) {
        char pointer = 10148;
        GoogleCalendarReceiver calendarReceiver = new GoogleCalendarReceiver();
        Doctor doctor = receiveDoctorFromDb(chatId);
        String doctorData = Objects.requireNonNull(doctor).getDoctorLastname() + " " + doctor.getDoctorFirstname() + " " + doctor.getDoctorPatronymic() + "-" + "doctorschadule";

        TreeMap<String, String> dateTime = calendarReceiver.receiveFromGoogleCalendar(doctorData);

        String timeAndPatient = "Ваша запись на " + date + ":\n\n" + pointer + " " + dateTime.get(date).replaceAll("-", "\n\n" + pointer + " ");
        timeAndPatient = timeAndPatient.substring(0, timeAndPatient.length() - 3);

        editMessageTextExecute(receiveEditMessageText(chatId, messageId, timeAndPatient));
    }

    // Метод предоставляет возможность пользователю зарегистрироваться как врачу
    private void userWantsToRegisterAs(long chatId, String phoneNumber, Doctor doctor) {
        SendMessage sendMessage = receiveCreatedMessage(chatId, doctor.getDoctorFirstname() + " " + doctor.getDoctorPatronymic() + ", вы регистрируетесь как:");

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>(); // коллекция коллекций с горизонтальным рядом кнопок, создаёт вертикальный ряд кнопок
        List<InlineKeyboardButton> firstRowInlineButton = new ArrayList<>(); // коллекция с горизонтальным рядом кнопок

        InlineKeyboardButton doctorButton = new InlineKeyboardButton();
        doctorButton.setText("Врач");
        doctorButton.setCallbackData(Strings.REG_AS_DOCTOR + doctor.getChatId() + " " + chatId);
        firstRowInlineButton.add(doctorButton);

        InlineKeyboardButton patientButton = new InlineKeyboardButton();
        patientButton.setText("Пациент");
        patientButton.setCallbackData(Strings.REG_AS_PATIENT + doctor.getChatId() + " " + phoneNumber);
        firstRowInlineButton.add(patientButton);

        rowsInline.add(firstRowInlineButton); // размещение набора кнопок в ряду
        inlineKeyboardMarkup.setKeyboard(rowsInline);
        sendMessage.setReplyMarkup(inlineKeyboardMarkup);

        sendMessageExecute(sendMessage);
    }

    // Добавление пациента в бд двумя различными путями в случае:
    // 1. если ФИО совпадают с ФИО врача из DoctorRepository
    // 2. простая регистрация пользователя
    private void setUserInDB(long chatId, String userData, String phone) {
        User user = new User();
        String[] data = userData.split(" ");

        if (data.length > 2) {
            user.setChatId(chatId);
            user.setLastname(data[0]);
            user.setFirstname(data[1]);
            user.setPatronymic(data[2]);
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));
            user.setPhoneNumber(Long.parseLong(phone));
            userRepository.save(user);
        } else {
            user.setChatId(chatId);
            user.setUserName(userData);
            user.setFirstname(FIRSTNAME.get(chatId));
            user.setPatronymic(PATRONYMIC.get(chatId));
            user.setLastname(LASTNAME.get(chatId));
            user.setPhoneNumber(Long.parseLong(phone));
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));
            userRepository.save(user);
        }
    }

    // Запись пациента к врачу в Google Calendar
    private void addNoteIntoGoogleCalendar(long chatId, String dateTime, long doctorId, String fullName) { // максимум 33 печатных символа
        String userFullName = "null";
        String doctorFullName = "null";
        String phoneNumber = "отсутствует";
        LocalDateTime localDateTime = LocalDateTime.now();
        LocalDateTime parseDate = LocalDateTime.parse(dateTime + ":00");
        long milliSeconds = ChronoUnit.MILLIS.between(localDateTime, parseDate); // разница в миллисекундах между датами

        Optional<User> user = userRepository.findById(chatId);

        if (isAdmin(chatId)) { // пациента записывает администратор
            userFullName = fullName;
        } else {
            if (user.isPresent()) {  // пациент записывается самостоятельно из меню бота
                userFullName = user.get().getLastname() + " " + user.get().getFirstname() + " " + user.get().getPatronymic();
                phoneNumber = String.valueOf(user.get().getPhoneNumber());
            }
        }

        Optional<Doctor> doctor = doctorRepository.findById(doctorId);
        if (doctor.isPresent())
            doctorFullName = doctor.get().getDoctorLastname() + " " + doctor.get().getDoctorFirstname() + " " + doctor.get().getDoctorPatronymic();
        CalendarEventSetter calendarEventSetter = new CalendarEventSetter();
        String doctorAndPatientsNames = doctorFullName + "-" + userFullName;

        try {
            calendarEventSetter.setToCalendar(milliSeconds, doctorAndPatientsNames);
        } catch (IOException | GeneralSecurityException e) {
            log.error("calendarEventSetter error: " + e.getMessage());
            throw new RuntimeException(e);
        }

        String messageForAdmin = "Приём у доктора: " + doctorFullName + "-" + userFullName + " (пациент), запись на " + dateTime.replace("T", " в ") + " . " +
                "Подтвердите, пожалуйста, запись и проверьте её наличие в календаре приёма. Номер телефона пациента: " + phoneNumber;

        Iterable<User> users = userRepository.findAll();
        for (User admin : users) {
            if (config.adminValidation.equals(admin.getLastname() + admin.getFirstname() + admin.getPatronymic())) {
                sendMessageWithScreenKeyboard(admin.getChatId(), messageForAdmin);
            }
        }
    }


    private Doctor receiveDoctorFromDb(long doctorId) {
        Iterable<Doctor> doctors = doctorRepository.findAll();
        for (Doctor doctor : doctors) {
            if (doctor.getChatId() == doctorId) {
                return doctor;
            }
        }
        return null;
    }

    // метод создаёт и возвращает экземпляр сообщения
    private SendMessage receiveCreatedMessage(long chatId, String messageText) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(messageText);
        return sendMessage;
    }

    // метод создаёт и возвращает экземпляр изменённого сообщения
    private EditMessageText receiveEditMessageText(long chatId, long messageId, String messageText) {
        EditMessageText editMessageText = new EditMessageText();
        editMessageText.setChatId(chatId);
        editMessageText.setMessageId((int) messageId);
        editMessageText.setText(messageText);
        return editMessageText;
    }

    // отправляет пользователю экземпляр сообщения
    private void sendMessageExecute(SendMessage sendMessage) {
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("SendMessage execute error: " + e.getMessage());
        }
    }

    // отправляет пользователю экземпляр изменённого сообщения
    private void editMessageTextExecute(EditMessageText editMessageText) {
        try {
            execute(editMessageText);
        } catch (TelegramApiException e) {
            log.error("SendMessage execute error: " + e.getMessage());
        }
    }


    private boolean isAdmin(long chatId) {
        Optional<User> user = userRepository.findById(chatId);
        if (user.isPresent()) {
            String fullName = user.get().getLastname() + user.get().getFirstname() + user.get().getPatronymic();
            return fullName.equals(config.adminValidation);
        }
        return false;
    }

    // Удаление данных из map после регистрации пользователя/врача
    private void clearData(long chatId) {
        BLOCK_DEFAULT_MESSAGE_VALUE.remove(chatId);
        LASTNAME.remove(chatId);
        FIRSTNAME.remove(chatId);
        PATRONYMIC.remove(chatId);
        PHONE_NUMBER.remove(chatId);
    }

    // один раз в фиксированный интервал времени метод удаляет данные из map, в которой один пользователь сохраняет время записи к врачу,
    // чтобы другой пользователь не имел возможности записи, пока не delayBeforeClear() не очистит avoidTimeCollision
    @Scheduled(fixedDelay = 60000)
    private void delayBeforeClear() {
        AVOID_REGISTER_COLLISION.clear();
    }

    // один раз в фиксированный интервал времени метод удаляет данные из map
    @Scheduled(cron = "0 0 3 * * *")
    private void clearAllHashMaps() {
        LASTNAME.clear();
        FIRSTNAME.clear();
        PATRONYMIC.clear();
        PHONE_NUMBER.clear();
        TEMP_CHOOSE_DOCTOR.clear();
        DOCTOR_SCHEDULE_LIST.clear();
        AVOID_REGISTER_COLLISION.clear();
        BLOCK_DEFAULT_MESSAGE_VALUE.clear();
    }

    // Метод отправляет сообщение пациенту заблаговременно до приёма, напоминая о записи к врачу
    @Scheduled(cron = Config.TIME_SETTING) // настройки таймера вынесены в Config
    private void sendMessageAboutVisit() {
        LocalDateTime localDateTime = LocalDateTime.now();
        DateTimeFormatter yearMonthDay = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        StringBuilder textForMessage = new StringBuilder();

        googleCalendar = new GoogleCalendarReceiver();
        TreeMap<String, String> googleCalendarSchedule;

        Iterable<User> users = userRepository.findAll();
        for (User user : users) {
            String fullName = user.getLastname() + " " + user.getFirstname() + " " + user.getPatronymic();
            googleCalendarSchedule = googleCalendar.receiveFromGoogleCalendar(fullName);
            // если приём у пациента назначен на следующий по отношению к текущему день, в установленное время происходит отправка сообщения
            if (googleCalendarSchedule.get(fullName) != null && googleCalendarSchedule.get(fullName).contains(yearMonthDay.format(localDateTime.plusDays(1)))) {
                String[] timeText = googleCalendarSchedule.get(fullName).split(";");

                for (int i = 0; i < timeText.length; i++) {
                    if (i != 0 && timeText[i].length() > 1) {
                        textForMessage.append("Дальнейшая запись: ").append(timeText[i]);
                    } else {
                        if (i == 0) textForMessage.append(timeText[i]).append("\n");
                    }
                }
                sendMessageWithScreenKeyboard(user.getChatId(), "Здравствуйте! Напоминаем, что завтра у вас будет приём у врача: " + textForMessage);
            }
            textForMessage = new StringBuilder();
        }
    }


}





