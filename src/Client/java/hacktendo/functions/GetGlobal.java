package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;

public class GetGlobal extends LinkerFunctions {

    private RenderEngine RE;
    private HacktendoLinker HL;

    public GetGlobal(RenderEngine RE, HacktendoLinker HL) {
        this.RE = RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        String key = (String) ((TypeString) parameters.get(0)).getStringValue();
        Object O = HL.getGlobal(key);
        if (O == null)
            return (new TypeString(""));
        return O;
    }
}
