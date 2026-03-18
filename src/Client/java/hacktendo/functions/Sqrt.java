package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;

public class Sqrt extends LinkerFunctions {

    private RenderEngine RE;
    private HacktendoLinker HL;

    public Sqrt(RenderEngine RE, HacktendoLinker HL) {
        this.RE = RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        float val = (Float) ((TypeFloat) parameters.get(0)).getRawValue();
        return new TypeFloat((float) Math.sqrt(val));
    }
}
