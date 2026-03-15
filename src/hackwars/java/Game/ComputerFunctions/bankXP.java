package game.computerfunctions;

import game.*;
import assignments.*;
/**
 A function that is run within Computer.java.

 Gives a player experience in their banking skill.
 */

import java.util.*;

import assignments.*;
import hackscript.model.*;

public class bankXP extends function {
    public bankXP(Computer MyComputer) {
        super(MyComputer);
    }

    public void execute(ApplicationData MyApplicationData) {
        HashMap Stats = super.getComputer().getStats();
        super.getComputer().sendDamagePacket();
        Float amount = (Float) MyApplicationData.getParameters();
        float mult = 1.0f;
        amount *= mult;
        amount += (Float) Stats.get("Bank");
        Stats.put("Bank", amount);
    }
}
