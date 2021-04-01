package redditPokerClient;

import redditjackal.entities.*;
import redditjackal.entities.inbox.InboxMessage;
import redditjackal.exceptions.RedditorNotFoundException;

import java.util.List;
import java.util.Scanner;
/*
RedditPokerClient

Folgende Daten werden benoetigt:
- API-Zugriff fuer den Account (appId, appSecret)
- Reddit-Account (username, password) diese werden über die Kommandozeile angefragt.

appId und appSecret koennen unter
https://www.reddit.com/prefs/apps
erstellt werden.

appId steht unter personal use script appSecret unter secret
unter developers koennen mehrere Accounts hinterlegt werden (TischClient, mehrere Spielerclients etc)
 */

public class redditPokerClient {
    static String username = "";
    static String postTitle = "New Game";

    public static void main (String [] args) throws Exception {
        // credentials
        String user = null;
        String pw = null;
        String appId = "appId";
        String appSecret = "appSecret";

        Scanner scanner = new Scanner(System.in);
        System.out.println("Reddit Pokerspiel\n" + "Reddit Username:");

        // Waiting for login credentials input
        boolean waitForLogin = true;

        while(waitForLogin){
            user = scanner.next();
            System.out.println("Reddit Password:");
            pw = scanner.next();
            if (user != null && pw != null && !user.isEmpty() && !pw.isEmpty()){
                waitForLogin = false;
            }
        }

        // login into reddit, set subreddit, set owner to receive messages
        Reddit reddit = new Reddit(user, pw, appId, appSecret);
        Subreddit subreddit = reddit.getSubreddit("PokerSturm");
        BotOwner botOwner = reddit.getMe();

        System.out.println("Possible Commands:");
        System.out.println("join, quit");

        // handling player input and sending game-commands to server via PM or comment
        while(true){
            switch (scanner.next()) {
                case "join":
                    System.out.println("Username for current game:");
                    username = scanner.next();
                    sendPM(reddit, "addPlayer " + username);
                    checkForAck(botOwner);
                    break;
                case "leave":
                    sendPM(reddit, "removePlayer " + username);
                    checkForAck(botOwner);
                    break;
                case "start":
                    action(subreddit, "startGame");
                    break;
                case "actions":
                    getAvailableActions(reddit,botOwner);
                    break;
                case "call":
                    action(subreddit, "call");
                    break;
                case "bet":
                    action(subreddit, "bet");
                    break;
                case "fold":
                    action(subreddit, "fold");
                    break;
                case "check":
                    action(subreddit, "check");
                    break;
                case "raise":
                    action(subreddit, "raise");
                    break;
                case "info":
                    sendPM(reddit, "getPlayerInformation");
                    checkForAck(botOwner);
                    break;
                case "update":
                    sendPM(reddit, "update");
                    checkForAck(botOwner);
                    break;
                case "shutdown":
                    sendPM(reddit, "serverShutdown");
                    checkForAck(botOwner);
                case "quit":
                    System.out.println("Terminating...");
                    System.exit(0);
                    break;
                default: break;
            }
            System.out.println("All Commands:\n start, leave, actions, bet/call/fold/raise, info, update, quit");
        }
    }

    // sends pm to server
    public static void sendPM(Reddit reddit, String command) throws RedditorNotFoundException {
        Redditor otherUser = reddit.getRedditor("pokersturm-acc01");
        otherUser.sendPrivateMessage("PokerSturm", command);
    }

    // wirte comment in post that represents current game
    public static void action(Subreddit ps, String action) throws Exception {
        List<Post> posts  = ps.postHistory().updateNew(5).getPosts();
        for (Post post: posts)  {
            if (post.getTitle().contains(postTitle))  {
                postTitle = post.getTitle();
                post.reply(action);
            }
        }
    }

    // checks pm inbox for server responses
    public static void checkForAck(BotOwner botOwner) throws Exception {
        String ack = "";
        while(ack == null || ack.isEmpty()){
            ack = handlePMs(botOwner);
        }
        System.out.println("Server Nachricht: " + ack);
    }

    // reads pms and generates output according to message
    public static String handlePMs(BotOwner botOwner) throws Exception {
        Thread.sleep(1800);
        List<InboxMessage> unread = botOwner.getUnreadInboxMessages();   //max 100
        String out = null;

        for (InboxMessage message: unread)  {
            botOwner.readPrivateMessage(message.getName());

            String msg = message.getBody();
            String subject = message.getSubject();

            if (subject.equals("PokerSturm-Server")){
                switch(msg){
                    case "add received":
                        out = "You joined the game";
                        break;
                    case "remove received":
                        out = "You left the game";
                        break;
                    case "screen refreshed":
                        out = "Screen refreshed";
                        break;
                    case "server shutting down...":
                        out = "Server shutting down...";
                        break;
                    default:
                        if (splitMsg(msg).equals("your player information") || splitMsg(msg).equals("available Actions")){
                            out = msg;
                        } else if (!msg.isEmpty()){
                            out = msg;
                        } else {
                            out = "No message.";
                        }
                }

            }

        }
        return out;
    }

    // Used to split PM from server containing player information
    public static String splitMsg(String msg){
        String[] str = msg.split(":");
        return str[0];
    }

    // Check all available actions
    public static void getAvailableActions(Reddit reddit, BotOwner botOwner) throws Exception {
        sendPM(reddit, "getAvailableActions");
        checkForAck(botOwner);
    }
}
