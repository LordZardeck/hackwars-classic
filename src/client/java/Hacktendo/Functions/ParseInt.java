package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;

public class ParseInt extends LinkerFunctions {

    private RenderEngine RE;
    private HacktendoLinker HL;

    public ParseInt(RenderEngine RE, HacktendoLinker HL) {
        this.RE = RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        String val = ((TypeString) parameters.get(0)).getStringValue();

        return (new TypeInteger((int) Integer.parseInt(val)));
    }
}
