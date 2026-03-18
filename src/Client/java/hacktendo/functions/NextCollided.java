package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;

public class NextCollided extends LinkerFunctions {

    private RenderEngine RE;
    private HacktendoLinker HL;

    public NextCollided(RenderEngine RE, HacktendoLinker HL) {
        this.RE = RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        int spriteID = HL.getSpriteID();
        Sprite S = RE.getSprite(spriteID);
        if (S != null) {
            S.nextCollided();
        }
        return null;
    }
}
