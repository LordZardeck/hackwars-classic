package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;

public class setSky extends LinkerFunctions {

    private OpenGLRenderEngine RE;
    private HacktendoLinker HL;

    public setSky(RenderEngine RE, HacktendoLinker HL) {
        this.RE = (OpenGLRenderEngine) RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        int val = 0;

        if (parameters.get(0) instanceof TypeInteger)
            val = (Integer) ((TypeInteger) parameters.get(0)).getRawValue();

        RE.setSky(val);
        return null;
    }
}
