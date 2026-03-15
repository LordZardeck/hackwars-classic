package game.computerfunctions;

import game.*;
/**
 Represents a watch which will fire given an appropriate event in the computer.
 */

import java.util.*;

import assignments.*;
import hackscript.model.*;
import assignments.*;

public abstract class function {
    private Computer MyComputer = null;

    public function(Computer MyComputer) {
        this.MyComputer = MyComputer;
    }

    public Computer getComputer() {
        return (MyComputer);
    }

    public abstract void execute(ApplicationData MyApplicationData);
}
