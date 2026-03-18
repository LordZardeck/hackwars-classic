package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;

public class Floor extends LinkerFunctions {

    private RenderEngine RE;
    private HacktendoLinker HL;

    public Floor(RenderEngine RE, HacktendoLinker HL) {
        this.RE = RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        float val = (float) (Float) ((TypeFloat) parameters.get(0)).getRawValue();
        return (new TypeInteger((int) Math.floor(val)));
    }
}
