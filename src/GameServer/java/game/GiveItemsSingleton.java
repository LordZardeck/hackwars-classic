package game;
/**
 * This singleton delivers files to a player when they purchase them via the Game's store.
 */

import com.hackwars.data.model.PendingPurchase;
import com.hackwars.data.service.GameWorldDataService;
import game.data.GameServerDataLocator;
import game.payload.SaveFileRequestPayload;

import java.util.ArrayList;
import java.util.List;

public class GiveItemsSingleton {
    //MYSQL INFO.
    public static int SAVE_COUNTER = 0;
    private static volatile GameWorldDataService worldDataService = null;

    //The singleton class.
    private static GiveItemsSingleton instance = null;

    public static GiveItemsSingleton getInstance() {
        if (instance == null) {
            instance = new GiveItemsSingleton();
        }

        return (instance);
    }

    public static synchronized void installWorldDataServiceForTests(GameWorldDataService service) {
        worldDataService = service;
    }

    public static synchronized void resetWorldDataServiceForTests() {
        worldDataService = null;
    }

    private static GameWorldDataService worldDataService() {
        if (worldDataService == null) {
            worldDataService = GameServerDataLocator.worldService();
        }
        return worldDataService;
    }

    //Make the constructor private.
    private GiveItemsSingleton() {

    }

    private void queueSaveFile(Computer playerComputer, ComputerHandler myComputerHandler, HackerFile file) {
        myComputerHandler.addData(
            new ApplicationData(new SaveFileRequestPayload("", file), 0, playerComputer.getIP()),
            playerComputer.getIP()
        );
    }

