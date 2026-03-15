package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;

public class getViewportXRotation extends LinkerFunctions {

    private OpenGLRenderEngine RE;
    private HacktendoLinker HL;

    public getViewportXRotation(RenderEngine RE, HacktendoLinker HL) {
        this.RE = (OpenGLRenderEngine) RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        return (new TypeFloat(RE.getXRotation()));
    }
}
