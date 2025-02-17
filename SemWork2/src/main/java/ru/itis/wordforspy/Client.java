package ru.itis.wordforspy;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;

public class Client extends Application {
    private String name;
    private boolean isSpy = false;
    private Integer points = 0;
    private static final String SERVER_IP = "127.0.0.1"; // IP адрес сервера
    private static final int SERVER_PORT = 5000; // Порт сервера

    private PrintWriter writer;
    private BufferedReader reader;

    private List<String> locations = Arrays.asList(
            "театр",
            "пиратский корабль",
            "университет",
            "спа-салон",
            "подводная лодка",
            "лайнер",
            "пляж",
            "полицейский участок",
            "орбитальная станция",
            "сервис",
            "вагон",
            "больница",
            "киностудия",
            "школа",
            "супермаркет",
            "ресторан",
            "цирк",
            "отель"
    );

    private Label timerLabel;
    private Timeline timeline;
    private int timeSeconds = 480; // 8 минут

    private TextArea chatArea;
    private TextField chatInput;

    private ListView<String> playersListView;
    private Button accuseButton;

    private Button readyButton;

    private GridPane locationsGrid;

    private HBox roleDisplayPane;
    private ImageView roleImageView;
    private Label roleLabel;

    private VBox votingPane;
    private Label votingLabel;
    private Button voteYesButton;
    private Button voteNoButton;
    private Label votingTimerLabel;
    private Timeline votingTimeline;
    private int votingTimeSeconds = 15;

    private String actualLocation;
    private String currentSpyName;

    private Map<String, Integer> scoreboard = new HashMap<>();

    public Client(String name) {
        this.name = name + new Random().nextInt(1000);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Слово для шпиона");

        timerLabel = new Label("Время: " + formatTime(timeSeconds));
        readyButton = new Button("Готов");
        readyButton.setOnAction(e -> onReady());
        HBox topBox = new HBox(20, timerLabel, readyButton);
        topBox.setPadding(new Insets(10));
        topBox.setAlignment(Pos.CENTER);

        playersListView = new ListView<>();
        playersListView.getItems().addAll("Игрок 1", "Игрок 2", "Игрок 3");
        accuseButton = new Button("Обвинить");
        accuseButton.setOnAction(e -> onAccuse());
        VBox leftBox = new VBox(10, new Label("Игроки"), playersListView, accuseButton);
        leftBox.setPadding(new Insets(10));

        VBox rightBox = new VBox(10);
        rightBox.setPadding(new Insets(10));
        rightBox.getChildren().add(new Label("Список локаций"));
        locationsGrid = new GridPane();
        locationsGrid.setHgap(5);
        locationsGrid.setVgap(5);
        int i = 1;
        for (String location : locations) {
            String path = "file:src/main/resources/" + location + ".jpg";
            ImageView iv = new ImageView(new Image(path, 160, 120, true, true));
            iv.setFitWidth(160);
            iv.setFitHeight(120);
            iv.setPreserveRatio(true);
            iv.setOnMouseClicked(event -> onLocationSelected(location));
            int col = (i - 1) % 6;
            int row = (i - 1) / 6;
            locationsGrid.add(iv, col, row);
            i++;
        }
        rightBox.getChildren().add(locationsGrid);

        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatInput = new TextField();
        chatInput.setPromptText("Введите сообщение...");
        Button sendButton = new Button("Отправить");
        sendButton.setOnAction(e -> sendChatMessage());
        HBox chatInputBox = new HBox(5, chatInput, sendButton);
        chatInputBox.setPadding(new Insets(5));

        roleImageView = new ImageView();
        roleImageView.setFitWidth(160);
        roleImageView.setFitHeight(120);
        roleImageView.setPreserveRatio(true);
        roleLabel = new Label();
        roleDisplayPane = new HBox(10, roleImageView, roleLabel);
        roleDisplayPane.setAlignment(Pos.CENTER);
        roleDisplayPane.setPadding(new Insets(10));
        roleDisplayPane.setVisible(false);

        votingLabel = new Label();
        voteYesButton = new Button("За");
        voteYesButton.setOnAction(e -> onVote(true));
        voteNoButton = new Button("Против");
        voteNoButton.setOnAction(e -> onVote(false));
        votingTimerLabel = new Label();
        votingPane = new VBox(10, votingLabel, new HBox(10, voteYesButton, voteNoButton), votingTimerLabel);
        votingPane.setAlignment(Pos.CENTER);
        votingPane.setPadding(new Insets(10));
        votingPane.setVisible(false);

        VBox chatBox = new VBox(5, chatArea, chatInputBox, roleDisplayPane, votingPane);
        chatBox.setPrefSize(400, 300);
        chatBox.setBackground(new Background(new BackgroundFill(
                javafx.scene.paint.Color.color(1, 1, 1, 0.8), CornerRadii.EMPTY, Insets.EMPTY)));
        StackPane centerStack = new StackPane();
        centerStack.getChildren().add(chatBox);

        BorderPane root = new BorderPane();
        root.setTop(topBox);
        root.setLeft(leftBox);
        root.setCenter(centerStack);
        root.setRight(rightBox);

        Scene scene = new Scene(root, 1700, 600);
        primaryStage.setScene(scene);
        primaryStage.show();

        connectToServer();
    }

