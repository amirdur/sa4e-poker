package smbCommunication;

import javax.crypto.KeyAgreement;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class keyExchangeWorker implements Runnable{

    private Thread t;
    private byte[] dhKey;
    private int num;
    private String clientID;
    private FileHandler fh;

    public keyExchangeWorker(int num, String player, Properties props) {
        this.num = num;
        this.clientID = player;
        this.fh = new FileHandler(props);
    }

    public void run() {
        int id = 1;
        try {
            KeyFactory kf = KeyFactory.getInstance("DH");
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH");
            KeyPair kp = kpg.generateKeyPair();

            KeyAgreement ka = KeyAgreement.getInstance("DH");
            ka.init(kp.getPrivate());
            fh.writeFile(clientID + ".1.shared", kp.getPublic().getEncoded());

            while (id != num) {
                TimeUnit.MILLISECONDS.sleep(10);
                List<String> list = fh.findFiles("", "*." + id + ".shared");
                if (list.size() == num) {
                    Collections.sort(list);
                    int pos = list.indexOf(clientID + "." + id + ".shared");
                    String file = list.get((pos + 1) % num);
                    byte[] in = fh.readFile(file);
                    PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(in));
                    id++;
                    if (id != num) {
                        Key key = ka.doPhase(publicKey, false);
                        fh.writeFile(clientID + "." + id + ".shared", key.getEncoded());
                    } else {
                        ka.doPhase(publicKey, true);
                        dhKey = MessageDigest.getInstance("SHA256").digest(ka.generateSecret());
                        smbCommunication.setDhKey(dhKey);
                    }
                }
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException | InterruptedException | InvalidKeySpecException e) {
            e.printStackTrace();
        }
    }
}

