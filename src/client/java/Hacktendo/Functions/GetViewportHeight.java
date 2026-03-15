package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;

public class GetViewportHeight extends LinkerFunctions {

    private OpenGLRenderEngine RE;
    private HacktendoLinker HL;

    public GetViewportHeight(RenderEngine RE, HacktendoLinker HL) {
        this.RE = (OpenGLRenderEngine) RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        return (new TypeInteger(RE.getViewportHeight()));
    }
}
