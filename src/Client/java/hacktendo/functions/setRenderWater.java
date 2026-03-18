package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;

public class setRenderWater extends LinkerFunctions {

    private OpenGLRenderEngine RE;
    private HacktendoLinker HL;

    public setRenderWater(RenderEngine RE, HacktendoLinker HL) {
        this.RE = (OpenGLRenderEngine) RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        boolean val = false;

        if (parameters.get(0) instanceof TypeBoolean)
            val = (Boolean) ((TypeBoolean) parameters.get(0)).getRawValue();

        RE.setRenderWater(val);
        return null;
    }
}
