package com.dev.mproject.service;

import com.dev.mproject.config.Config;
import com.dev.mproject.model.GoogleCalendarRepository;
import com.dev.mproject.model.TablesCreator;
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

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {

    @Autowired
    public GoogleCalendarRepository googleCalendarRepository;

    @Autowired
    public UserRepository userRepository;


    private TablesCreator dbTableCreator;
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

            if (messageText.contains("/setdoctor")) {
                System.out.println("Ввод setdoctor");
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
            } else {
                switch (messageText) {
                    case "/start" -> {
                        String text = "Здравствуйте, " + update.getMessage().getChat().getFirstName() + "!";
                        messageFromBot(chatId, text);
                        registerUser(chatId);
                    }
                    case "/help" -> {
                        String text = "Help";
                        messageFromBot(chatId, text);
                        dbTableCreator = new TablesCreator();
                        dbTableCreator.add(googleCalendarRepository, 767676786, "Ебал я рот этого казино");
                        String text2 = "Таблица создана";
                        messageFromBot(chatId, text2);
                    }
                    case "/register" -> {
                        System.out.println("/register");
                        registerUser(chatId);
                    }
                    case "/users" -> {
                        var users = userRepository.findAll();
                        for (User user : users) {
                            if (chatId == user.getChatId() && user.getIsDoctor()) {
                                StringBuilder stringBuilder = new StringBuilder();
                                for (User u : users) {
                                    stringBuilder.append(u.toString());
                                }
                                String listOfUsers = stringBuilder.toString() + "\n";
                                messageFromBot(chatId, listOfUsers);
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
                editMessageText.setText("Введите вашу фамилию \n Отлично, теперь вы зарегестрированы!");
                addUserInDB(update.getCallbackQuery().getMessage());
                try {
                    execute(editMessageText);
                } catch (TelegramApiException e) {
                    log.error("SendMessage execute error: " + e.getMessage());
                }
            } else {
                EditMessageText editMessageText = new EditMessageText();
                editMessageText.setChatId(chatId);
                editMessageText.setMessageId((int) messageId);
                editMessageText.setText("Без регистрации функционал сильно ограничен, в дальнейшем вы можете зарегистрироваться с помощью команды /register");
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


    public void messageFromBot(long chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("SendMessage execute error: " + e.getMessage());
        }
    }


    public void registerUser(Long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText("Зарегистрированные пользователи имеют больше возможностей. Желаете зарегистрироваться?");
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInlineButton = new ArrayList<>();

        InlineKeyboardButton yesButton = new InlineKeyboardButton();
        yesButton.setText("Да");
        yesButton.setCallbackData("YES_BUTTON");

        InlineKeyboardButton noButton = new InlineKeyboardButton();
        noButton.setText("Нет");
        noButton.setCallbackData("NO_BUTTON");

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


    public void addUserInDB(Message message) {
        if (userRepository.findById(message.getChatId()).isEmpty()) {
            User user = new User();
            Chat chat = message.getChat();
            user.setChatId(message.getChatId());
            user.setUserName(chat.getUserName());
            user.setFirstname(chat.getFirstName());
            user.setLastname(chat.getLastName());
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
 */