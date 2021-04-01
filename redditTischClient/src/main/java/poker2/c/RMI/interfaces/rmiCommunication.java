package poker2.c.RMI.interfaces;

import java.net.*;
import java.rmi.*;

public class rmiCommunication {

    String rmiServer;
    IServer rmi;

    public rmiCommunication (String server) throws RemoteException, NotBoundException, MalformedURLException {
        rmiServer = server;
        rmi = (IServer) Naming.lookup(rmiServer);
        System.out.println("Connected to RMI-Server!");
    }

    public void addPlayer(String name) throws RemoteException {
        if(rmi.addPlayer(name))
            System.out.println("Successfully added Player " + name);
        else
            System.out.println("Failed to add Player " + name);
    }

    public void removePlayer(String name) throws RemoteException {
        if(rmi.removePlayer(name))
            System.out.println("Successfully removed Player " + name);
        else
            System.out.println("Failed to remove Player " + name);
    }

    public void update() throws RemoteException {
        rmi.update();
        System.out.println("Updated Screen");
    }

    public void startGame() throws RemoteException {
        if(rmi.startGame())
            System.out.println("Successfully started Game");
        else
            System.out.println("Failed to start Game");
    }

    public void call(String name, String action) throws RemoteException {
        if(rmi.call(name, action))
            System.out.println("Success! Player " + name + " Action: " + action);
        else
            System.out.println("Failure! Player " + name + " Action: " + action);
    }

    public String getCurrentPlayer() throws RemoteException {
        return rmi.getCurrentPlayer();
    }

    public String getPlayerInformation(String name) throws RemoteException {
        return rmi.getPlayerInformation(name);
    }

    public String getAvailableActions(String name) throws RemoteException {
        return rmi.getAvailableActions(name);
    }

    public String getState() throws RemoteException {
        return rmi.getState();
    }
}
