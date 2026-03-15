package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;

public class ReplaceAll extends LinkerFunctions {

    private RenderEngine RE;
    private HacktendoLinker HL;

    public ReplaceAll(RenderEngine RE, HacktendoLinker HL) {
        this.RE = RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        String s1 = ((TypeString) parameters.get(0)).getStringValue();
        String s2 = ((TypeString) parameters.get(1)).getStringValue();
        String s3 = ((TypeString) parameters.get(2)).getStringValue();
        return (new TypeString(s1.replaceAll(s2, s3)));
    }
}
