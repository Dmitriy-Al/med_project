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
//    import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    private final DoctorRepository doctorRepository;

    @Autowired
    public UserRepository userRepository;
    private GoogleCalendarReceiver googleCalendar;

    private int tempDayOfWeek;
    private final Config config;
    private String tempData = null;
    private String tempHour = null; // TODO поменять на tempData
    private String doctorSpeciality = null; // TODO поменять на tempData

    private final HashMap<Long, String> LASTNAME = new HashMap<>(); // размещение данных пользователей в паре ключ-значение сделано для того,
    private final HashMap<Long, String> FIRSTNAME = new HashMap<>(); // чтобы при регистрации нескольких пользователей одновременно не происходило смешение вводимых данных
    private final HashMap<Long, String> PATRONYMIC = new HashMap<>();
    private final HashMap<Long, String> PHONE_NUMBER = new HashMap<>();

    private final HashMap<Long, Doctor> TEMP_CHOOSE_DOCTOR = new HashMap<>(); // выбранный доктор
    private final HashMap<Integer, String> DOCTOR_SCHEDULE_LIST = new HashMap<>(8); // доктор и расписание доктора, помещаемое в бд
    private final HashMap<Long, String> AVOID_REGISTER_COLLISION = new HashMap<>();
    private final HashMap<Long, Integer> BLOCK_DEFAULT_MESSAGE_VALUE = new HashMap<>(); //


    public TelegramBot(Config config, DoctorRepository doctorRepository) {
        super(config.botToken);
        this.config = config;
        this.doctorRepository = doctorRepository;

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
                case "/start" -> {
                    Optional<User> user = userRepository.findById(update.getMessage().getChatId());
                    Optional<Doctor> doctor = doctorRepository.findById(update.getMessage().getChatId());
                    if (user.isEmpty() && doctor.isEmpty()) {
                        BLOCK_DEFAULT_MESSAGE_VALUE.remove(chatId); // если по какой-то причине регистрация была прервана и начата заново, процесс начинается с начала
                        startMessage(chatId); // если пользователь не зарегистрирован, вызывается метод блокирующий отправку ботом дефолтных сообщений, чтобы пользователь вводил данные без получения default message
                    } else {
                        messageFromBot(chatId, "Вы являетесь зарегистрированным пользователем");
                    }
                }
                case "/mydata" -> {
                    Optional<User> user = userRepository.findById(chatId);
                    String userData = user.toString().replace("Optional[", "").replace("]", "");
                    messageFromBot(chatId, userData);
                }
                case "/deletedata" -> {
                    Optional<Doctor> doctor = doctorRepository.findById(chatId);
                    if (doctor.isPresent()) {
                        messageFromBot(chatId, "Удалить учётную запись врача может только администратор");
                    } else {
                        Optional<User> user = userRepository.findById(chatId);
                        user.ifPresent(value -> userRepository.delete(value));
                        sendMessageExecute(receiveCreatedMessage(chatId, "Вся информация о пользователе была удалена"));
                    }
                }
                case "/settings" -> {
                    messageFromBot(chatId, "Меню настроек для данного профиля отсутствует");
                }
                case "Записаться на приём к врачу" -> {
                    if (userRepository.findById(chatId).isPresent()) {
                        setDoctorsOnButtons(chatId, Strings.CHOOSE_DOCTOR);
                    } else messageFromBot(chatId, "Пожалуйста зарегистрируйтесь, чтобы иметь возможность записаться к врачу");
                }
                case "Посмотреть мою запись" -> {
                    Optional<User> user = userRepository.findById(chatId);
                    if (user.isEmpty()) {
                        messageFromBot(chatId, "Пожалуйста зарегистрируйтесь, чтобы иметь возможность просмотра записи");
                        return;
                    } else {
                        String userData = user.get().getLastname() + " " + user.get().getFirstname() + " " + user.get().getPatronymic() + "-" + "patient";
                        String fullName = user.get().getLastname() + " " + user.get().getFirstname() + " " + user.get().getPatronymic();
                        googleCalendar = new GoogleCalendarReceiver();
                        TreeMap<String, String> schedule = googleCalendar.receiveFromGoogleCalendar(userData);

                        if (schedule.get(fullName) == null) {
                            messageFromBot(chatId, "Здравствуйте! У вас отсутствует приём у врача");
                        } else {
                            String text = "Здравствуйте! Ваш доктор: \n" + schedule.get(fullName);
                            messageFromBot(chatId, text);
                        }
                    }
                }
                case "Добавить врача" -> {
                    if (isAdmin(chatId)) {
                        BLOCK_DEFAULT_MESSAGE_VALUE.put(chatId, 5); // техническое поле, не-null значение которого блокирует отправку дефолтных сообщений бота
                    } else messageFromBot(chatId, Strings.COMMAND_DOES_NOT_EXIST);
                }
                case "Удалить врача" -> {
                    if (isAdmin(chatId)) {
                        setDoctorsOnButtons(chatId, Strings.DELETE_DOCTOR);
                    } else messageFromBot(chatId, Strings.COMMAND_DOES_NOT_EXIST);
                }
                case "Просмотр расписания врачей" -> {
                    if (isAdmin(chatId)) {
                        setDoctorsOnButtons(chatId, Strings.OBSERVE_SCHEDULE);
                    } else messageFromBot(chatId, Strings.COMMAND_DOES_NOT_EXIST);
                }
                case "Редактировать расписание врачей" -> {
                    if (isAdmin(chatId)) {
                        setDoctorsOnButtons(chatId, Strings.EDITE_DOCTOR);
                    } else messageFromBot(chatId, Strings.COMMAND_DOES_NOT_EXIST);
                }
                case "Меню для работы с базой пациентов" -> {
                    if (isAdmin(chatId)) {
                        workWithPatientMenu(chatId);
                    } else messageFromBot(chatId, Strings.COMMAND_DOES_NOT_EXIST);
                }
                case "Мой рабочий график" -> {
                    Optional<Doctor> doctor = doctorRepository.findById(chatId);
                    if (doctor.isPresent()) {
                        String doctorScheduleFromDb = receiveDoctorScheduleFromDb(Objects.requireNonNull(receiveDoctorFromDb(chatId)));
                        messageFromBot(chatId, doctorScheduleFromDb);
                    } else {
                        messageFromBot(chatId, Strings.COMMAND_DOES_NOT_EXIST);
                    }
                }
                case "Моя запись" -> {
                    Optional<Doctor> doctor = doctorRepository.findById(chatId);
                    if (doctor.isPresent()) {
                        createDateButtonsForDoctor(chatId);
                    } else {
                        String text = "Такая команда отсутствует";
                        messageFromBot(chatId, text);
                    }
                }
                case "Записать пациента к врачу" -> {
                    if (isAdmin(chatId)) {
                        BLOCK_DEFAULT_MESSAGE_VALUE.put(chatId, 10);
                    } else messageFromBot(chatId, Strings.COMMAND_DOES_NOT_EXIST);
                }
                default -> {
                    if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == null) {
                        messageFromBot(chatId, Strings.COMMAND_DOES_NOT_EXIST);
                    }
                }
            }

            if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 1) { //         логика регистрации пользователя: после команды /start блокируется отправка дефолтных сообщений
                LASTNAME.put(chatId, messageText); //                после чего пользователь начинает поэтапно вводить данные, а hashMap использует в качестве ключей
                BLOCK_DEFAULT_MESSAGE_VALUE.replace(chatId, 2); //         уникальный идентификатор chatId. Метод userRegistrationSteps "запоминает" пройденный этап регистрации
                messageFromBot(chatId, "Введите ваше имя и отправьте сообщение"); //       и переносит пользователя на следующий, количество этапов == 4
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 2) {
                FIRSTNAME.put(chatId, messageText);
                BLOCK_DEFAULT_MESSAGE_VALUE.replace(chatId, 3);
                messageFromBot(chatId, "Введите ваше отчество и отправьте сообщение");
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 3) {
                PATRONYMIC.put(chatId, messageText);
                BLOCK_DEFAULT_MESSAGE_VALUE.replace(chatId, 4);
                messageFromBot(chatId, "Введите ваш номер телефона в формате 8xxxxxxxxxx и отправьте сообщение");
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 4) {
                PHONE_NUMBER.put(chatId, messageText);
                addUser(update.getMessage());
                BLOCK_DEFAULT_MESSAGE_VALUE.remove(chatId);
                LASTNAME.remove(chatId);
                FIRSTNAME.remove(chatId);
                PATRONYMIC.remove(chatId);
                PHONE_NUMBER.remove(chatId);
                Optional<User> user = userRepository.findById(chatId);
                user.ifPresent(value -> messageFromBot(chatId, value.getFirstname() + " " + value.getPatronymic() + ", спасибо за регистрацию!"));
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 5) {  // для регистрации доктора в базе данных в процессе ввода фио и специализации блокируется отправка дефолтных сообщений
                messageFromBot(chatId, "Введите фамилию врача, затем отправьте сообщение");
                BLOCK_DEFAULT_MESSAGE_VALUE.replace(chatId, 6);
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 6) {  // для регистрации доктора в базе данных в процессе ввода фио и специализации блокируется отправка дефолтных сообщений
                LASTNAME.put(chatId, messageText);
                BLOCK_DEFAULT_MESSAGE_VALUE.replace(chatId, 7);
                messageFromBot(chatId, "Введите имя врача, затем отправьте сообщение");
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 7) {
                FIRSTNAME.put(chatId, messageText);
                BLOCK_DEFAULT_MESSAGE_VALUE.replace(chatId, 8);
                messageFromBot(chatId, "Введите отчество врача, затем отправьте сообщение");
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 8) {
                PATRONYMIC.put(chatId, messageText);
                BLOCK_DEFAULT_MESSAGE_VALUE.replace(chatId, 9);
                messageFromBot(chatId, "Введите специализацию врача, затем отправьте сообщение");
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 9) {
                doctorSpeciality = messageText.toLowerCase();
                if (doctorSpeciality.length() > 2 && LASTNAME.get(chatId).length() > 2 && FIRSTNAME.get(chatId).length() > 2 && PATRONYMIC.get(chatId).length() > 2) {
                    addDoctorInDb(chatId);
                    messageFromBot(chatId, "Доктор зарегистрирован в базе данных");
                } else {
                    messageFromBot(chatId, "Ошибка ввода, ФИО не должны быть короче трёх символов");
                }
                BLOCK_DEFAULT_MESSAGE_VALUE.remove(chatId);
                LASTNAME.remove(chatId);
                FIRSTNAME.remove(chatId);
                PATRONYMIC.remove(chatId);
                BLOCK_DEFAULT_MESSAGE_VALUE.remove(chatId);
                doctorSpeciality = null;
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 10) {
                messageFromBot(chatId, "Введите ФИО пациента (пример: Иванов Иван Иванович) и отправьте сообщение ");
                BLOCK_DEFAULT_MESSAGE_VALUE.put(chatId, 11);

            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 11) {
                BLOCK_DEFAULT_MESSAGE_VALUE.remove(chatId);
                if (messageText.split(" ").length != 3) {
                    messageFromBot(chatId, "Ошибка при вводе ФИО пациента");
                } else {
                    tempData = messageText;
                    setDoctorsOnButtons(chatId, Strings.CHOOSE_DOCTOR);
                }
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 12) {
                findUser(chatId, messageText);
                BLOCK_DEFAULT_MESSAGE_VALUE.remove(chatId);
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 13) {
                char symbol = 8252;
                long chatIdFromTempData = Long.parseLong(tempData);
                messageFromBot(chatIdFromTempData, symbol + " Сообщение от администратора " + symbol + "\n" + messageText);
                messageFromBot(chatId, "Сообщение отправлено");
                tempData = null;
                BLOCK_DEFAULT_MESSAGE_VALUE.remove(chatId);
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 14) {
                messageForAllUsers(messageText);
                messageFromBot(chatId, "Это сообщение отправлено всем зарегистрированным пользователям");
                BLOCK_DEFAULT_MESSAGE_VALUE.remove(chatId);
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 15) {
                tempData = messageText;
                messageFromBot(chatId, "Введите дату выхода врача на работу - год-месяц-число в формате 2023-05-09");
                BLOCK_DEFAULT_MESSAGE_VALUE.put(chatId, 16);
            } else if (BLOCK_DEFAULT_MESSAGE_VALUE.get(chatId) == 16) {
                setDoctorVacation(chatId, messageText);
                tempData = null;
                DOCTOR_SCHEDULE_LIST.clear();
                BLOCK_DEFAULT_MESSAGE_VALUE.remove(chatId);
            }


        } else if (update.hasCallbackQuery()) {
            long messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            String callbackData = update.getCallbackQuery().getData();

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
                doctorWorkHourKeyboard(chatId, messageId, dayAndDoctorId[1]);

            } else if (callbackData.contains(Strings.CHOOSE_TIME)) { // запускает метод doctorWorkHourKeyboard для установки времени приёма врача в формате "00ч-00ч"
                String[] timeAndDoctorId = callbackData.replace(Strings.CHOOSE_TIME, "").split(" ");
                String doctorId = timeAndDoctorId[1];

                if (tempHour == null) {
                    tempHour = timeAndDoctorId[0]; //  "-" + timeAndDoctorId[0];
                    editMessageTextExecute(receiveEditMessageText(chatId, messageId, ""));
                    doctorWorkHourKeyboard(chatId, messageId, doctorId);
                } else {
                    if (Integer.parseInt(tempHour) > Integer.parseInt(timeAndDoctorId[0])) {
                        tempHour = null;
                        DOCTOR_SCHEDULE_LIST.clear();
                        editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Время начала приёма не может быть позднее времени окончания приёма!"));
                    } else {
                        tempHour += "-" + timeAndDoctorId[0];
                        doctorWorkWeekKeyboard(chatId, messageId, doctorId);
                        DOCTOR_SCHEDULE_LIST.put(tempDayOfWeek, tempHour);
                        tempHour = null;
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
                        addNoteInGoogleCalendar(chatId, dateTime, doctorId, fullName);
                        tempData = null;
                        editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Запись добавлена в календарь"));
                        AVOID_REGISTER_COLLISION.remove(chatId);
                    } else {
                        addNoteInGoogleCalendar(chatId, dateTime, doctorId, "");
                        editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Спасибо, мы будем ждать вас! Для уточнения деталей записи с вами может связаться администратор"));
                        AVOID_REGISTER_COLLISION.remove(chatId);
                    }
                }

            } else if (callbackData.contains(Strings.FOUND_PATIENT) && tempData == null) {
                BLOCK_DEFAULT_MESSAGE_VALUE.put(chatId, 13);
                editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Введите текст сообщения, затем отправьте его получателю"));

            } else if (callbackData.contains(Strings.FOUND_PATIENT) && tempData.equals("find user for getting info about him")) {
                long chatIdFromString = Long.parseLong(callbackData.replace(Strings.FOUND_PATIENT, ""));
                Optional<User> user = userRepository.findById(chatIdFromString);
                String infoAboutUser = user.toString().replace("Optional[", "").replace("]", "");
                editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Информация о пациенте:"));
                messageFromBot(chatId, infoAboutUser);
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
                messageFromBot(userChatId, newDoctor.getDoctorFirstname() + " " + newDoctor.getDoctorPatronymic() + ", вы зарегистрированы как врач");

            } else if (callbackData.contains(Strings.SAVE_SCHEDULE)) {
                long doctorId = Long.parseLong(callbackData.replace(Strings.SAVE_SCHEDULE, ""));
                setDoctorScheduleInDB(Objects.requireNonNull(receiveDoctorFromDb(doctorId)));
                String doctorScheduleFromDb = receiveDoctorScheduleFromDb(Objects.requireNonNull(receiveDoctorFromDb(doctorId)));
                DOCTOR_SCHEDULE_LIST.clear();
                editMessageTextExecute(receiveEditMessageText(chatId, messageId, doctorScheduleFromDb));

            } else if (callbackData.contains(Strings.DO_NOT_REGISTER)) {
                long userChatId = Long.parseLong(callbackData.replace(Strings.DO_NOT_REGISTER, ""));
                messageFromBot(userChatId, "Ваша заявка на регистрацию отклонена");
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
                editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Спасибо за регистрацию!"));

            } else if (callbackData.contains(Strings.SET_VACATION)) {
                long doctorId = Long.parseLong(callbackData.replace(Strings.SET_VACATION, ""));
                TEMP_CHOOSE_DOCTOR.put(chatId, receiveDoctorFromDb(doctorId));
                editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Введите дату начала отпуска (больничного/отгула) год-месяц-число в формате 2023-05-09"));
                BLOCK_DEFAULT_MESSAGE_VALUE.put(chatId, 15);

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
                // метод receiveEditMessage(chatId, messageId) создаёт и возвращает экземпляр EditMessage
                // метод receiveEditMessageText(chatId, messageId, text) создаёт и возвращает экземпляр EditMessage текстом для продолжения диалога
                case Strings.YES_BUTTON -> {
                    editMessageTextExecute(receiveEditMessageText(chatId, messageId, Strings.INTRODUCE_TEXT));
                    BLOCK_DEFAULT_MESSAGE_VALUE.put(chatId, 1);
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
                    } else messageFromBot(chatId, "Такая команда отсутствует");
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
                case Strings.USER_MESSAGE -> {
                    BLOCK_DEFAULT_MESSAGE_VALUE.put(chatId, 12);
                    editMessageTextExecute(receiveEditMessageText(chatId, messageId, "Введите фамилию/часть фамилии пациента и отправьте сообщение"));
                }
            }
        }
    }


    @Override
    public String getBotUsername() {
        return config.BotName;
    }


    public void startMessage(long chatId) { // регистрация пользователя и вызов метода addUserInDB() для добавления пользователя в дб
        SendMessage sendMessage = new SendMessage(); // TODO
        sendMessage.setChatId(chatId);
        sendMessage.setText("Здравствуйте! Зарегистрированные пользователи имеют возможность записи к врачу из меню и просмотр своей записи. Желаете зарегистрироваться?");

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


    private void addUser(Message message) { // добавления пользователя в дб
        if (userRepository.findById(message.getChatId()).isEmpty()) {
            Chat chat = message.getChat();
            long chatId = message.getChatId();
            long phone = 0;

            try {
                phone = Long.parseLong(PHONE_NUMBER.get(chatId));
            } catch (NumberFormatException e) {
                log.info("SendMessage execute error: " + e.getMessage());
            }
            Iterable<User> users = userRepository.findAll();
            for (User userFromDb : users) {
                if (FIRSTNAME.get(chatId).length() < 3 || LASTNAME.get(chatId).length() < 3 || PATRONYMIC.get(chatId).length() < 3 || phone == 0 || userFromDb.getLastname().equals("Admin") &&
                        LASTNAME.get(chatId).equals("Admin") || userFromDb.getFirstname().equals("Admin") &&
                        FIRSTNAME.get(chatId).equals("Admin") || userFromDb.getPatronymic().equals("Admin") && PATRONYMIC.get(chatId).equals("Admin")) {
                    String text = "Регистрация с такими личными данными запрещена. Длинна фамилии, имени, отчества должны быть не менее трёх символов, телефонный номер прописывается цифровыми символами";
                    messageFromBot(chatId, text);
                    LASTNAME.remove(chatId);
                    FIRSTNAME.remove(chatId);
                    PATRONYMIC.remove(chatId);
                    PHONE_NUMBER.remove(chatId);
                    BLOCK_DEFAULT_MESSAGE_VALUE.remove(chatId);
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
        }
    }


    private void doctorKeyBoard(SendMessage sendMessage) { // кнопки экранной клавиатуры
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setResizeKeyboard(true);
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add("Моя запись");
        row.add("Мой рабочий график");
        keyboardRows.add(row);
        replyKeyboardMarkup.setKeyboard(keyboardRows);
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
    }


    private void userKeyBoard(SendMessage sendMessage) { // кнопки экранной клавиатуры
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
        //Ответ был найден методом проб и ошибок. 1) Ставим в методе sendMsg на параметре replyKeyboardMarkup.setOneTimeKeyboard - (true);
        // 2) На каждый case создаем отдельный метод. На команду 1 - sendMsgcom1 с одним набором кнопок, на команду 2 - sensMsg2 с другим набором кнопок.
    }


    public void adminKeyBoard(SendMessage sendMessage) { // кнопки экранной клавиатуры
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setResizeKeyboard(true); // уменьшение кнопок экранной клавиатуры
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


    public void messageFromBot(long chatId, String text) { // метод отправляет сообщения и устанавливает разную экранную клавиатуру разным группам пользователей
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);

        Iterable<User> users = userRepository.findAll(); // все пользователи из бд

        for (User user : users) {
            if (user.getChatId() == chatId && !(config.adminValidation.equals(user.getLastname() + user.getFirstname() + user.getPatronymic()))) {
                userKeyBoard(sendMessage); // экранная клавиатура для зарегистрированных пользователей
            } else if (user.getChatId() == chatId && config.adminValidation.equals(user.getLastname() + user.getFirstname() + user.getPatronymic())) {
                adminKeyBoard(sendMessage); // экранная клавиатура для администратора
            }
        }
        Optional<Doctor> doctor = doctorRepository.findById(chatId);
        if (doctor.isPresent()) {
            doctorKeyBoard(sendMessage);
        }
        sendMessageExecute(sendMessage); // экранная клавиатура для незарегистрированных пользователей отсутствует
    }


    private void setDoctorsOnButtons(long chatId, String orderText) { // String orderText переотправляется два типа: для удаления врача "%deletedoctor" и для редактирования графика - "%editedoctor"
        SendMessage sendMessage = new SendMessage();// TODO убрать SendMessage sendMessage
        sendMessage.setChatId(chatId);

        Iterable<Doctor> doctors = doctorRepository.findAll();

        if (doctorRepository.count() == 0) {
            messageFromBot(chatId, "Нет врачей для выбора");
        } else {
            String chooseOrderText = switch (orderText) {
                case Strings.DELETE_DOCTOR -> "Выберите врача, которого хотите удалить из базы";
                case Strings.EDITE_DOCTOR -> "Выберите врача, расписание которого хотите редактировать";
                case Strings.OBSERVE_SCHEDULE -> "Выберите врача, расписание которого хотите посмотреть";
                case Strings.CHOOSE_DOCTOR -> "Выберите врача, к которому желаете записаться";
                default -> "Err";
            };

            sendMessage.setText(chooseOrderText);
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


    private void doctorWorkHourKeyboard(long chatId, long messageId, String doctorId) {
        String doctorWorkDayText;

        if (tempHour == null) {
            doctorWorkDayText = "Выберите время начала рабочего дня врача (час)";
        } else {
            doctorWorkDayText = "Выберите время окончания рабочего дня врача (час)";
        }

        EditMessageText editMessageText = receiveEditMessageText(chatId, messageId, doctorWorkDayText); // решение тестовое
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>(); // коллекция коллекций с горизонтальным рядом кнопок, создаёт вертикальный ряд кнопок
        List<InlineKeyboardButton> firstRowInlineButton = new ArrayList<>(); // коллекция с горизонтальным рядом кнопок
        List<InlineKeyboardButton> secondRowInlineButton = new ArrayList<>(); // коллекция с горизонтальным рядом кнопок

        for (int i = 0; i < 14; i++) {
            InlineKeyboardButton keyboardButton = new InlineKeyboardButton();
            keyboardButton.setText((String.valueOf(i + 8))); // часы работы врача берутся из цикла и выводится на кнопки, отсчёт с 8 часов
            keyboardButton.setCallbackData(Strings.CHOOSE_TIME + (i + 8) + " " + doctorId);
            if (i < 7) {
                firstRowInlineButton.add(keyboardButton);
            } else secondRowInlineButton.add(keyboardButton);
        }

        rowsInline.add(firstRowInlineButton); // размещение набора кнопок в вертикальном ряду
        rowsInline.add(secondRowInlineButton); // размещение набора кнопок в вертикальном ряду
        inlineKeyboardMarkup.setKeyboard(rowsInline);
        editMessageText.setReplyMarkup(inlineKeyboardMarkup);

        editMessageTextExecute(editMessageText);
    }


    private void addDoctorInDb(long chatId) {
        Doctor newDoctor = new Doctor();
        long doctorChatId = 1;

        Iterable<Doctor> doctors = doctorRepository.findAll();
        for (Doctor doctor : doctors) {
            if (doctorChatId != doctor.getChatId()) break;
            doctorChatId++;
        }
        //@GeneratedValue(strategy = GenerationType.AUTO) не применима, поскольку при регистрации доктора необходимо заново устанавливать новый Id
        newDoctor.setChatId(doctorChatId);
        newDoctor.setDoctorLastname(LASTNAME.get(chatId));
        newDoctor.setDoctorFirstname(FIRSTNAME.get(chatId));
        newDoctor.setDoctorPatronymic(PATRONYMIC.get(chatId));
        newDoctor.setSpeciality(doctorSpeciality);
        doctorRepository.save(newDoctor);
    }


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


    private void controlQuestionButton(String dateTime, long chatId, long messageId, String doctorId) {
        EditMessageText editMessageText = receiveEditMessageText(chatId, messageId, "Подтвердите, пожалуйста, запись к врачу");
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>(); // коллекция коллекций с горизонтальным рядом кнопок, создаёт вертикальный ряд кнопок
        List<InlineKeyboardButton> firstRowInlineButton = new ArrayList<>(); // коллекция с горизонтальным рядом кнопок верхнего ряда

        InlineKeyboardButton sureButton = new InlineKeyboardButton();
        sureButton.setText("Подтвердить");
        sureButton.setCallbackData(Strings.I_AM_SURE + dateTime + " " + doctorId); // запись мз поля tempSetDoctor, для быстрого возврата к дате в расписании доктора из метода chooseTime()
        firstRowInlineButton.add(sureButton);

        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("Отменить");
        cancelButton.setCallbackData(Strings.I_AM_NOT_SURE);
        firstRowInlineButton.add(cancelButton);
        rowsInline.add(firstRowInlineButton); // размещение набора кнопок в вертикальном ряду

        inlineKeyboardMarkup.setKeyboard(rowsInline);
        editMessageText.setReplyMarkup(inlineKeyboardMarkup);

        editMessageTextExecute(editMessageText);
    }


    private void workWithPatientMenu(long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(Strings.PATIENT_MENU_TEXT);

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>(); // коллекция коллекций с горизонтальным рядом кнопок, создаёт вертикальный ряд кнопок

        List<InlineKeyboardButton> firstRowInlineButton = new ArrayList<>(); // коллекция с горизонтальным рядом кнопок верхнего ряда
        List<InlineKeyboardButton> secondRowInlineButton = new ArrayList<>();
        List<InlineKeyboardButton> thirdRowInlineButton = new ArrayList<>();
        List<InlineKeyboardButton> fourthRowInlineButton = new ArrayList<>();

        InlineKeyboardButton firstButton = new InlineKeyboardButton(); // кнопка
        firstButton.setText("Список всех пациентов");
        firstButton.setCallbackData(Strings.ALL_USER_LIST);

        InlineKeyboardButton secondButton = new InlineKeyboardButton(); // кнопка
        secondButton.setText("Написать сообщение пациенту");
        secondButton.setCallbackData(Strings.USER_MESSAGE);

        InlineKeyboardButton thirdButton = new InlineKeyboardButton(); // кнопка
        thirdButton.setText("Сообщение для всех пациентов");
        thirdButton.setCallbackData(Strings.FOR_ALL_USER_MESSAGE);

        InlineKeyboardButton fourthButton = new InlineKeyboardButton(); // кнопка
        fourthButton.setText("Посмотреть информацию о пациенте");
        fourthButton.setCallbackData(Strings.INFO_ABOUT_USER);


        firstRowInlineButton.add(firstButton);
        secondRowInlineButton.add(secondButton);
        thirdRowInlineButton.add(thirdButton);
        fourthRowInlineButton.add(fourthButton);

        rowsInline.add(firstRowInlineButton); // размещение набора кнопок в вертикальном ряду
        rowsInline.add(secondRowInlineButton); // размещение набора кнопок в вертикальном ряду
        rowsInline.add(thirdRowInlineButton); // размещение набора кнопок в вертикальном ряду
        rowsInline.add(fourthRowInlineButton);

        inlineKeyboardMarkup.setKeyboard(rowsInline);
        sendMessage.setReplyMarkup(inlineKeyboardMarkup);

        sendMessageExecute(sendMessage);
    }


    private void doctorScheduleDaysButtons(Doctor doctor, long chatId, long messageId) {
        ArrayList<String> ScheduleDays = new ArrayList<>(25);
        LocalDate localDate = LocalDate.now();
        googleCalendar = new GoogleCalendarReceiver();

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd"); // формат даты для сравнения с датами календаря Google
        DateTimeFormatter weekFormatter = DateTimeFormatter.ofPattern("e"); // формат даты для работы с днём недели в цикле

        TreeMap<String, String> googleCalendarSchedule = new TreeMap<>();
        TreeMap<String, String> tempSchedule = googleCalendar.receiveFromGoogleCalendar(doctor.getDoctorLastname() + " " + doctor.getDoctorFirstname() + " " + doctor.getDoctorPatronymic() + "-" + "doctor");

        for (Map.Entry<String, String> map : tempSchedule.entrySet()) { // реализация возможности добавлять 2 пациента на одно и то же время в календаре, оставляя в map только одно дубль-время
            String[] time = map.getValue().split(" ");
            for (int i = 0; i < time.length - 1; i++) {
                if (!time[i].equals(time[i + 1])) {
                    time[i] = "";
                }
            }
            time[time.length - 1] = "";
            map.setValue(Arrays.toString(time).replace(",", "").replace("[", "").replace("]", "").replaceAll("  ", " "));
        } // реализация возможности добавлять 2 пациента на одно и то же время в календаре

        googleCalendarSchedule.putAll(tempSchedule);
        googleCalendarSchedule.putAll(doctorVacationDaysCheck(doctor));

        String setTodayText = "<------------------------the string with length over doctors work time---------------------------->";
        googleCalendarSchedule.put(dateTimeFormatter.format(localDate), setTodayText); // строка длинной более x символов добавляется в HashMap для того, чтобы не отображать текущий день на кнопке


        int dayOfWeek = Integer.parseInt(weekFormatter.format(localDate));
        int day = 0;

        // Цикл сверяет недельный график доктора с записью в календаре Google и находит свободные для записи дни, помещая их в ArrayList
        for (int i = 0; i < 30; i++) { // TODO: посчитать количество итераций
            if (dayOfWeek == 1 && doctor.getMondaySchedule() != null && ScheduleDays.size() < 26) { // соответствие дня недели в цикле и текущего/итерационного && если в этот день недели есть приём у доктора
                String[] workHours = doctor.getMondaySchedule().split("-");
                int workTime = Integer.parseInt(workHours[1]) - Integer.parseInt(workHours[0]); // продолжительность рабочего дня врача

                if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))) == null) { // если рабочий день полностью свободен, он записывается в ArrayList
                    ScheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                } else if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))).length() < workTime * 4) { // каждая запись в расписании календаря == 6 символам, если длинна строки с расписанием меньше рабочего времени * 4, рабочий день записывается в ArrayList
                    ScheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                }
            }
            if (dayOfWeek == 1) {
                day++;
                dayOfWeek++;
            }

            if (dayOfWeek == 2 && doctor.getTuesdaySchedule() != null && ScheduleDays.size() < 26) {
                String[] workHours = doctor.getTuesdaySchedule().split("-");
                int workTime = Integer.parseInt(workHours[1]) - Integer.parseInt(workHours[0]);

                if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))) == null) {
                    ScheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                } else if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))).length() < workTime * 4) {
                    ScheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                }
            }
            if (dayOfWeek == 2) {
                day++;
                dayOfWeek++;
            }

            if (dayOfWeek == 3 && doctor.getWednesdaySchedule() != null && ScheduleDays.size() < 26) {
                String[] workHours = doctor.getWednesdaySchedule().split("-");
                int workTime = Integer.parseInt(workHours[1]) - Integer.parseInt(workHours[0]);

                if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))) == null) {
                    ScheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                } else if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))).length() < workTime * 4) {
                    ScheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                }
            }
            if (dayOfWeek == 3) {
                day++;
                dayOfWeek++;
            }

            if (dayOfWeek == 4 && doctor.getThursdaySchedule() != null && ScheduleDays.size() < 26) {
                String[] workHours = doctor.getThursdaySchedule().split("-");
                int workTime = Integer.parseInt(workHours[1]) - Integer.parseInt(workHours[0]);

                if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))) == null) {
                    ScheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                } else if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))).length() < workTime * 4) {
                    ScheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                }
            }
            if (dayOfWeek == 4) {
                day++;
                dayOfWeek++;
            }

            if (dayOfWeek == 5 && doctor.getFridaySchedule() != null && ScheduleDays.size() < 26) {
                String[] workHours = doctor.getFridaySchedule().split("-");
                int workTime = Integer.parseInt(workHours[1]) - Integer.parseInt(workHours[0]);

                if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))) == null) {
                    ScheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                } else if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))).length() < workTime * 4) {
                    ScheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                }
            }
            if (dayOfWeek == 5) {
                day++;
                dayOfWeek++;
            }

            if (dayOfWeek == 6 && doctor.getSaturdaySchedule() != null && ScheduleDays.size() < 26) {
                String[] workHours = doctor.getSaturdaySchedule().split("-");
                int workTime = Integer.parseInt(workHours[1]) - Integer.parseInt(workHours[0]);

                if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))) == null) {
                    ScheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                } else if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))).length() < workTime * 4) {
                    ScheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                }
            }
            if (dayOfWeek == 6) {
                day++;
                dayOfWeek++;
            }

            if (dayOfWeek == 7 && doctor.getSundaySchedule() != null && ScheduleDays.size() < 26) {
                String[] workHours = doctor.getSundaySchedule().split("-");
                int workTime = Integer.parseInt(workHours[1]) - Integer.parseInt(workHours[0]);

                if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))) == null) {
                    ScheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
                } else if (googleCalendarSchedule.get(dateTimeFormatter.format(localDate.plusDays(day))).length() < workTime * 4) {
                    ScheduleDays.add(dateTimeFormatter.format(localDate.plusDays(day)));
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

        for (int i = 0; i < ScheduleDays.size(); i++) {
            dateFromList = LocalDate.parse(ScheduleDays.get(i));

            InlineKeyboardButton keyboardButton = new InlineKeyboardButton();
            keyboardButton.setText(dateFormatter.format(dateFromList));
            keyboardButton.setCallbackData(Strings.DATE_BUTTON + ScheduleDays.get(i) + " " + doctor.getChatId());

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


    private void doctorScheduleHoursButtons(String choseDate, long chatId, long messageId, String doctorId) {
        LocalDate localDate = LocalDate.parse(choseDate); // 2023-06-15
        DateTimeFormatter dayOfWeek = DateTimeFormatter.ofPattern("e"); // 4
        googleCalendar = new GoogleCalendarReceiver();
        Doctor doctor = receiveDoctorFromDb(Long.parseLong(doctorId));

        String doctorData = Objects.requireNonNull(doctor).getDoctorLastname() + " " + doctor.getDoctorFirstname() + " " + doctor.getDoctorPatronymic() + "-" + "doctor";

        TreeMap<String, String> googleCalendarSchedule = googleCalendar.receiveFromGoogleCalendar(doctorData); // "2023-06-16" "08:00 09:30 12:00 13:30"

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
            String temp = Arrays.toString(hours).replace(",", "").replace("[", "").replace("]", "").replaceAll("  ", " ").replaceAll("  ", " ").trim();

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

            } else if (!beginOfWorkTime.toString().equals(hours[iteration])) { // массив hours = "08:00 ; 10:30 ; 12:00 ; 13:30"
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


    private void messageForAllUsers(String text) {
        Iterable<User> users = userRepository.findAll();
        for (User user : users) {
            messageFromBot(user.getChatId(), text);
        }
    }


    private void findUser(long chatId, String lastName) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText("Выберите пациента из списка");

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
            messageFromBot(chatId, "Пользователей с фамилией " + lastName + " не найдено");
        } else {
            inlineKeyboardMarkup.setKeyboard(rowsInline);
            sendMessage.setReplyMarkup(inlineKeyboardMarkup);
            sendMessageExecute(sendMessage);
        }
    }


    private HashMap<String, String> doctorVacationDaysCheck(Doctor doctor) {
        String setText = "<------------------------the string with length over doctors work time---------------------------->";
        String[] doctorVacation;
        HashMap<String, String> vacationTime = new HashMap<>();
        LocalDate localDate = LocalDate.now();
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        if (doctor.getVacation() != null) {
            doctorVacation = doctor.getVacation().split("    ");
        } else return vacationTime;

        for (int i = 0; i < doctorVacation.length; i++) {
            String[] startAndVacation = doctorVacation[i].split(":");
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


    private void setDoctorVacation(long chatId, String messageText) {
        try {
            LocalDate firstDate = LocalDate.parse(tempData);
            LocalDate secondDate = LocalDate.parse(messageText);
            if (firstDate.isAfter(secondDate) || secondDate.isBefore(LocalDate.now())) {
                messageFromBot(chatId, "Дата окончания отпуска не может быть раньше текущей даты/даты начала отпуска");
                TEMP_CHOOSE_DOCTOR.clear();
                tempData = null;
                return;
            }
        } catch (DateTimeParseException e) {
            messageFromBot(chatId, "Неправильный ввод даты, отпуск врача не был установлен");
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
        messageFromBot(chatId, "Отпуск был добавлен.\n" + schedule);
    }


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

            LASTNAME.remove(chatId);
            FIRSTNAME.remove(chatId);
            PATRONYMIC.remove(chatId);
            PHONE_NUMBER.remove(chatId);
            BLOCK_DEFAULT_MESSAGE_VALUE.remove(chatId);
        }
    }


    private void addNoteInGoogleCalendar(long chatId, String dateTime, long doctorId, String fullName) { // максимум 33 печатных символа
        String userFullName = "null";
        String doctorFullName = "null";
                LocalDateTime localDateTime = LocalDateTime.now();
        LocalDateTime parseDate = LocalDateTime.parse(dateTime + ":00");
        long milliSeconds = ChronoUnit.MILLIS.between(localDateTime, parseDate); // разница в миллисекундах между датами

        Optional<User> user = userRepository.findById(chatId);
        if (isAdmin(chatId)) {
            userFullName = fullName;
        } else {
            if(user.isPresent()) userFullName = user.get().getLastname() + " " + user.get().getFirstname() + " " + user.get().getPatronymic();
        }

        Optional<Doctor> doctor = doctorRepository.findById(doctorId);
        if(doctor.isPresent()) doctorFullName = doctor.get().getDoctorLastname() + " " + doctor.get().getDoctorFirstname() + " " + doctor.get().getDoctorPatronymic();
        CalendarEventSetter calendarEventSetter = new CalendarEventSetter();
        String doctorAndPatientsNames = doctorFullName + "-" + userFullName;

        try {
            calendarEventSetter.setToCalendar(milliSeconds, doctorAndPatientsNames);
        } catch (IOException | GeneralSecurityException e) {
            log.error("calendarEventSetter error: " + e.getMessage());
            throw new RuntimeException(e);
        }

        String messageForAdmin = "Приём у доктора: " + doctorFullName + "-" + userFullName + " (пациент), запись на " + dateTime.replace("T", " в ") + " . " +
                "Подтвердите, пожалуйста, запись и проверьте её наличие в календаре приёма. Номер телефона пациента: " + user.get().getPhoneNumber();

        Iterable<User> users = userRepository.findAll();
        for (User admin : users) {
            if (config.adminValidation.equals(admin.getLastname() + admin.getFirstname() + admin.getPatronymic())) {
                messageFromBot(admin.getChatId(), messageForAdmin);
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


    private SendMessage receiveCreatedMessage(long chatId, String messageText) { // метод создаёт и возвращает экземпляр изменённого сообщения
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(messageText);
        return sendMessage;
    }


    private EditMessageText receiveEditMessageText(long chatId, long messageId, String messageText) { // метод создаёт и возвращает экземпляр изменённого сообщения с вновь добавленным текстом
        EditMessageText editMessageText = new EditMessageText();
        editMessageText.setChatId(chatId);
        editMessageText.setMessageId((int) messageId);
        editMessageText.setText(messageText);
        return editMessageText;
    }


    private void sendMessageExecute(SendMessage sendMessage) { // отправляет пользователю экземпляр сообщения
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("SendMessage execute error: " + e.getMessage());
        }
    }


    private void editMessageTextExecute(EditMessageText editMessageText) { // отправляет пользователю экземпляр изменённого сообщения
        try {
            execute(editMessageText);
        } catch (TelegramApiException e) {
            log.error("SendMessage execute error: " + e.getMessage());
        }
    }


    private boolean isAdmin(long chatId) {
        Optional<User> user = userRepository.findById(chatId); // все пользователи из бд
        if (user.isPresent()) {
            String fullName = user.get().getLastname() + user.get().getFirstname() + user.get().getPatronymic();
            return fullName.equals(config.adminValidation);
        }
        return false;
    }


    @Scheduled(fixedDelay = 60000)
    private void delayBeforeClear() { // один раз в фиксированный интервал времени метод удаляет данные из map, в которой один пользователь сохраняет время записи к врачу,
        AVOID_REGISTER_COLLISION.clear(); // чтобы другой пользователь не имел возможности записи, пока не delayBeforeClear() не очистит avoidTimeCollision
    }


    @Scheduled(cron = "${cron.schedule}") // настройки таймера вынесены в properties // TODO поменять на нужные
    private void sendMessageAboutVisit() {
        LocalDateTime localDateTime = LocalDateTime.now();
        DateTimeFormatter yearMonthDay = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String textForMessage = "";

        googleCalendar = new GoogleCalendarReceiver();
        TreeMap<String, String> googleCalendarSchedule;

        Iterable<User> users = userRepository.findAll();
        for (User user : users) {
            String fullName = user.getLastname() + " " + user.getFirstname() + " " + user.getPatronymic();
            googleCalendarSchedule = googleCalendar.receiveFromGoogleCalendar(fullName);

            if (googleCalendarSchedule.get(fullName) != null && googleCalendarSchedule.get(fullName).contains(yearMonthDay.format(localDateTime.plusDays(1)))) {
                String[] timeText = googleCalendarSchedule.get(fullName).split(";");

                for (int i = 0; i < timeText.length; i++) {
                    if (i != 0 && timeText[i].length() > 1) {
                        textForMessage += "Дальнейшая запись: " + timeText[i];
                    } else {
                        if (i == 0) textForMessage += timeText[i] + "\n";
                    }
                }
                messageFromBot(user.getChatId(), "Здравствуйте! Напоминаем, что завтра у вас будет приём у врача: " + textForMessage);
            }
            textForMessage = "";
        }
    }


    ///////////////////////////////////////////////////////////////________________TEST__________________///////////////////////////////////////////////////////////////////////////////////////





}





