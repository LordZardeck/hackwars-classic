package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;

public class SetMusic extends LinkerFunctions {

    private RenderEngine RE;
    private HacktendoLinker HL;

    public SetMusic(RenderEngine RE, HacktendoLinker HL) {
        this.RE = RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        int id = (int) (Integer) ((TypeInteger) parameters.get(0)).getRawValue();
        return null;
    }
}
