package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;

public class Char extends LinkerFunctions {

    private RenderEngine RE;
    private HacktendoLinker HL;

    public Char(RenderEngine RE, HacktendoLinker HL) {
        this.RE = RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        int data = (int) (Integer) ((TypeInteger) parameters.get(0)).getRawValue();

        return (new TypeString("" + (char) data));
    }
}
