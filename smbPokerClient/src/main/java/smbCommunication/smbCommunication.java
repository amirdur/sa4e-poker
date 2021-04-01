package smbCommunication;

import org.json.JSONException;
import org.json.JSONObject;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class smbCommunication {
    private FileHandler file;
    private Properties props;

    private KeyAgreement keyAgreement;
    private final Map<String, byte[]> dhKeys;
    private static byte[] dhKey;

    public smbCommunication(Properties props) {
        this.props = props;
        dhKeys = new HashMap<>();
        file = new FileHandler(props);
    }

    public void addPlayer(String name) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(256);
            KeyPair kp = kpg.generateKeyPair();
            keyAgreement = KeyAgreement.getInstance("ECDH");
            keyAgreement.init(kp.getPrivate());

            System.out.println("Generated Player: " + name + " and written to disk");
            file.writeFile(name + ".dh1", kp.getPublic().getEncoded());

            while (file.readFile(name + ".dh2") == null) {
                Thread.sleep(500);
            }

            byte[] dh1 = file.readFile(name + ".dh2");
            System.out.println("Read from disk: " + name + ".dh2");

            KeyFactory kf = KeyFactory.getInstance("EC");
            X509EncodedKeySpec x509 = new X509EncodedKeySpec(dh1);
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(kp.getPrivate());
            ka.doPhase(kf.generatePublic(x509), true);
            dhKeys.put(name, ka.generateSecret());
            System.out.println("Key Exchange completed for Player " + name);

        } catch (NoSuchAlgorithmException | InvalidKeyException | InterruptedException | InvalidKeySpecException e) {
            e.printStackTrace();
        }

        request("addplayer", name, "");
    }

    public void removePlayer(String name) {
        if(request("removeplayer", name, "")) dhKeys.remove(name);
    }

    public boolean request(String method, String name, String action) {
        JSONObject req = new JSONObject();
        req.put("method", method);
        req.put("name", name);
        req.put("action", action);
        file.writeFile(name + ".request", encrypt(dhKeys.get(name),  req.toString().getBytes(StandardCharsets.UTF_8)));

        while(file.readFile(name + ".response") == null) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        JSONObject response = new JSONObject(new String(file.readFile(name + ".response")));
        file.deleteFile(name + ".response");

        if(!response.getString("status").equals("Success")) System.out.println("request failed");
        return response.getString("status").equals("Success");
    }

    public boolean findShared(){
        List<String> list = file.findFiles("", "*." + "[1-9]" + ".shared");
        if (list.size() > 0){
            return true;
        }
        return false;
    }

    public void keyExchange() throws InterruptedException {
        int num = Integer.parseInt(new String(file.readFile("waiting"), StandardCharsets.UTF_8).split(",")[0]) + 1;
        ExecutorService es = Executors.newCachedThreadPool();

        for(String player : dhKeys.keySet()) {
            keyExchangeWorker k = new keyExchangeWorker(num, player, props);
            es.execute(k);
        }

        es.shutdown();
        es.awaitTermination(1, TimeUnit.MINUTES);
        System.out.println("Our Key: " + new String(dhKey, StandardCharsets.UTF_8));
    }

    public static void setDhKey(byte[] key) {
        dhKey = key;
    }

    public String decryptFile(String fileName, String keyWord, String name) {
        if(file.readFile(fileName) == null) return null;
        JSONObject obj;
        if(fileName.startsWith("all.")){
            obj = new JSONObject(new String(decrypt(dhKey, file.readFile(fileName))));
        } else {
            obj = new JSONObject(new String(decrypt(dhKeys.get(name), file.readFile(fileName))));
        }
        try {
           return obj.getString(keyWord);
        }catch (JSONException e){
            return obj.toString(4);
        }
    }

    public byte[] encrypt(byte[] key, byte[] data) {
        SecureRandom r = new SecureRandom();
        try {
            SecretKeySpec spec = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[12];
            r.nextBytes(iv);
            data = ByteBuffer.allocate(255 - 24).putInt(data.length).put(data).array();
            cipher.init(Cipher.ENCRYPT_MODE, spec, new GCMParameterSpec(96, iv));
            return ByteBuffer.allocate(24 + data.length).put(iv).put(cipher.doFinal(data)).array();
        } catch (Exception e) {
            return null;
        }
    }

    public byte[] decrypt(byte[] key, byte[] data) {
        try {
            SecretKeySpec spec = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[12];
            ByteBuffer bb = ByteBuffer.wrap(data);
            byte[] enc = new byte[bb.get(iv).limit() - 12];
            bb.get(enc);
            cipher.init(Cipher.DECRYPT_MODE, spec, new GCMParameterSpec(96, iv));
            bb = ByteBuffer.wrap(cipher.doFinal(enc));
            byte[] dec = new byte[bb.getInt()];
            bb.get(dec);
            return dec;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private byte[] pad(byte[] in, int len) {
        byte[] out = new byte[len];
        System.arraycopy(in, Math.max(0, in.length - len), out, Math.max(0, len - in.length), Math.min(len, in.length));
        return out;
    }

    public byte[] pad(byte[] data) {
        SecureRandom r = new SecureRandom();
        BigInteger m = BigInteger.TWO.pow(2048).subtract(BigInteger.valueOf(1557));
        BigInteger[] a = new BigInteger[8];
        BigInteger[] b = new BigInteger[8];
        BigInteger[] c = new BigInteger[5];
        ByteBuffer out = ByteBuffer.allocate(4096);
        for (int i = 0; i < 5; i++) {
            c[i] = new BigInteger(2047, r);
        }
        for (int i = 0; i < 8; i++) {
            a[i] = new BigInteger(2047, r);
            b[i] = new BigInteger(1, data);
            for (int j = 0; j < 5; j++) {
                b[i] = b[i].add(c[j].multiply(a[i].modPow(BigInteger.valueOf(j + 1), m)).mod(m)).mod(m);
            }
            out.put(pad(a[i].toByteArray(), 256));
            out.put(pad(b[i].toByteArray(), 256));
        }
        return out.array();
    }
}
