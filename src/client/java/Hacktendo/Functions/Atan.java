package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;

public class Atan extends LinkerFunctions {

    private RenderEngine RE;
    private HacktendoLinker HL;

    public Atan(RenderEngine RE, HacktendoLinker HL) {
        this.RE = RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        float val = (Float) ((TypeFloat) parameters.get(0)).getRawValue();
        float returnMe = (float) Math.atan(val) * HacktendoLinker.V180_OVER_PI;
        return (new TypeFloat(returnMe));
    }
}
