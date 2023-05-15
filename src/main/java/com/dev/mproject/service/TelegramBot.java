package com.dev.mproject.service;

import com.dev.mproject.config.Config;
import com.dev.mproject.model.GoogleCalendarReceiver;
import com.dev.mproject.model.User;
import com.dev.mproject.model.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    @Autowired
    public UserRepository userRepository;
    private GoogleCalendarReceiver googleCalendar;
    private final Config config;

    public TelegramBot(Config config) {
        super(config.botToken);
        this.config = config;
    }


    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (messageText.contains("/setdoctor")) { // рабочий метод
                String findByName = messageText.substring(messageText.indexOf(" ")).stripLeading();
                System.out.println("messageText.substring =" + findByName);
                var users = userRepository.findAll();
                System.out.println("var users = userRepository.findAll();");
                for (User user : users) {
                    if (findByName.equalsIgnoreCase(user.getFirstname())) {
                        user.setIsDoctor(true);
                        userRepository.save(user);
                        System.out.println("Успех");
                    }
                }
            }else if (messageText.contains("/setuser")) {
                String userData = messageText.substring(messageText.indexOf(" ")).stripLeading(); // рабочий метод
                addUserInDB(update.getMessage(), userData);
                messageFromBot(chatId, "Спасибо, теперь вы зарегистрированы и можете получить доступ к расписанию приёма используя команду /priem");

            } else {
                switch (messageText) {
                    case "/start" -> {
                        String text = "Здравствуйте, " + update.getMessage().getChat().getFirstName() + "!";
                        messageFromBot(chatId, text);
                        registerUser(chatId);
                    }
                    case "/priem" -> { // рабочий метод
                        Optional<User> user = userRepository.findById(update.getMessage().getChatId()); //TODO: сделать
                        String userFullName = user.get().getLastname() + " " + user.get().getFirstname() + " " + user.get().getPatronymic();
                        googleCalendar = new GoogleCalendarReceiver();
                        googleCalendar.receiveSchedule(userFullName);
                        String text = "Здравствуйте, " + googleCalendar.getPatientSchedule();
                        messageFromBot(chatId, text);
                    }
                    case "/register" -> {


                    }
                    case "/drpriem" -> {
                        Iterable<User> users = userRepository.findAll();
                        for (User user : users) {
                            if (chatId == user.getChatId() && user.getIsDoctor()) {
                                StringBuilder stringBuilder = new StringBuilder();
                                for (User patients : users) {
                                    stringBuilder.append(patients.toString());
                                }
                                googleCalendar = new GoogleCalendarReceiver();
                                googleCalendar.receiveSchedule("Дмитрий Дмитриевич Дмитриев");
                                String text = "Здравствуйте, " + googleCalendar.getDoctorSchedule();
                                messageFromBot(chatId, text);
                            } else {
                                String text = "Такая команда отсутствует";
                                messageFromBot(chatId, text);
                            }
                        }
                    }
                    default -> {
                        String text = "Такая команда отсутствует";
                        messageFromBot(chatId, text);
                    }
                }
            }

        } else if (update.hasCallbackQuery()) {
            long messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            String callbackData = update.getCallbackQuery().getData();
            if (callbackData.equals("YES_BUTTON")) {
                EditMessageText editMessageText = new EditMessageText();
                editMessageText.setChatId(chatId);
                editMessageText.setMessageId((int) messageId);
                editMessageText.setText("Введите команду /setuser, вашу фамилию, имя, отчество и номер телефона, проверьте правильность ввода и отправьте сообщение. \n пример: /setuser Фёдоров Фёдор Фёдорович 89001112233");
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
                try {
                    execute(editMessageText);
                } catch (TelegramApiException e) {
                    log.error("SendMessage execute error: " + e.getMessage());
                }
            } else {
                EditMessageText editMessageText = new EditMessageText();
                editMessageText.setChatId(chatId);
                editMessageText.setMessageId((int) messageId);
                editMessageText.setText("Без регистрации функционал сильно ограничен, в дальнейшем вы можете зарегистрироваться с помощью команды /setuser");
                try {
                    execute(editMessageText);
                } catch (TelegramApiException e) {
                    log.error("SendMessage execute error: " + e.getMessage());
                }
            }

        }

    }

    @Override
    public String getBotUsername() {
        return config.BotName;
    }


    public void onUpdateRegistration(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String userData = "/register " + update.getMessage().getText();

            long chatId = update.getMessage().getChatId();

            System.out.println("step 2");

            addUserInDB(update.getMessage(), userData);
            messageFromBot(chatId, "Спасибо, теперь вы зарегистрированы и можете получить доступ к расписанию приёма используя команду /priem");
        }
    }


    public void messageFromBot(long chatId, String text) { // метод отправки сообщения пользователю, принимаемые параметры chatId и отправляемое сообщение
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("SendMessage execute error: " + e.getMessage());
        }
    }


    public void registerUser(long chatId) { // регистрация пользователя и вызов метода addUserInDB() для добавления пользователя в дб
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText("Зарегистрированные пользователи имеют больше возможностей. Желаете зарегистрироваться?");
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInlineButton = new ArrayList<>();

        InlineKeyboardButton yesButton = new InlineKeyboardButton();
        yesButton.setText("Да");
        yesButton.setCallbackData("YES_BUTTON");

        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("Отмена");
        cancelButton.setCallbackData("CANCEL_BUTTON");

        rowInlineButton.add(yesButton);
        rowInlineButton.add(cancelButton);

        rowsInline.add(rowInlineButton);

        inlineKeyboardMarkup.setKeyboard(rowsInline);
        sendMessage.setReplyMarkup(inlineKeyboardMarkup);

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("SendMessage execute error: " + e.getMessage());
        }

        }



    public void addUserInDB(Message message, String userData) { // добавления пользователя в дб

        String[] splitUserDate = userData.split(" ");

        String lastname = splitUserDate[0].trim();
        String firstname = splitUserDate[1].trim();
        String patronymic = splitUserDate[2].trim();
        long phoneNumber = Long.parseLong(splitUserDate[3]);

        if (userRepository.findById(message.getChatId()).isEmpty()) {
            User user = new User();
            Chat chat = message.getChat();

            user.setChatId(message.getChatId());
            user.setUserName(chat.getFirstName());
            user.setFirstname(firstname);
            user.setPatronymic(patronymic);
            user.setLastname(lastname);
            user.setPhoneNumber(phoneNumber);
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));
            user.setIsDoctor(false);
            userRepository.save(user);
        }
    }





}

