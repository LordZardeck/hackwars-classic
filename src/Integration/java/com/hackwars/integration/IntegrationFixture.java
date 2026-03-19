package com.hackwars.integration;

import assignments.PacketNetwork;
import assignments.PacketPort;
import game.HackerFile;
import gui.Hacker;

import java.util.ArrayList;
import java.util.HashMap;

public final class IntegrationFixture {
    private IntegrationFixture() {
    }

    public static PacketPort[] defaultPorts() {
        return new PacketPort[] {
            port(4, PacketPort.BANKING, "Integration Bank", true, true),
            port(6, PacketPort.FTP, "Integration FTP", true, true),
            port(3, PacketPort.ATTACK, "Integration Attack", true, true),
            port(8, PacketPort.HTTP, "Integration HTTP", true, true),
            port(9, PacketPort.REDIRECT, "Integration Redirect", true, true)
        };
    }

    public static Object[] homeDirectory() {
        return new Object[] {
            Integer.valueOf(Hacker.HOME),
            "Docs",
            fileRow("notes.txt", HackerFile.TEXT, 1, 12.5f, "Integration", 0.5f, "Starter notes", 3.0f),
            fileRow("virus.hs", HackerFile.ATTACKING_SCRIPT, 1, 25.0f, "Integration", 1.25f, "Attack script", 8.0f)
        };
    }

    public static Object[] storeDirectory() {
        return new Object[] {
            Integer.valueOf(Hacker.BROWSER),
            storeFile("Banker.bin", HackerFile.BANKING_COMPILED, "Integration", "Bank utility"),
            storeFile("PortProbe.bin", HackerFile.ATTACKING_COMPILED, "Integration", "Attack utility")
        };
    }

    public static String websiteBody() {
        return "<html><body><h1>Integration Hub</h1><p>Hello from integration web page.</p></body></html>";
    }

    public static PacketNetwork network(String storeIp) {
        PacketNetwork network = new PacketNetwork();
        network.setName("ROOT");
        network.setStoreIP(storeIp);
        network.setAttackNPCs(new ArrayList());
        network.setQuestNPCs(new ArrayList());
        network.setMiningNPCs(new ArrayList());
        network.setStoreNPCs(new ArrayList());
        return network;
    }

    private static PacketPort port(int number, int type, String note, boolean on, boolean isDefault) {
        PacketPort port = new PacketPort();
        port.setNumber(number);
        port.setType(type);
        port.setNote(note);
        port.setOn(on);
        port.setDummy(false);
        port.setHealth(100.0f);
        port.setCPUCost(1.0f);
        port.setMaxCPUCost(3.5f);
        port.setDefault(isDefault ? 1 : 0);
        return port;
    }

    private static Object[] fileRow(String name, int type, int quantity, float yourStorePrice, String maker, float cpuCost, String description, float sellPrice) {
        return new Object[] {
            name,
            Integer.valueOf(type),
            Integer.valueOf(quantity),
            Float.valueOf(yourStorePrice),
            maker,
            Float.valueOf(cpuCost),
            description,
            Float.valueOf(sellPrice)
        };
    }

    private static HackerFile storeFile(String name, int type, String maker, String description) {
        HackerFile file = new HackerFile(type);
        file.setName(name);
        file.setMaker(maker);
        file.setDescription(description);
        file.setPrice(30.0f);
        file.setQuantity(1);
        file.setCPUCost(1.0f);
        HashMap<String, Object> content = new HashMap<String, Object>();
        content.put("data", "<compiled />");
        file.setContent(content);
        return file;
    }
}
