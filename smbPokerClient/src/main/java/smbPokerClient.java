import smbCommunication.smbCommunication;

import java.util.Scanner;
import java.io.File;
import java.io.FileReader;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class smbPokerClient {

    private static File configFile = new File("PokerCommunication.cfg");
    private static Properties props;
    private static String username;

    static void loadConfig() {
        try {
            FileReader reader = new FileReader(configFile);
            props = new Properties();
            props.load(reader);
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void main (String [] args) throws Exception {
        loadConfig();
        smbCommunication smb = new smbCommunication(props);
        Scanner scanner = new Scanner(System.in);

        startGameChecker sgc = new startGameChecker(smb);
        ExecutorService es = Executors.newCachedThreadPool();
        es.execute(sgc);

        // handling player input and sending game-commands to server via PM or comment
        while(true){
            while(true){
                System.out.println("Possible Commands:");
                System.out.println("join, leave, start, bet/call/fold/raise, info, update, shutdown,quit");
                switch (scanner.next()) {
                    case "join":
                        System.out.println("Username for current game:");
                        username = scanner.next();
                        smb.addPlayer(username);
                        break;
                    case "leave":
                        smb.removePlayer(username);
                        break;
                    case "start":
                        smb.request("start", username, "");
                        break;
                    case "call":
                        smb.request("call", username, "call");
                        break;
                    case "bet":
                        smb.request("call", username, "bet");
                        break;
                    case "fold":
                        smb.request("call", username, "fold");
                        break;
                    case "check":
                        smb.request("call", username, "check");
                        break;
                    case "raise":
                        smb.request("call", username, "raise");
                        break;
                    case "info":
                        System.out.println(smb.decryptFile(username + ".info","", username));
                        break;
                    case "update":
                        smb.request("update", username, "");
                        break;
                    case "quit":
                        System.out.println("Terminating...");
                        System.exit(0);
                        break;
                    default:
                        System.out.println("Wrong Command");
                        break;
                }
            }
        }
    }
}
