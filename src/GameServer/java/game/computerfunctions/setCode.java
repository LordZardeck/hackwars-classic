package game.computerfunctions;

import game.*;
import assignments.*;
/**
 A function that is run within Computer.java.

 This function is used to set the code that is being peeked at in a packet.
 */

import java.util.*;

import assignments.*;
import hackscript.model.*;

public class setCode extends function {
    public setCode(Computer MyComputer) {
        super(MyComputer);
    }

    public void execute(ApplicationData MyApplicationData) {
        super.getComputer().sendPacket();
        HashMap PeakCode = (HashMap) MyApplicationData.getParameters();
        super.getComputer().getPacketAssignment().setPeakCode(PeakCode);
    }
}
