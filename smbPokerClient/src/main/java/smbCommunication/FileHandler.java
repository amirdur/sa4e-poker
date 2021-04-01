package smbCommunication;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileOutputStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FileHandler {

    private Properties props;
    private final String baseDir;
    private final String user;
    private final String pass;
    private final NtlmPasswordAuthentication auth;

    public FileHandler(Properties props) {
        this.props = props;
        this.baseDir = props.getProperty("smb.baseDir");
        this.user = props.getProperty("smb.username");
        this.pass = props.getProperty("smb.password");
        auth = new NtlmPasswordAuthentication("",user, pass);
    }

    public void deleteFile(String path) {
        try {
            if(props.getProperty("smb.localmode").equals("true")) {
                new File(baseDir + path).delete();
            } else {
                new SmbFile(baseDir + path, auth).delete();
            }
        } catch (MalformedURLException | SmbException e) {
            e.printStackTrace();
        }
    }

    public byte[] readFile(String path) {
        try {
            if(props.getProperty("smb.localmode").equals("true")) {
                return Files.readAllBytes(Paths.get(baseDir + path));
            } else {
                System.out.println("Read from: " + baseDir + path);
                SmbFile file = new SmbFile(baseDir + path, auth);
                return file.getInputStream().readAllBytes();
            }
        } catch (Exception e) {
            return null;
        }
    }

    public boolean writeFile(String path, byte[] content) {
        try {
            if(props.getProperty("smb.localmode").equals("true")) {
                OutputStream o = new FileOutputStream(baseDir + path);
                o.write(content);
                o.close();
            } else {
                System.out.println("Write to: " + baseDir + path);
                SmbFile smbFile = new SmbFile(baseDir + path, auth);
                SmbFileOutputStream o = new SmbFileOutputStream(smbFile);
                o.write(content);
                o.close();
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public List<String> findFiles(String path, String mask) {
        try {
            if(props.getProperty("smb.localmode").equals("true")) {
                File[] list = new File(baseDir + path).listFiles((dir, name) -> {
                    String p = mask.replace(".", "\\.").replace("*", ".*");
                    return Pattern.matches(p, name);
                });
                assert list != null;
                return Arrays.stream(list).map(File::getName).collect(Collectors.toList());
            } else {
                SmbFile[] list = new SmbFile(baseDir + path, auth).listFiles((dir, name) -> {
                    String p = mask.replace(".", "\\.").replace("*", ".*");
                    return Pattern.matches(p, name);
                });
                assert list != null;
                return Arrays.stream(list).map(SmbFile::getName).collect(Collectors.toList());
            }
        } catch (MalformedURLException | SmbException e) {
            e.printStackTrace();
        }
        return null;
    }
}