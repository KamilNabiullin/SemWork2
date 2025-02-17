package ru.itis.wordforspy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;

public class Handler extends Thread {
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private String playerName;

    private static final Set<PrintWriter> writers = new HashSet<>();
    private static final Set<String> playerNames = new HashSet<>();
    private static final Set<String> readyPlayers = new HashSet<>();
    private static final int MIN_PLAYERS_COUNT = 3;
    private static boolean gameStarted = false;
    private static String[] locations = {
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
    };

    private static boolean votingInProgress = false;
    private static String currentAccused = null;
    private static String currentAccuser = null;

    private static Map<String, String> currentVotes = new HashMap<>();
    private static Timer voteTimer = new Timer();

    private static boolean finalVotingInProgress = false;

    private static Map<String, String> finalVotes = new HashMap<>();
    private static Timer finalVoteTimer = new Timer();

    private static String currentSpyName = null;

    public Handler(Socket socket) {
        this.socket = socket;
    }

    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
            synchronized (writers) {
                writers.add(writer);
            }
            playerName = reader.readLine();
            System.out.println(playerName);
            synchronized (playerNames) {
                playerNames.add(playerName);
            }
            broadcast(playerName + " присоединился к игре", writer);
            broadcastPlayersList();

            String message;
            while ((message = reader.readLine()) != null) {
                System.out.println("Received from " + playerName + ": " + message);

                // Обработка READY-сообщения
                if (message.equals("READY")) {
                    synchronized (readyPlayers) {
                        if (!readyPlayers.contains(playerName)) {
                            readyPlayers.add(playerName);
                            broadcast(playerName + " готов", writer);
                        }
                    }
                    synchronized (playerNames) {
                        if (!gameStarted
                                && readyPlayers.size() == playerNames.size()
                                && playerNames.size() >= MIN_PLAYERS_COUNT) {
                            gameStarted = true;
                            ArrayList<String> playersList = new ArrayList<>(playerNames);
                            Random random = new Random();
                            String spyName = playersList.get(random.nextInt(playersList.size()));
                            String chosenLocation = locations[random.nextInt(locations.length)];
                            currentSpyName = spyName; // сохраняем выбранного шпиона
                            broadcast("GAME_START|" + spyName + "|" + chosenLocation, null);
                        }
                    }
                    continue;
                }
                if (message.startsWith("SPY_WON") || message.startsWith("SPY_LOST")) {
                    broadcast(message, null);
                    gameStarted = false;
                    readyPlayers.clear();
                }

                if (message.startsWith("VOTE_START|")) {
                    // Формат: VOTE_START|<accused>|<accuser>
                    String[] tokens = message.split("\\|");
                    if (tokens.length >= 3) {
                        synchronized (Handler.class) {
                            if (!votingInProgress) {
                                votingInProgress = true;
                                currentAccused = tokens[1];
                                currentAccuser = tokens[2];
                                currentVotes.clear();
                                broadcast(message, null);
                                voteTimer.schedule(new TimerTask() {
                                    @Override
                                    public void run() {
                                        processVoteResults();
                                    }
                                }, 15000);
                            }
                        }
                    }
                    continue;
                }

                // Обработка голосов: VOTE|YES или VOTE|NO
                if (message.startsWith("VOTE|")) {
                    String[] tokens = message.split("\\|");
                    if (tokens.length >= 2 && votingInProgress) {
                        synchronized (Handler.class) {
                            currentVotes.put(playerName, tokens[1]);
                            // Ожидаемые голосующие – все игроки, кроме обвинённого
                            Set<String> expectedVoters = new HashSet<>(playerNames);
                            expectedVoters.remove(currentAccused);
                            if (currentVotes.keySet().containsAll(expectedVoters)) {
                                voteTimer.cancel();
                                processVoteResults();
                            }
                        }
                    }
                    continue;
                }

                if (message.equals("FINAL_VOTE_START")) {
                    synchronized (Handler.class) {
                        if (!finalVotingInProgress) {
                            finalVotingInProgress = true;
                            finalVotes.clear();
                            broadcast("FINAL_VOTE_START", null);
                            finalVoteTimer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    processFinalVoteResults();
                                }
                            }, 30000);
                        }
                    }
                    continue;
                }

                // Обработка финальных голосов: FINAL_VOTE|<accused>
                if (message.startsWith("FINAL_VOTE|")) {
                    String[] tokens = message.split("\\|");
                    if (tokens.length >= 2 && finalVotingInProgress) {
                        synchronized (Handler.class) {
                            finalVotes.put(playerName, tokens[1]);
                            // Для финального голосования рассматриваем голоса всех игроков, кроме шпиона
                            Set<String> expectedVoters = new HashSet<>(playerNames);
                            expectedVoters.remove(currentSpyName);
                            if (finalVotes.keySet().containsAll(expectedVoters)) {
                                finalVoteTimer.cancel();
                                processFinalVoteResults();
                            }
                        }
                    }
                    continue;
                }

                broadcast(message, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                synchronized (writers) {
                    writers.remove(writer);
                }
            }
            if (playerName != null) {
                synchronized (playerNames) {
                    playerNames.remove(playerName);
                }
                synchronized (readyPlayers) {
                    readyPlayers.remove(playerName);
                }
                broadcast(playerName + " покинул игру", writer);
                broadcastPlayersList();
            }
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void processVoteResults() {
        String result;
        synchronized (Handler.class) {
            Set<String> expectedVoters = new HashSet<>(playerNames);
            expectedVoters.remove(currentAccused);
            if (currentVotes.containsKey(currentAccuser) &&
                    currentVotes.get(currentAccuser).equalsIgnoreCase("NO")) {
                result = "SPLIT";
            } else if (!currentVotes.keySet().containsAll(expectedVoters)) {
                result = "SPLIT";
            } else {
                boolean allYes = currentVotes.values().stream().allMatch(v -> v.equalsIgnoreCase("YES"));
                boolean allNo = currentVotes.values().stream().allMatch(v -> v.equalsIgnoreCase("NO"));
                if (allYes) {
                    if (!currentAccused.equalsIgnoreCase(currentSpyName)) {
                        result = "NO_UNANIMOUS";
                    } else {
                        result = "YES_UNANIMOUS";
                    }
                } else if (allNo) {
                    result = "NO_UNANIMOUS";
                } else {
                    result = "SPLIT";
                }
            }
            votingInProgress = false;
            String accusedForMsg = currentAccused;
            String accuserForMsg = currentAccuser;
            currentAccused = null;
            currentAccuser = null;
            currentVotes.clear();
            voteTimer = new Timer();
            broadcast("VOTE_RESULT|" + result + "|" + accusedForMsg + "|" + accuserForMsg, null);
            gameStarted = false;
            readyPlayers.clear();
        }
    }

    private void processFinalVoteResults() {
        String result;
        synchronized (Handler.class) {
            int nonSpyCount = playerNames.size() - 1;
            Map<String, Integer> candidateVotes = new HashMap<>();
            for (Map.Entry<String, String> entry : finalVotes.entrySet()) {
                String voter = entry.getKey();
                if (!voter.equalsIgnoreCase(currentSpyName)) {
                    String candidate = entry.getValue();
                    candidateVotes.put(candidate, candidateVotes.getOrDefault(candidate, 0) + 1);
                }
            }
            String unanimousCandidate = null;
            for (Map.Entry<String, Integer> entry : candidateVotes.entrySet()) {
                if (entry.getValue() == nonSpyCount) {
                    unanimousCandidate = entry.getKey();
                    break;
                }
            }
            if (unanimousCandidate != null) {
                if (unanimousCandidate.equalsIgnoreCase(currentSpyName)) {
                    result = "YES_UNANIMOUS";
                } else {
                    result = "NO_UNANIMOUS";
                }
            } else {
                result = "SPLIT";
            }
            finalVotingInProgress = false;
            finalVotes.clear();
            finalVoteTimer = new Timer();
            broadcast("FINAL_VOTE_RESULT|" + result, null);
            gameStarted = false;
            readyPlayers.clear();
        }
    }

    private void broadcast(String message, PrintWriter sender) {
        synchronized (writers) {
            for (PrintWriter w : writers) {
                if (w != sender) {
                    w.println(message);
                }
            }
        }
    }

    private void broadcastPlayersList() {
        String playersListStr;
        synchronized (playerNames) {
            playersListStr = playerNames.stream().collect(Collectors.joining(","));
        }
        broadcast("PLAYERS|" + playersListStr, null);
    }
}
