package game.computerfunctions;

import game.*;
/**
 A function that is run within Computer.java.

 This function delete's a player's logs.
 */

import java.util.*;

import assignments.*;
import hackscript.model.*;

public class deleteLogs extends function {
    public deleteLogs(Computer MyComputer) {
        super(MyComputer);
    }

    public void execute(ApplicationData MyApplicationData) {
        super.getComputer().resetLogs();
        super.getComputer().sendPacket();
    }
}
