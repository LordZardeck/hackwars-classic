package game.computerfunctions;

import game.*;
import assignments.*;
/**
 A function that is run within Computer.java.

 Reward a player FTP experience.
 */

import java.util.*;

import assignments.*;
import hackscript.model.*;

public class setFTPPassword extends function {
    public setFTPPassword(Computer MyComputer) {
        super(MyComputer);
    }

    public void execute(ApplicationData MyApplicationData) {
        super.getComputer().setPassword((String) MyApplicationData.getParameters());
    }
}