    /**
     The following functions provide the player with the items they have purchased,
     this logic is specific enough it requires multiple functions.

     DataShield
     DigitalFortress
     */
    public boolean givePackage1(Computer PlayerComputer, ComputerHandler MyComputerHandler) {
        try {
            ArrayList Items = new ArrayList();
            int basic = 2 + (int) (Math.random() * 2.0);
            int medium = 1 + (int) (Math.random() * 2.0);

            NewFireWall F = new NewFireWall();
            for (int i = 0; i < basic; i++) {
                Items.add(F.generateFirewall("DataShield"));
            }

            for (int i = 0; i < medium; i++) {
                Items.add(F.generateFirewall("DigitalFortress"));
            }

            if (PlayerComputer.getFileSystem().getSpaceLeft() < Items.size()) {
                PlayerComputer.addMessage("You did not have enough HD space to receive the files you purchased from the HackWars online store. They will be given to you when you delete some files.");
                return (false);
            } else {
                for (int i = 0; i < Items.size(); i++) {
                    queueSaveFile(PlayerComputer, MyComputerHandler, (HackerFile) Items.get(i));
                }

                PlayerComputer.addMessage("You have received files that you purchased using HackWars' online store. (" + basic + " x Attacking Data Shield, " + medium + " x Attacking Digital Fortress)");
                return (true);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return (false);
    }

    /**
     DigitalFortress
     RubyGuardian
     */
    public boolean givePackage2(Computer PlayerComputer, ComputerHandler MyComputerHandler) {
        try {
            ArrayList Items = new ArrayList();
            int basic = 2 + (int) (Math.random() * 2.0);
            int medium = 1 + (int) (Math.random() * 2.0);

            NewFireWall F = new NewFireWall();
            for (int i = 0; i < basic; i++) {
                Items.add(F.generateFirewall("DigitalFortress"));
            }

            for (int i = 0; i < medium; i++) {
                Items.add(F.generateFirewall("RubyGuardian"));
            }

            if (PlayerComputer.getFileSystem().getSpaceLeft() < Items.size()) {
                PlayerComputer.addMessage("You did not have enough HD space to receive the files you purchased from the HackWars online store. They will be given to you when you delete some files.");
                return (false);
            } else {
                for (int i = 0; i < Items.size(); i++) {
                    queueSaveFile(PlayerComputer, MyComputerHandler, (HackerFile) Items.get(i));
                }

                PlayerComputer.addMessage("You have received files that you purchased using HackWars' online store. (" + basic + " x Attacking Digital Fortress, " + medium + " x Attacking Ruby Guardian)");
                return (true);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return (false);
    }

    /**
     RubyGuardian
     DiamondDefender
     */
    public boolean givePackage3(Computer PlayerComputer, ComputerHandler MyComputerHandler) {
        try {
            ArrayList Items = new ArrayList();
            int basic = 2 + (int) (Math.random() * 2.0);
            int medium = 1 + (int) (Math.random() * 2.0);

            NewFireWall F = new NewFireWall();
            for (int i = 0; i < basic; i++) {
                Items.add(F.generateFirewall("RubyGuardian"));
            }

            for (int i = 0; i < medium; i++) {
                Items.add(F.generateFirewall("DiamondDefender"));
            }

            if (PlayerComputer.getFileSystem().getSpaceLeft() < Items.size()) {
                PlayerComputer.addMessage("You did not have enough HD space to receive the files you purchased from the HackWars online store. They will be given to you when you delete some files.");
                return (false);
            } else {
                for (int i = 0; i < Items.size(); i++) {
                    queueSaveFile(PlayerComputer, MyComputerHandler, (HackerFile) Items.get(i));
                }

                PlayerComputer.addMessage("You have received files that you purchased using HackWars' online store. (" + basic + " x Attacking Ruby Guardian, " + medium + " x Attacking Diamond Defender)");
                return (true);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return (false);
    }

    /**
     LOW
     MEDIUM
     */
    public boolean givePackage4(Computer PlayerComputer, ComputerHandler MyComputerHandler) {
        try {
            ArrayList Items = new ArrayList();
            int basic = 2 + (int) (Math.random() * 2.0);
            int medium = 1 + (int) (Math.random() * 2.0);

            NewFireWall F = new NewFireWall();
            for (int i = 0; i < basic; i++) {
                Items.add(GenerateHardwareKt.generateHardware(HardwareRarity.LOW));
            }

            for (int i = 0; i < medium; i++) {
                Items.add(GenerateHardwareKt.generateHardware(HardwareRarity.MEDIUM));
            }

            if (PlayerComputer.getFileSystem().getSpaceLeft() < Items.size()) {
                PlayerComputer.addMessage("You did not have enough HD space to receive the files you purchased from the HackWars online store. They will be given to you when you delete some files.");
                return (false);
            } else {
                for (int i = 0; i < Items.size(); i++) {
                    queueSaveFile(PlayerComputer, MyComputerHandler, (HackerFile) Items.get(i));
                }

                PlayerComputer.addMessage("You have received files that you purchased using HackWars' online store. (" + basic + " x Low Hardware Rolls, " + medium + " x Medium Hardware Rolls)");
                return (true);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return (false);
    }

    /**
     MEDIUM
     HIGH
     */
    public boolean givePackage5(Computer PlayerComputer, ComputerHandler MyComputerHandler) {
        try {
            ArrayList Items = new ArrayList();
            int basic = 2 + (int) (Math.random() * 2.0);
            int medium = 1 + (int) (Math.random() * 2.0);

            NewFireWall F = new NewFireWall();
            for (int i = 0; i < basic; i++) {
                Items.add(GenerateHardwareKt.generateHardware(HardwareRarity.MEDIUM));
            }

            for (int i = 0; i < medium; i++) {
                Items.add(GenerateHardwareKt.generateHardware(HardwareRarity.HIGH));
            }

            if (PlayerComputer.getFileSystem().getSpaceLeft() < Items.size()) {
                PlayerComputer.addMessage("You did not have enough HD space to receive the files you purchased from the HackWars online store. They will be given to you when you delete some files.");
                return (false);
            } else {
                for (int i = 0; i < Items.size(); i++) {
                    queueSaveFile(PlayerComputer, MyComputerHandler, (HackerFile) Items.get(i));
                }

                PlayerComputer.addMessage("You have received files that you purchased using HackWars' online store. (" + basic + " x Medium Hardware Rolls, " + medium + " x High Hardware Rolls)");
                return (true);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return (false);
    }


    /**
     HIGH
     RARE
     */
    public boolean givePackage6(Computer PlayerComputer, ComputerHandler MyComputerHandler) {
        try {
            ArrayList Items = new ArrayList();
            int basic = 2 + (int) (Math.random() * 2.0);
            int medium = 1 + (int) (Math.random() * 2.0);

            NewFireWall F = new NewFireWall();
            for (int i = 0; i < basic; i++) {
                Items.add(GenerateHardwareKt.generateHardware(HardwareRarity.HIGH));
            }

            for (int i = 0; i < medium; i++) {
                Items.add(GenerateHardwareKt.generateHardware(HardwareRarity.RARE));
            }

            if (PlayerComputer.getFileSystem().getSpaceLeft() < Items.size()) {
                PlayerComputer.addMessage("You did not have enough HD space to receive the files you purchased from the HackWars online store. They will be given to you when you delete some files.");
                return (false);
            } else {
                for (int i = 0; i < Items.size(); i++) {
                    queueSaveFile(PlayerComputer, MyComputerHandler, (HackerFile) Items.get(i));
                }

                PlayerComputer.addMessage("You have received files that you purchased using HackWars' online store. (" + basic + " x High Hardware Rolls, " + medium + " x Rare Hardware Rolls)");
                return (true);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return (false);
    }

    /**
     This method gives a file to a player if there is enough room on their HD,
     and they have purchased a file.

     1                               FW1             2-3 Basic Firewalls 1-2 Medium Firewalls
     2                               FW2             2-3 Medium Firewalls 1-2 Advanced Firewalls
     3                               FW3             2-3 Advanced Firewalls 1-2 Ultimate Firewalls
     4                               AGP1    2-3 1+ AGP cards
     5                               AGP2    2-3 AGP 1+ 2-3 APG 2+
     6                               AGP3    2-3 AGP 1+ 3-4 AGP 2+
     */
    public synchronized void giveFiles(Computer PlayerComputer, ComputerHandler MyComputerHandler) {
        try {
            ArrayList<Long> givenList = new ArrayList<Long>();//Purchases that have been successfully given.
            List<PendingPurchase> purchases = worldDataService().findPendingPurchasesByIp(PlayerComputer.getIP());

            for (PendingPurchase purchase : purchases) {
                String store_item_id = String.valueOf(purchase.getStoreItemId());

                if (store_item_id.equals("1")) {
                    if (givePackage1(PlayerComputer, MyComputerHandler)) {
                        givenList.add(purchase.getBoughtItemId());
                    }
                } else if (store_item_id.equals("2")) {
                    if (givePackage2(PlayerComputer, MyComputerHandler)) {
                        givenList.add(purchase.getBoughtItemId());
                    }
                } else if (store_item_id.equals("3")) {
                    if (givePackage3(PlayerComputer, MyComputerHandler)) {
                        givenList.add(purchase.getBoughtItemId());
                    }
                } else if (store_item_id.equals("4")) {
                    if (givePackage4(PlayerComputer, MyComputerHandler)) {
                        givenList.add(purchase.getBoughtItemId());
                    }
                } else if (store_item_id.equals("5")) {
                    if (givePackage5(PlayerComputer, MyComputerHandler)) {
                        givenList.add(purchase.getBoughtItemId());
                    }
                } else if (store_item_id.equals("6")) {
                    if (givePackage6(PlayerComputer, MyComputerHandler)) {
                        givenList.add(purchase.getBoughtItemId());
                    }
                }
            }

            worldDataService().markPurchasesGiven(givenList);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
