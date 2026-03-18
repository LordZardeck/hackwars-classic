package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;

public class GetLocal extends LinkerFunctions {

    private RenderEngine RE;
    private HacktendoLinker HL;

    public GetLocal(RenderEngine RE, HacktendoLinker HL) {
        this.RE = RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        if (parameters.size() >= 2) {
            int id = (int) (Integer) ((TypeInteger) parameters.get(0)).getRawValue();
            String key = (String) ((TypeString) parameters.get(1)).getStringValue();
            Sprite MySprite = RE.getSprite(id);
            Object O = MySprite.getParameter(key);

            return O;
        } else {
            int spriteID = HL.getSpriteID();


            String key = (String) ((TypeString) parameters.get(0)).getStringValue();
            Sprite MySprite = RE.getSprite(spriteID);
            Object O = MySprite.getParameter(key);

            return O;
        }
    }
}
