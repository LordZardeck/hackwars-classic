package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;
import com.plink.hack3d.*;

public class setTerrain extends LinkerFunctions {

    private OpenGLRenderEngine RE;
    private HacktendoLinker HL;

    public setTerrain(RenderEngine RE, HacktendoLinker HL) {
        this.RE = (OpenGLRenderEngine) RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        try {
            int val = 0;

            if (parameters.get(0) instanceof TypeFloat)
                val = (int) (float) (Float) ((TypeFloat) parameters.get(0)).getRawValue();
            else if (parameters.get(0) instanceof TypeInteger)
                val = (Integer) ((TypeInteger) parameters.get(0)).getRawValue();

            RE.requestLoadTerrain(val);
        } catch (Exception e) {
        }
        return null;
    }
}
