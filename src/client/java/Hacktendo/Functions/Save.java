package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;

import java.util.*;

public class Save extends LinkerFunctions {

    private RenderEngine RE;
    private HacktendoLinker HL;

    public Save(RenderEngine RE, HacktendoLinker HL) {
        this.RE = RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {

        HashMap TriggerParam = new HashMap();
        for (int i = 0; i < parameters.size(); i += 2) {
            String key = ((TypeString) parameters.get(i)).getStringValue();
            TriggerParam.put(key, parameters.get(i + 1));
        }

        RE.setSaveFile(TriggerParam);
        return null;
    }
}
