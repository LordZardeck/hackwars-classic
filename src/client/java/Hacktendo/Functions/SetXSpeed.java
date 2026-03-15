package hacktendo.functions;


import java.util.ArrayList;

import hackscript.model.*;
import hacktendo.*;

public class SetXSpeed extends LinkerFunctions {

    private RenderEngine RE;
    private HacktendoLinker HL;

    public SetXSpeed(RenderEngine RE, HacktendoLinker HL) {
        this.RE = RE;
        this.HL = HL;
    }

    public Object execute(ArrayList parameters) {
        int speed = (int) (Integer) ((TypeInteger) parameters.get(0)).getRawValue();
        int time = (int) (Integer) ((TypeInteger) parameters.get(1)).getRawValue();
        RE.setSpeedX(speed);
        RE.setXEndTime(time);
        return (new TypeFloat(0));
    }
}
