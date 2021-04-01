package redditCommunication;

import poker2.c.RMI.interfaces.rmiCommunication;
import redditjackal.entities.*;
import redditjackal.entities.inbox.*;
import redditjackal.exceptions.RedditorNotFoundException;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.List;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.Map;

/*

Spielablauf:

TischClient wird gestartet, dabei wird im subreddit PokerSturm ein neuer Post erstellt.
Spieler werden per PM an den Account vom TischClient hinzugefuegt (Subject muss bei allen PMs "PokerSturm"
lauten, inhalt der Message dann "addPlayer name"
Sobald mind. zwei Spieler angemeldet sind, sucht der TischClient unter seinem Post nach einem Kommentar "startGame"
eines beliebigen Spielers.
Befehle wie bet call raise etc. erwartet der TischClient als Kommentar unter seinem Post, dabei wird immer nur
der aktuelle Spieler betrachtet.
Seine Karten kann ein Spieler per PM erfragen (Subject "PokerSturm" Inhalt "getPlayerInformation",
er erhaelt dann auch eine Antwort per PM, Abmeldung, update etc laufen auch ueber PM
PMs werden unabhängig vom aktuellen Spieler verarbeitet

 */

public class redditCommunication {

    // credentials
    final String username, password, appId, appSecret;
    boolean running;

    // time and date
    final DateTimeFormatter dtf;
    final LocalDateTime now;
    final long unixTime;

    // reddit objects
    Reddit reddit;
    Subreddit PokerSturm;
    BotOwner botOwner;

    // rmi connection to tisch
    rmiCommunication rmi;

    // playerNames:
    // key: reddit-account-name //value: rmi-name (displayed in UI)
    HashMap<String, String> playerNames = new HashMap<>();
    // commentTime:
    // key: reddit-account-name //value: time of last message (initialized with current time on startuo)
    HashMap<String, Long> commentTime = new HashMap<>();

    public redditCommunication(String username, String password, String appId, String appSecret) throws Exception {
        this.username = username;
        this.password = password;
        this.appId    = appId;
        this.appSecret= appSecret;

        // format Date and Time for use as title of Post
        dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd%20HH:mm:ss");
        now = LocalDateTime.now();

        // login in reddit, set our subreddit, set owner to receive messages
        reddit = new Reddit(username, password, appId, appSecret);
        PokerSturm = reddit.getSubreddit("PokerSturm");
        botOwner = reddit.getMe();

        unixTime = System.currentTimeMillis() / 1000L;
    }

    public void runServer() throws Exception {

        running = true;

        try {
            rmi = new rmiCommunication("RMIServer");
        } catch (Exception e) {
            System.out.println("Cant connect to RMI-Server... Maybe its not running?");
            return;
        }

        // game startup: Post a new game "Lobby" on our subreddit
        PokerSturm.post(dtf.format(now) + "%20New%20Game", "self", "started%20by%20server...");

        // main loop for game logic, run as long as no shutdown-command is received
        while(running) {
            // wait for at least two players, check for registration via private messages
            while(playerNames.size() < 2) {
                System.out.println("Checking for PMs...");
                handlePMs();
                Thread.sleep(3000);
            }

            // enough players registered, check each player has started a game
            // else check PMs for additional instructions
            while(waitForGame()) {
                handlePMs();
                Thread.sleep(3000);
            }

            // in-game: check if active player issued a command
            while(!rmi.getState().equals("End-State")) {
                handlePMs();
                handleActivePlayer();
                Thread.sleep(3000);
            }
        }
    }

