package com.dev.mproject.service;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

public class KeyboardWrapper {

    private String buttonSetText;
    private String buttonSetCallbackText;

    public InlineKeyboardButton getKeyboardButton() {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(buttonSetText);
        button.setCallbackData(buttonSetCallbackText);
        return button;
    }

    public void setButtonSetText(String buttonSetText) {
        this.buttonSetText = buttonSetText;
    }

    public void setButtonSetCallbackText(String buttonSetCallbackText) {
        this.buttonSetCallbackText = buttonSetCallbackText;
    }

}
