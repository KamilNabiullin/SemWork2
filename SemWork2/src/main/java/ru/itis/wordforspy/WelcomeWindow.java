package ru.itis.wordforspy;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.kordamp.bootstrapfx.BootstrapFX;

public class WelcomeWindow extends Application {
    private VBox welcomeLayout;
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        welcomeLayout = new VBox(10);
        welcomeLayout.setAlignment(Pos.CENTER);

        welcomeLayout.getStyleClass().addAll("p-4", "bg-light");

        Label gameTitle = new Label("Слово для шпиона");
        gameTitle.getStyleClass().add("h1");

        TextField nameInputField = new TextField();
        nameInputField.setMaxWidth(200);
        nameInputField.setPromptText("Введите ваше имя");
        nameInputField.getStyleClass().addAll("form-control");

                Button joinButton = new Button("Присоединиться к игре");
        joinButton.getStyleClass().addAll("btn", "btn-primary");
        joinButton.setOnAction(e -> {
            String clientName = nameInputField.getText();
            if (clientName.trim().isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Ошибка");
                alert.setHeaderText(null);
                alert.setContentText("Имя не может быть пустым!");
                alert.showAndWait();
            } else {
                joinGame(clientName);
            }
        });



        welcomeLayout.getChildren().addAll(
                gameTitle,
                new Label("Введите ваше имя:"),
                nameInputField,
                joinButton
        );

        Scene welcomeScene = new Scene(welcomeLayout, 400, 350);
        welcomeScene.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
        primaryStage.setScene(welcomeScene);
        primaryStage.setTitle("Добро пожаловать");
        primaryStage.show();
    }

    private void joinGame(String clientName) {
        Client crocodileClient = new Client(clientName);
        Stage stage = new Stage();
        crocodileClient.start(stage);
    }
}
