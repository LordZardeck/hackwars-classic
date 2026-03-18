package game.computerfunctions;

import game.*;
import assignments.*;
/**
 A function that is run within Computer.java.

 Saves a note that has been associated with a port to indicate its function.
 */

import java.util.*;

import assignments.*;
import hackscript.model.*;

public class savePortNote extends function {
    public savePortNote(Computer MyComputer) {
        super(MyComputer);
    }

    public void execute(ApplicationData MyApplicationData) {
        int port = MyApplicationData.getPort();
        String note = (String) (MyApplicationData.getParameters());

        Iterator PortIterator = super.getComputer().getPorts().entrySet().iterator();
        while (PortIterator.hasNext()) {
            Port TempPort = (Port) (((Map.Entry) PortIterator.next()).getValue());
            if (TempPort.getNumber() == port) {
                TempPort.setNote(note);
            }
        }
    }
}