    private void connectToServer() {
        try {
            Socket socket = new Socket(SERVER_IP, SERVER_PORT);
            writer = new PrintWriter(socket.getOutputStream(), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer.println(name);
            Thread readerThread = new Thread(() -> {
                try {
                    while (true) {
                        String message = reader.readLine();
                        if (message != null) {
                            Platform.runLater(() -> processMessage(message));
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            readerThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processMessage(String message) {
        if (message.startsWith("PLAYERS|")) {
            String playersStr = message.substring("PLAYERS|".length());
            String[] players = playersStr.split(",");
            for (String player : players) {
                if (!scoreboard.containsKey(player)) {
                    scoreboard.put(player, 0);
                }
            }
            updatePlayersList();
        } else if (message.startsWith("GAME_START|")) {
            // Формат: GAME_START|<spyName>|<location>
            String[] tokens = message.split("\\|");
            if (tokens.length >= 3) {
                currentSpyName = tokens[1];
                actualLocation = tokens[2];
                chatArea.appendText("Игра начинается!\n");
                startTimer();
                if (name.equals(currentSpyName)) {
                    roleImageView.setImage(new Image("file:src/main/resources/spy.jpg"));
                    roleLabel.setText("Ваша роль - шпион");
                    isSpy = true;
                } else {
                    roleImageView.setImage(new Image("file:src/main/resources/" + actualLocation + ".jpg"));
                    roleLabel.setText("Локация: " + actualLocation);
                    isSpy = false;
                }
                roleDisplayPane.setVisible(true);
            }
        } else if (message.startsWith("VOTE_START|")) {
            // Формат: VOTE_START|<accused>|<accuser>
            String[] tokens = message.split("\\|");
            if (tokens.length >= 3) {
                String accused = tokens[1];
                String accuser = tokens[2];
                if (!name.equals(accused)) {
                    startVoting(accused, accuser);
                }
            }
        } else if (message.startsWith("VOTE_RESULT|")) {
            // Формат: VOTE_RESULT|<result>|<accused>|<accuser>
            String[] tokens = message.split("\\|");
            if (tokens.length >= 4) {
                String result = tokens[1]; // YES_UNANIMOUS, NO_UNANIMOUS, или SPLIT
                String accused = tokens[2];
                String accuser = tokens[3];
                if (result.equals("YES_UNANIMOUS")) {
                    for (String player : scoreboard.keySet()) {
                        if (player.equals(accuser)) {
                            scoreboard.put(player, scoreboard.get(player) + 2);
                        } else if (!player.equals(accused)) {
                            scoreboard.put(player, scoreboard.get(player) + 1);
                        }
                    }
                    chatArea.appendText(accused + " оказался шпионом\n");
                    updatePlayersList();
                    restartGame();
                } else if (result.equals("NO_UNANIMOUS")) {
                    scoreboard.put(currentSpyName, scoreboard.get(currentSpyName) + 2);
                    chatArea.appendText("Шпионом оказался " + currentSpyName + "\n");
                    updatePlayersList();
                    restartGame();
                } else if (result.equals("SPLIT")) {
                    chatArea.appendText("Голоса разделились или не все проголосовали. Игра продолжается.\n");
                    hideVotingPanel();
                }
            }
        } else if (message.startsWith("FINAL_VOTE_START|")) {
            startFinalVoting();
        } else if (message.startsWith("FINAL_VOTE_RESULT|")) {
            String[] tokens = message.split("\\|");
            if (tokens.length >= 2) {
                String result = tokens[1];
                if (result.equals("YES_UNANIMOUS")) {
                    for (String player : scoreboard.keySet()) {
                        if (!player.equals(currentSpyName)) {
                            scoreboard.put(player, scoreboard.get(player) + 1);
                        }
                    }
                    chatArea.appendText("Финальное голосование: единогласно обвинены. Нешпионы +1 очко.\n");
                    updatePlayersList();
                    restartGame();
                } else if (result.equals("SPLIT")) {
                    scoreboard.put(currentSpyName, scoreboard.get(currentSpyName) + 2);
                    chatArea.appendText("Финальное голосование: голоса разделились. Шпион +2 очка.\n");
                    updatePlayersList();
                    restartGame();
                }
            }
        } else if (message.equals("SPY_WON")) {
            for (String player : scoreboard.keySet()) {
                if (player.equals(currentSpyName)) {
                    scoreboard.put(player, scoreboard.get(player) + 4);
                }
            }
            chatArea.appendText("Шпион угадал локацию!\n");
            updatePlayersList();
            restartGame();
        } else if (message.equals("SPY_LOST")) {
            for (String player : scoreboard.keySet()) {
                if (!player.equals(currentSpyName)) {
                    scoreboard.put(player, scoreboard.get(player) + 1);
                }
            }
            chatArea.appendText("Шпион не угадал локацию! Правильная локация - " + actualLocation + "\n");
            updatePlayersList();
            restartGame();
        } else {
            chatArea.appendText(message + "\n");
        }
    }

    private void updatePlayersList() {
        playersListView.getItems().clear();
        for (String player : scoreboard.keySet()) {
            playersListView.getItems().add(player + " (" + scoreboard.get(player) + " очков)");
        }
    }

    private void startTimer() {
        timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            timeSeconds--;
            timerLabel.setText("Время: " + formatTime(timeSeconds));
            if (timeSeconds <= 0) {
                timeline.stop();
                onTimerFinished();
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void onReady() {
        chatArea.appendText("Вы нажали 'Готов'. Ожидаем остальных игроков...\n");
        readyButton.setDisable(true);
        if (writer != null) {
            writer.println("READY");
        }
    }

    private void onAccuse() {
        String selectedPlayer = playersListView.getSelectionModel().getSelectedItem();
        if (selectedPlayer != null) {
            String baseName = selectedPlayer.split(" ")[0];
            if (!baseName.equals(name)) {
                chatArea.appendText("Вы обвинили: " + selectedPlayer + "\n");
                // Отправляем сообщение голосования на сервер: VOTE_START|<accused>|<accuser>
                writer.println("VOTE_START|" + baseName + "|" + name);
            } else {
                chatArea.appendText("Нельзя обвинить себя.\n");
            }
        } else {
            chatArea.appendText("Сначала выберите игрока для обвинения.\n");
        }
    }

    private void sendChatMessage() {
        String msg = chatInput.getText();
        if (!msg.isEmpty()) {
            chatArea.appendText("Вы: " + msg + "\n");
            writer.println(name + ": " + msg);
            chatInput.clear();
        }
    }

    private void onLocationSelected(String chosenLocation) {
        if (isSpy) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Подтверждение выбора");
            alert.setHeaderText(null);
            alert.setContentText("Вы уверены, что хотите выбрать локацию: " + chosenLocation + "?");
            ButtonType yesButton = new ButtonType("Да", ButtonBar.ButtonData.YES);
            ButtonType noButton = new ButtonType("Нет", ButtonBar.ButtonData.NO);
            alert.getButtonTypes().setAll(yesButton, noButton);
            alert.showAndWait().ifPresent(response -> {
                if (response == yesButton) {
                    if (chosenLocation.equalsIgnoreCase(actualLocation)) {
                        chatArea.appendText("Вы угадали локацию! Вам начислено 4 очка.\n");
                        writer.println("SPY_WON");
                    } else {
                        chatArea.appendText("Неправильный выбор! Правильная локация - " + actualLocation + "\n");
                        writer.println("SPY_LOST");
                    }
                }
            });
        } else {
            chatArea.appendText("Вы не шпион, угадывать локацию нельзя.\n");
        }
    }

    private void onTimerFinished() {
        chatArea.appendText("Время истекло! Переход к финальному этапу...\n");
        writer.println("FINAL_VOTE_START");
    }

    private void startVoting(String accused, String accuser) {
        votingLabel.setText("Голосование: обвинить " + accused + "?");
        votingTimeSeconds = 15;
        votingTimerLabel.setText("Осталось: " + votingTimeSeconds + " сек");
        votingPane.setVisible(true);
        votingTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            votingTimeSeconds--;
            votingTimerLabel.setText("Осталось: " + votingTimeSeconds + " сек");
            if (votingTimeSeconds <= 0) {
                votingTimeline.stop();
                writer.println("VOTE_RESULT|SPLIT|" + accused + "|" + accuser);
                hideVotingPanel();
            }
        }));
        votingTimeline.setCycleCount(Timeline.INDEFINITE);
        votingTimeline.play();
    }

    private void startFinalVoting() {
        votingLabel.setText("Финальное голосование: обвините шпиона.");
        votingTimeSeconds = 30;
        votingTimerLabel.setText("Осталось: " + votingTimeSeconds + " сек");
        votingPane.setVisible(true);
        votingTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            votingTimeSeconds--;
            votingTimerLabel.setText("Осталось: " + votingTimeSeconds + " сек");
            if (votingTimeSeconds <= 0) {
                votingTimeline.stop();
                writer.println("FINAL_VOTE_RESULT|SPLIT");
                hideVotingPanel();
            }
        }));
        votingTimeline.setCycleCount(Timeline.INDEFINITE);
        votingTimeline.play();
    }

    private void onVote(boolean voteYes) {
        writer.println("VOTE|" + (voteYes ? "YES" : "NO"));
        hideVotingPanel();
    }

    private void hideVotingPanel() {
        votingPane.setVisible(false);
        if (votingTimeline != null) {
            votingTimeline.stop();
        }
    }

    private void restartGame() {
        timeSeconds = 480;
        timerLabel.setText("Время: " + formatTime(timeSeconds));
        if (timeline != null) {
            timeline.stop();
        }
        roleDisplayPane.setVisible(false);
        readyButton.setDisable(false);
        actualLocation = null;
        currentSpyName = null;
        isSpy = false;
        chatArea.appendText("Раунд завершен. Нажмите 'Готов' для новой игры.\n");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
