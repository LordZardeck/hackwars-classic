package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;

public class setBeat extends LinkerFunctions {

    private RenderEngine RE;
    private HacktendoLinker HL;

    public setBeat(RenderEngine RE, HacktendoLinker HL) {
        this.RE = RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        RE.setBeat(true);
        return (null);
    }
}
