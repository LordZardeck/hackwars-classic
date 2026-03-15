package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;

public class SetViewportY extends LinkerFunctions {

    private RenderEngine RE;
    private HacktendoLinker HL;

    public SetViewportY(RenderEngine RE, HacktendoLinker HL) {
        this.RE = RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        int val;
        if (parameters.get(0) instanceof TypeInteger)
            val = (int) (Integer) ((TypeInteger) parameters.get(0)).getRawValue();
        else
            val = (int) (float) (Float) ((TypeFloat) parameters.get(0)).getRawValue();

        RE.setViewY(val);
        return null;
    }
}
