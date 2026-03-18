package game.computerfunctions;

import game.*;
import assignments.*;
/**
 A function that is run within Computer.java.

 This function adds to the show choices array. This tells the client to display show choices options.
 */

import java.util.*;

import assignments.*;
import hackscript.model.*;

public class addShowChoices extends function {
    public addShowChoices(Computer MyComputer) {
        super(MyComputer);
    }

    public void execute(ApplicationData MyApplicationData) {
        super.getComputer().sendPacket();
        super.getComputer().sendDamagePacket();
        super.getComputer().getShowChoicesArray().add(MyApplicationData.getParameters());
    }
}