/*
    public void createTables() {
        String createText = "Жопин Аркадий Кешевич 10.30 осмотр Головач Лена Анусовна 89213332211";

        List<String> wordsList = Arrays.asList(createText.split(" "));

        String calendarScheduleText = createText;
        String doctorLastname = wordsList.get(0);
        String doctorFirstname = wordsList.get(1);
        String doctorPatronymic = wordsList.get(2);
        String patientLastname = wordsList.get(5);
        String patientFirstname = wordsList.get(6);
        String patientPatronymic = wordsList.get(7);
        long patientPhoneNumber = Long.parseLong(wordsList.get(8));

        GoogleCalendar googleCalendar = new GoogleCalendar();
        googleCalendar.addCalendarTable(patientPhoneNumber, calendarScheduleText);
        googleCalendarRepository.save(googleCalendar);

        if (userRepository.findById(patientPhoneNumber).isEmpty()) {
            User user = new User();
            user.addUserPatient(patientLastname, patientFirstname, patientPatronymic, patientPhoneNumber);
            userRepository.save(user);
        }
    }
    **********************************************************************************************************

        public void yesOrCancelChatClick(SendMessage sendMessage){
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInlineButton = new ArrayList<>();

        InlineKeyboardButton yesButton = new InlineKeyboardButton();
        yesButton.setText("Да");
        yesButton.setCallbackData("YES_BUTTON");

        InlineKeyboardButton noButton = new InlineKeyboardButton();
        noButton.setText("Отмена");
        noButton.setCallbackData("CANCEL_BUTTON");

        rowInlineButton.add(yesButton);
        rowInlineButton.add(noButton);

        rowsInline.add(rowInlineButton);

        inlineKeyboardMarkup.setKeyboard(rowsInline);
        sendMessage.setReplyMarkup(inlineKeyboardMarkup);

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("SendMessage execute error: " + e.getMessage());
        }
    }
*****************************************************************************************************************

                    long messageId1 = update.getCallbackQuery().getMessage().getMessageId();
                    long chatId1 = update.getCallbackQuery().getMessage().getChatId();

                        EditMessageText editMessageText1 = new EditMessageText();
                        editMessageText.setChatId(chatId);
                        editMessageText.setMessageId((int) messageId);
 */