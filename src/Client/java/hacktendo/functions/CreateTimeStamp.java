package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;
import util.*;

public class CreateTimeStamp extends LinkerFunctions {

    private RenderEngine RE;
    private HacktendoLinker HL;

    public CreateTimeStamp(RenderEngine RE, HacktendoLinker HL) {
        this.RE = RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        String key = (String) ((TypeString) parameters.get(0)).getStringValue();
        HL.addTimer(key, new Long(GameClock.nowMillis()));
        return (new TypeFloat(0));
    }
}
