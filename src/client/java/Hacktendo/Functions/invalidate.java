package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;
import game.mmo.*;

public class invalidate extends LinkerFunctions {

    private MMOEngine RE;
    private HacktendoLinker HL;
    private boolean initialized = false;

    public invalidate(RenderEngine RE, HacktendoLinker HL) {
        if (RE instanceof MMOEngine) {
            this.RE = (MMOEngine) RE;
            this.HL = HL;
            initialized = true;
        }
    }

    public Object execute(ArrayList parameters) {
        if (initialized) {
            RE.invalidate(HL.getSpriteID());
        }

        return null;
    }
}
