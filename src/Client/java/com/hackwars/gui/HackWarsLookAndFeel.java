package com.hackwars.gui;

import javax.swing.*;
import javax.swing.plaf.metal.MetalLookAndFeel;

public class HackWarsLookAndFeel extends MetalLookAndFeel {
    @Override
    protected void initClassDefaults(UIDefaults table) {
        super.initClassDefaults(table);

        final String metalPackageName = "com.hackwars.gui.";

        Object[] uiDefaults = {
                "LoginTextFieldUI", metalPackageName + "login.LoginTextFieldUI",
                "LoginPasswordFieldUI", metalPackageName + "login.LoginPasswordFieldUI",
        };

        table.putDefaults(uiDefaults);
    }
}
