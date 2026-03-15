package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;

public class GetViewportX extends LinkerFunctions {

    private RenderEngine RE;
    private HacktendoLinker HL;

    public GetViewportX(RenderEngine RE, HacktendoLinker HL) {
        this.RE = RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        return (new TypeInteger(RE.getViewX()));
    }
}
