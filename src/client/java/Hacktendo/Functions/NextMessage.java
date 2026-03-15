package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;

public class NextMessage extends LinkerFunctions {

    private RenderEngine RE;
    private HacktendoLinker HL;

    public NextMessage(RenderEngine RE, HacktendoLinker HL) {
        this.RE = RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        int spriteID = HL.getSpriteID();
        Sprite MySprite = RE.getSprite(spriteID);
        MySprite.nextMessage();
        return null;
    }
}
