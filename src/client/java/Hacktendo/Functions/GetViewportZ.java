package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;

public class GetViewportZ extends LinkerFunctions {

    private OpenGLRenderEngine RE;
    private HacktendoLinker HL;

    public GetViewportZ(RenderEngine RE, HacktendoLinker HL) {
        this.RE = (OpenGLRenderEngine) RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        return (new TypeInteger(-1 * RE.getViewZ()));
    }
}