    // pull unread messages and check for commands
    void handlePMs() throws Exception {
        List<InboxMessage> unread = botOwner.getUnreadInboxMessages();   //max 100

        for (InboxMessage message: unread)  {
            botOwner.readPrivateMessage(message.getName());

            String msg = message.getBody();
            String[] content = msg.split(" ");

            if(message.getSubject().equals("PokerSturm")) {
                System.out.println("Received Command: " + content[0]);
                switch (content[0]) {
                    case "addPlayer":
                        if(content.length >= 2) {
                            addPlayer(message.getAuthor(), content[1]);
                            botOwner.sendPrivateMessage("PokerSturm-Server", "add received", message.getAuthor());
                        }
                        break;
                    case "removePlayer":
                        if(content.length >= 2) {
                            removePlayer(message.getAuthor(), content[1]);
                            botOwner.sendPrivateMessage("PokerSturm-Server", "remove received", message.getAuthor());
                        }
                        break;
                    case "getPlayerInformation":
                        botOwner.sendPrivateMessage("PokerSturm-Server", "your player information: " + rmi.getPlayerInformation(playerNames.get(message.getAuthor())), message.getAuthor());
                        break;
                    case "getAvailableActions":
                        botOwner.sendPrivateMessage("PokerSturm-Server", "available Actions: " + rmi.getAvailableActions(playerNames.get(message.getAuthor())), message.getAuthor());
                        break;
                    case "update":
                        rmi.update();
                        botOwner.sendPrivateMessage("PokerSturm-Server", "screen refreshed", message.getAuthor());
                        break;
                    case "serverShutdown":
                        botOwner.sendPrivateMessage("PokerSturm-Server", "server shutting down...", message.getAuthor());
                        running = false;
                        break;
                    default:
                        botOwner.sendPrivateMessage("PokerSturm-Server - rejected", "wrong message content", message.getAuthor());
                        break;
                }
            }
        }
    }

    // check each registered player if someone issued a startGame comment
    boolean waitForGame() throws RedditorNotFoundException, RemoteException, InterruptedException {
        for(Map.Entry player: playerNames.entrySet()) {
            Redditor user = reddit.getRedditor(player.getKey().toString());
            List<Comment> newComments = user.commentHistory().updateNew().getComments();
            if(newComments.get(0).getBody().equals("startGame") && (newComments.get(0).getCreatedUtc() > commentTime.get(player.getKey().toString()))) {
                commentTime.replace(player.getKey().toString(),newComments.get(0).getCreatedUtc());
                rmi.startGame();
                return false;
            }
            Thread.sleep(500);
        }
        return true;
    }

    // check if active player issued a new command via comment, handle command and save time of latest message
    void handleActivePlayer() throws RemoteException, RedditorNotFoundException {
        Redditor user = reddit.getRedditor(getCurrentPlayer());
        List<Comment> newComments = user.commentHistory().updateNew().getComments();

        if(newComments.get(0).getCreatedUtc() > commentTime.get(getCurrentPlayer())) {
            commentTime.replace(getCurrentPlayer(),newComments.get(0).getCreatedUtc());
            String action = newComments.get(0).getBody();
            switch (action) {
                case "bet":
                    rmi.call(playerNames.get(getCurrentPlayer()), "bet");
                    break;
                case "call":
                    rmi.call(playerNames.get(getCurrentPlayer()), "call");
                    break;
                case "raise":
                    rmi.call(playerNames.get(getCurrentPlayer()), "raise");
                    break;
                case "check":
                    rmi.call(playerNames.get(getCurrentPlayer()), "check");
                    break;
                case "allin":
                    rmi.call(playerNames.get(getCurrentPlayer()), "allin");
                    break;
                case "fold":
                    rmi.call(playerNames.get(getCurrentPlayer()), "fold");
                    break;
            }
        }
    }

    // register player on tisch via rmi, save name of reddit-account
    // and displayed name, set latest comment time to startup time of server
    void addPlayer(String name, String displayName) throws RemoteException {
        playerNames.put(name, displayName);
        commentTime.put(name, unixTime);
        rmi.addPlayer(displayName);
        System.out.println("player " + name + " added");
    }

    // remove player from tisch via rmi and from hashmaps
    void removePlayer(String name, String displayName) throws RemoteException {
        playerNames.remove(name, displayName);
        commentTime.remove(name);
        rmi.removePlayer(displayName);
        System.out.println("player " + name + " removed");
    }

    // check via rmi which players is active, return the corresponding reddit-account
    String getCurrentPlayer() throws RemoteException {
        String rmiName = rmi.getCurrentPlayer();
        for(Map.Entry player: playerNames.entrySet()) {
            if(player.getValue().equals(rmiName))
                return player.getKey().toString();
        }
        return null;
    }
}