package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;
import util.*;

public class CheckTimeStamp extends LinkerFunctions {

    private RenderEngine RE;
    private HacktendoLinker HL;

    public CheckTimeStamp(RenderEngine RE, HacktendoLinker HL) {
        this.RE = RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        String key = (String) ((TypeString) parameters.get(0)).getStringValue();
        long timerValue = HL.checkTimer(key);
        return (new TypeInteger((int) (Time.getInstance().getCurrentTime() - timerValue)));
    }
}
