package server;

/**
 * (c) Vulgate 2007<br />
 * The main server entry point, pulls everything together.
 */

import assignments.LoginAssignment;
import assignments.MessageInPacket;
import assignments.PingAssignment;
import com.plink.dolphinnet.*;
import com.plink.dolphinnet.MessageServer;

import java.util.*;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.Time;
import util.UserHandler;
import util.PlayFabTokenVerifier;
import util.SessionTokenVerifiers;

public class ChatServer extends MessageCoordinator implements Runnable {
    private static final Logger Logger = LoggerFactory.getLogger(ChatServer.class);

    //Data.
    private UserHandler MyUserHandler = null;
    private Time MyTime = null;
    private final Semaphore available = new Semaphore(1, true);
    private Thread MyThread = null;
    private ArrayList Tasks = new ArrayList();

    public ChatServer(MessageServer e) {//Used for constructor just keep this here in IPartys.
        super(e);
        MyTime = new Time();
        MyUserHandler = new UserHandler(MyTime, this);
        MyThread = new Thread(this, "Chat Server Thread.");
        MyThread.start();
    }

    /** Receive a failed assignment.*/
    public synchronized void failedAssignment(Assignment a) {

    }

    /**
     Dispatch a packet assignment.
     */
    public void dispatchPacket(Assignment DispatchMe, int connectionID) {
        try {
            available.acquire();
            Tasks.add(new Object[]{DispatchMe, new Integer(connectionID)});
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            available.release();
        }
    }

    /**
     Inform a client that they are no longer logged in.
     */
    public void logout(int connectionID) {
        try {
            //System.out.println("Trying to acquire.");
            available.acquire();
            //System.out.println("You're stuck here!");
            Tasks.add(new Integer(connectionID));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            available.release();
        }
    }

    /** Receive a completed assignment.*/
    public void returnAssignment(Assignment MyAssignment) {
        try {
            available.acquire();
            Tasks.add(MyAssignment);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            available.release();
        }
    }

    static void main() {
        try {
            MessageServer E = new MessageServer(2048, 1000, 10025, 10026);//Creates a new server for distributing tasks.
            E.setClientJobSize(4);
            new ChatServer(E);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run() {
        int i = 0;
        while (true) {
            try {
                Thread.sleep(5);
            } catch (Exception e) {

            }

            i++;

            Object o = null;
            try {
                available.acquire();
                Iterator MyIterator = Tasks.iterator();
                if (MyIterator.hasNext()) {
                    o = MyIterator.next();
                    MyIterator.remove();
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                available.release();
            }

            if (o instanceof Object[]) {
                Assignment DispatchMe = (Assignment) ((Object[]) o)[0];
                int connectionID = (Integer) ((Object[]) o)[1];

                //System.out.println("Sending Logout Assignment.");
                ClientBinaryList MyClientBinaryList = this.getEditor().getClients();
                ClientData MyClientData = (ClientData) MyClientBinaryList.get(new Integer(connectionID));
                if (MyClientData != null) {
                    MyClientData.addJob(DispatchMe);
                }
                //System.out.println("Assignment sent.");
            } else if (o instanceof LoginAssignment) {
                Assignment MyAssignment = (Assignment) o;
                LoginAssignment MyLoginAssignment = (LoginAssignment) MyAssignment;
                String accessToken = MyLoginAssignment.getAccessToken();
                PlayFabTokenVerifier.AuthResult authResult = null;
                try {
                    authResult = SessionTokenVerifiers.active().verify(accessToken);
                } catch (Exception authError) {
                    Logger.error("ChatServer: PlayFab authentication failed", authError);
                }

                if (authResult == null) {
                    logout(MyLoginAssignment.getReporterID());
                } else {
                    String user = authResult.getPlayFabId().toLowerCase();
                    MyUserHandler.loadPlayer(MyLoginAssignment.getReporterID(), user, "");
                }
            } else if (o instanceof MessageInPacket) {
                Assignment MyAssignment = (Assignment) o;
                MyUserHandler.addPacket((MessageInPacket) MyAssignment);
            } else if (o instanceof PingAssignment) {
                //System.out.println("Got Ping Assignment In.");
                Assignment MyAssignment = (Assignment) o;
                dispatchPacket(new PingAssignment(0, ""), MyAssignment.getReporterID());
                MyUserHandler.pingPlayer((PingAssignment) MyAssignment);
                //System.out.println("Finished Pinging player.");
            } else if (o instanceof Integer) {
                int connectionID = (Integer) o;
                ClientBinaryList MyClientBinaryList = this.getEditor().getClients();
                ClientData MyClientData = (ClientData) MyClientBinaryList.get(new Integer(connectionID));
                if (MyClientData != null) {
                    MyClientData.addJob(new LoginAssignment(0, "", "", ""));
                }
            }

        }
    }

    /**
     Used to encrypt and decrypt XOR encrypted data.
     */
    static private String crypt(byte[] data, String key) {
        for (int ii = 0; ii < data.length; ii++) {
            data[ii] ^= (int) (key.getBytes())[ii % key.length()];
        }
        return (new String(data));
    }
}
