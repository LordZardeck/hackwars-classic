package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;

public class GetKeyDownCount extends LinkerFunctions {

    private RenderEngine RE;
    private HacktendoLinker HL;

    public GetKeyDownCount(RenderEngine RE, HacktendoLinker HL) {
        this.RE = RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        return (new TypeInteger(RE.getKeyDownCount()));
    }
}
