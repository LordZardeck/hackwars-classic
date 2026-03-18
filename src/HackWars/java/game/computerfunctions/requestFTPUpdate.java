package game.computerfunctions;

import game.*;
import assignments.*;
/**
 A function that is run within Computer.java.

 This function requests that the client side pulls an update of its FTP directories.
 */

import java.util.*;

import assignments.*;
import hackscript.model.*;

public class requestFTPUpdate extends function {
    public requestFTPUpdate(Computer MyComputer) {
        super(MyComputer);
    }

    public void execute(ApplicationData MyApplicationData) {
        PacketAssignment PA = super.getComputer().getPacketAssignment();
        PA.setRequestPrimary(true, 8);
        PA.setRequestSecondary(true, 8);
        super.getComputer().sendPacket();
    }
}
