package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;

public class IsPassable extends LinkerFunctions {

    private RenderEngine RE;
    private HacktendoLinker HL;

    public IsPassable(RenderEngine RE, HacktendoLinker HL) {
        this.RE = RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        int x = (int) (Integer) ((TypeInteger) parameters.get(0)).getRawValue();
        int y = (int) (Integer) ((TypeInteger) parameters.get(1)).getRawValue();
        int tileID = (x / 32) + (y / 32) * 72;
        return (new TypeBoolean(RE.isPassable(tileID)));
    }
}
