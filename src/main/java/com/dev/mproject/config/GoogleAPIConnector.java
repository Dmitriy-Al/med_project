package com.dev.mproject.config;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.CalendarScopes;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.io.IOException;
import java.util.List;
/*
@Data
@Configuration
@PropertySource("application.properties")

@Value("${client.id}")
public String clientId;

    @Value("${client.secret}")
    public String clientSecret;
*/
public class GoogleAPIConnector {

    private static final String CLIENT_ID = "";
    private static final String CLIENT_SECRET = "";

    private static final String APPLICATION_NAME = "Google Calendar API";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final List<String> SCOPES = List.of(CalendarScopes.CALENDAR_READONLY); // private static final List<String> SCOPES = Arrays.asList(CalendarScopes.CALENDAR_READONLY);

    public Credential getCredentials(final HttpTransport HTTP_TRANSPORT) throws IOException {
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, CLIENT_ID, CLIENT_SECRET, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File("tokens")))
                .setAccessType("offline")
                .build();
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }

    public JsonFactory getJsonFactory(){
        return JSON_FACTORY;
    }

    public String getApplicationName(){
        return APPLICATION_NAME;
    }
}

/*


    private static final String APPLICATION_NAME = "Google Calendar API";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Arrays.asList(CalendarScopes.CALENDAR_READONLY); // TODO: поправить на list.of в готовом приложении
    //private static final String CREDENTIALS_FILE_PATH = "C:\\Users\\admit\\OneDrive\\Рабочий стол\\key"; //  private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    public Credential getCredentials(final HttpTransport HTTP_TRANSPORT) throws IOException {
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, "521841621879-tko20rggg6k7emrvbkmgbq4nng4lkjv7.apps.googleusercontent.com", "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", SCOPES) // HTTP_TRANSPORT, JSON_FACTORY, "YOUR_CLIENT_ID", "YOUR_CLIENT_SECRET", SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File("tokens")))
                .setAccessType("offline")
                .build();
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }

    public JsonFactory getJsonFactory(){
        return JSON_FACTORY;
    }

    public String getApplicationName(){
        return APPLICATION_NAME;
    }

 */