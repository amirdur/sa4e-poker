import redditCommunication.redditCommunication;

import java.io.*;
import java.util.Properties;

/*
RedditTischClient

Folgende Daten werden benoetigt:
- Reddit-Account (username, password)
- API-Zugriff fuer den Account (appId, appSecret)

appId und appSecret koennen unter
https://www.reddit.com/prefs/apps
erstellt werden.

"Create another app", name vergeben,
script auswaehlen, about und redirect
koennen zB auf https://reddit.com gesetzt werden

appId steht unter personal use script appSecret unter secret
unter developers koennen mehrere Accounts hinterlegt werden (TischClient, mehrere Spielerclients etc)

Alle vier Parameter in die redditTischClient.cfg eintragen
 */

public class redditTischClient {
    public static void main (String [] args) throws Exception {
        File configFile = new File("redditTischClient.cfg");

        try {
            FileReader reader = new FileReader(configFile);
            Properties props = new Properties();
            props.load(reader);
            redditCommunication reddit = new redditCommunication(props.getProperty("reddit.username"),
                    props.getProperty("reddit.password"), props.getProperty("reddit.appId"), props.getProperty("reddit.appSecret"));
            reddit.runServer();
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
