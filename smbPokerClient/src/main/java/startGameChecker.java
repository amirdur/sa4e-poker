import smbCommunication.smbCommunication;

import java.util.Scanner;

public class startGameChecker implements Runnable{
    private smbCommunication smb;
    private String username;
    private boolean started = false;

    public startGameChecker(smbCommunication smb) {
        this.smb = smb;
    }

    public void run() {
        while(!started){
            if(smb.findShared()){
                System.out.println("Game starting");
                try {
                    smb.keyExchange();
                    started = true;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                break;
            }
        }
        System.out.println("join, leave, start, bet/call/fold/raise, info, update, shutdown,quit");


    }
}
