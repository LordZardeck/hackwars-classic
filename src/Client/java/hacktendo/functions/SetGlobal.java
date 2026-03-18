package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;

public class SetGlobal extends LinkerFunctions {

    private RenderEngine RE;
    private HacktendoLinker HL;

    public SetGlobal(RenderEngine RE, HacktendoLinker HL) {
        this.RE = RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        String key = (String) ((TypeString) parameters.get(0)).getStringValue();
        Object val = parameters.get(1);
        HL.addGlobal(key, val);
        return null;
    }
}
