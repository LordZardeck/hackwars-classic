package game;

import assignments.PacketNetwork;
import assignments.PacketPort;
import com.hackwars.game.program.AttackProgram;
import com.hackwars.game.program.Program;
import com.hackwars.game.program.ShippingProgram;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Extracted legacy ApplicationData branch ladder for combat, network, bounty,
 * scan, and quest-related commands.
 */
public class LegacyCombatNetworkQuestCommands implements LegacyApplicationDataHandler {
    @Override
    public boolean dispatch(Computer computer, ApplicationData applicationData, int resolvedPort) {
        String function = applicationData.getFunction();

        if (function.equals("firewallxp")) {
            computer.healthChange = true;
            float mult = 1.0f;
            Float amount = (Float) applicationData.getParameters();
            if (amount > 110.0f) {
                amount = 110.0f;
            }
            amount *= mult;
            amount += (Float) computer.Stats.get("FireWall");
            if (amount < 300.0f) {
                amount = 300.0f;
            }
            computer.Stats.put("FireWall", amount);
            return true;
        } else if (function.equals("opponentupdate")) {
            Float[] values = (Float[]) (((Object[]) applicationData.getParameters())[0]);
            boolean targetWatch = (Boolean) (((Object[]) applicationData.getParameters())[1]);
            boolean damageFromFirewall = (Boolean) (((Object[]) applicationData.getParameters())[3]);
            boolean mining = (Boolean) (((Object[]) applicationData.getParameters())[4]);
            Float amount = values[0];
            Port port = (Port) computer.Ports.get(new Integer(resolvedPort));
            if (computer.connectionID != -1) {
                computer.Damage.add(new Object[]{getWindowHandle(port), new Float((float) amount), damageFromFirewall, mining});
            }
            computer.healthChange = true;

            if (port != null) {
                port.setTargetHP(values[1]);
                port.setTargetPettyCash(values[2]);
                port.setTargetCPUCost(values[3]);
                port.setTargetWatch(targetWatch);
            }
            return true;
        } else if (function.equals("attackxp")) {
            if (applicationData.getParameters() instanceof Object[]) {
                Float[] values = (Float[]) (((Object[]) applicationData.getParameters())[0]);
                boolean targetWatch = (Boolean) (((Object[]) applicationData.getParameters())[1]);
                Float amount = values[0];
                float mult = 1.0f;
                amount *= mult;
                amount += (Float) computer.Stats.get("Attack");
                if (amount < 300.0f && mult < 0) {
                    amount = 300.0f;
                }
                Port port = (Port) computer.Ports.get(new Integer(resolvedPort));
                if (((Object[]) applicationData.getParameters()).length == 4) {
                    if (computer.connectionID != -1) {
                        boolean firewall = (Boolean) (((Object[]) applicationData.getParameters())[2]);
                        boolean mining = (Boolean) (((Object[]) applicationData.getParameters())[3]);
                        computer.Damage.add(new Object[]{getWindowHandle(port), new Float((float) values[4]), firewall, mining});
                    }
                    if (port != null) {
                        port.setTargetHP(values[1]);
                        port.setTargetPettyCash(values[2]);
                        port.setTargetCPUCost(values[3]);
                        port.setTargetWatch(targetWatch);
                    }
                } else if (computer.connectionID != -1) {
                    boolean firewall = (Boolean) (((Object[]) applicationData.getParameters())[3]);
                    computer.Damage.add(new Object[]{getWindowHandle(port), new Float((float) values[4]), (String) (((Object[]) applicationData.getParameters())[2]), firewall, false});
                }

                if (computer.type != Computer.NPC) {
                    computer.Stats.put("Attack", amount);
                }
            } else {
                Float amount = (Float) applicationData.getParameters();
                float mult = 1.0f;
                amount *= mult;
                amount += (Float) computer.Stats.get("Attack");
                if (amount < 300.0f && mult < 0) {
                    amount = 300.0f;
                }
                if (computer.type != Computer.NPC) {
                    computer.Stats.put("Attack", amount);
                }
            }
            computer.healthChange = true;
            return true;
        } else if (function.equals("miningdamageupdate")) {
            if (applicationData.getParameters() instanceof Object[]) {
                Float[] values = (Float[]) (((Object[]) applicationData.getParameters())[0]);
                boolean targetWatch = (Boolean) (((Object[]) applicationData.getParameters())[1]);
                Float amount = values[4];
                Port port = (Port) computer.Ports.get(new Integer(resolvedPort));
                if (((Object[]) applicationData.getParameters()).length == 4) {
                    if (computer.connectionID != -1) {
                        boolean firewall = (Boolean) (((Object[]) applicationData.getParameters())[2]);
                        computer.Damage.add(new Object[]{getWindowHandle(port), amount, firewall, true});
                    }
                    if (port != null) {
                        port.setTargetHP(values[1]);
                        port.setTargetPettyCash(values[2]);
                        port.setTargetCPUCost(values[3]);
                        port.setTargetWatch(targetWatch);
                    }
                } else if (computer.connectionID != -1) {
                    boolean firewall = (Boolean) (((Object[]) applicationData.getParameters())[2]);
                    computer.Damage.add(new Object[]{getWindowHandle(port), amount, (String) (((Object[]) applicationData.getParameters())[2]), firewall, true});
                }
            }
            computer.healthChange = true;
            return true;
        } else if (function.equals("scan")) {
            float scanXP = (Float) computer.Stats.get("Scanning");
            int scanLevel = computer.getLevel(scanXP);
            float xp = 60.0f;

            if (computer.getCPULoad() > computer.getMaximumCPULoad()) {
                computer.addMessage(MessageHandler.SCAN_FAIL_OVERHEATED);
            } else if (!computer.checkBank()) {
                computer.addMessage(MessageHandler.ACTIVE_BANK_NOT_FOUND);
            } else if (computer.getPettyCash() < 10.0f) {
                computer.addMessage(MessageHandler.SCAN_FAIL_NO_MONEY);
            } else if (computer.getPettyCash() >= 10.0f) {
                int opponentFirewall = (Integer) ((Object[]) applicationData.getParameters())[0];
                PacketPort[] packetPorts = (PacketPort[]) ((Object[]) applicationData.getParameters())[1];
                boolean npc = (Boolean) ((Object[]) applicationData.getParameters())[6];

                if (!computer.isNPC()) {
                    computer.MyMakeBounty.checkBounty(computer, null, computer.MyMakeBounty.SCAN, applicationData.getSourceIP(), false, "");
                }

                for (int i = 0; i < packetPorts.length; i++) {
                    PacketPort packetPort = packetPorts[i];
                    if (packetPort != null) {
                        if (scanLevel - opponentFirewall < 25) {
                            xp = 40.0f;
                            packetPort.setDefault(-1);
                        } else {
                            packetPort.setDefault(0);
                            packetPort.setNote(applicationData.getSourceIP());

                            if (packetPort.getParamIndex() > 0) {
                                if (packetPort.getNumber() == (Integer) ((Object[]) applicationData.getParameters())[packetPort.getParamIndex()]) {
                                    packetPort.setDefault(1);
                                }
                            }
                        }

                        if (scanLevel - opponentFirewall < 15) {
                            xp = 20.0f;
                            packetPort.setFireWall(null);
                        }
                    }
                    computer.PA.setScannedPorts(packetPorts);
                }

                computer.getComputerHandler().addData(new ApplicationData("pettycash", new Float(-10.0f), 0, computer.ip), computer.ip);
                computer.getComputerHandler().addData(new ApplicationData("scanxp", new Float(xp), 0, computer.ip), computer.ip);
                computer.getComputerHandler().addData(new ApplicationData("scansuccess", null, 0, computer.ip), applicationData.getSourceIP());
            } else {
                computer.addMessage(MessageHandler.ACTIVE_BANK_NOT_FOUND);
            }
            computer.systemChange = true;
            return true;
        } else if (function.equals("scansuccess")) {
            return true;
        } else if (function.equals("requestscan")) {
            float firewallXP = (Float) computer.Stats.get("FireWall");
            int firewallLevel = computer.getLevel(firewallXP);

            String target = (String) applicationData.getParameters();
            PacketPort[] packetPorts = new PacketPort[computer.Ports.size()];

            Iterator portIterator = computer.Ports.entrySet().iterator();
            int ii = 0;
            while (portIterator.hasNext()) {
                Port tempPort = (Port) (((Map.Entry) portIterator.next()).getValue());
                if (tempPort.getOn()) {
                    packetPorts[ii] = tempPort.getPacketPort();
                }
                ii++;
            }
            Object[] payload = new Object[]{new Integer(firewallLevel), packetPorts, new Integer(computer.defaultBank), new Integer(computer.defaultAttack), new Integer(computer.defaultFTP), new Integer(computer.defaultHTTP), new Boolean(computer.type == Computer.NPC), new Integer(computer.defaultShipping)};
            computer.getComputerHandler().addData(new ApplicationData("scan", payload, 0, computer.ip), target);
            return true;
        } else if (function.equals("checkbounty")) {
            String fname = (String) applicationData.getParameters();
            HackerFile file = computer.MyFileSystem.getFile("Store/", fname);
            if (file != null) {
                HashMap content = file.getContent();
                float reward = new Float((String) content.get("reward"));
                computer.getComputerHandler().addData(new ApplicationData("pettycash", new Float(reward), 0, computer.ip), applicationData.getSourceIP());
                computer.getComputerHandler().addData(new ApplicationData("message", new Object[]{MessageHandler.BOUNTY_COMPLETED, new Object[]{NumberFormat.getCurrencyInstance().format(reward)}}, 0, computer.ip), applicationData.getSourceIP());
            } else {
                computer.getComputerHandler().addData(new ApplicationData("message", MessageHandler.BOUNTY_FAILED_ALREADY_COMPLETED, 0, computer.ip), applicationData.getSourceIP());
            }
            return true;
        } else if (function.equals("makebounty")) {
            Object[] params = (Object[]) applicationData.getParameters();
            boolean anonymous = (Boolean) params[0];
            String target = (String) params[1];
            int type = (Integer) params[2];
            String fileName = (String) params[3];
            String filePath = (String) params[4];
            int iterations = (Integer) params[5];
            float reward = (Float) params[6];

            if (!computer.checkBank()) {
                computer.addMessage(MessageHandler.ACTIVE_BANK_NOT_FOUND);
            } else if (computer.getPettyCash() >= reward) {
                HashMap content = new HashMap();
                content.put("count", "" + iterations);
                content.put("type", "" + type);
                content.put("reward", "" + reward);
                content.put("target", "" + target);
                content.put("bountyip", "" + computer.ip);
                content.put("timeout", "" + computer.getCurrentTime());

                HackerFile file = new HackerFile(HackerFile.BOUNTY);
                file.setQuantity(1);
                if (anonymous && type != computer.MyMakeBounty.CHANGE) {
                    file.setName(MakeBounty.getTypeName(type) + " By (Anonymous)");
                } else {
                    file.setName(MakeBounty.getTypeName(type) + " By (" + computer.ip + ")");
                }

                String description = "";
                description += "Bounty Type: " + MakeBounty.getTypeName(type) + "\n";
                if (!target.equals("*")) {
                    description += "Target: " + target + "\n";
                } else {
                    description += "Target: Any Player.\n";
                }
                description += "Reward: " + NumberFormat.getCurrencyInstance().format(reward) + "\n";

                if (type == computer.MyMakeBounty.INSTALL && fileName != null && !fileName.equals("")) {
                    HackerFile checkFile = computer.MyFileSystem.getFile(filePath, fileName);
                    description += "Must Install: " + checkFile.getName() + " Maker: " + checkFile.getMaker() + "\n";
                    content.put("maker", checkFile.getMaker());
                    content.put("script", checkFile.getName());
                } else {
                    content.put("maker", "");
                    content.put("script", "");
                }

                file.setDescription(description);
                file.setQuantity(-1);
                file.setContent(content);
                file.setLocation("Store/");
                Object[] save = new Object[]{"Store/", file};
                computer.getComputerHandler().addData(new ApplicationData("savefile", save, 0, computer.ip), computer.store);
                computer.getComputerHandler().addData(new ApplicationData("pettycash", new Float(-1.0f * reward), 0, computer.ip), computer.ip);
            } else {
                computer.addMessage(MessageHandler.SCAN_FAIL_NO_MONEY);
            }
            computer.systemChange = true;
            return true;
        } else if (function.equals("changenetwork")) {
            String changeNetwork = (String) applicationData.getParameters();

            if (computer.network.equals(changeNetwork)) {
                computer.addMessage(MessageHandler.CHANGE_NETWORK_FAIL_ALREADY_ON, new Object[]{computer.network});
            } else if (computer.network.equals(Network.JAIL_NETWORK)) {
                computer.addMessage(MessageHandler.CHANGE_NETWORK_FAIL_JAILED, new Object[]{computer.network});
            } else if (computer.MyTime.getCurrentTime() - computer.lastChangeNetwork < Computer.CHANGE_NETWORKS) {
                computer.addMessage(MessageHandler.CHANGE_NETWORK_FAIL_TIMEOUT, new Object[]{computer.network});
            } else {
                boolean allowed = false;
                if (changeNetwork.equals("UGOPNet")) {
                    allowed = true;
                } else {
                    for (int i = 0; i < computer.AllowedNetworks.size(); i++) {
                        String check = (String) computer.AllowedNetworks.get(i);
                        if (check.equals(changeNetwork)) {
                            allowed = true;
                            break;
                        }
                    }
                }

                if (allowed) {
                    computer.lastChangeNetwork = computer.MyTime.getCurrentTime();

                    Network.getInstance(computer.getComputerHandler()).removeFromNetwork(computer.network, computer.ip);
                    Network.getInstance(computer.getComputerHandler()).addToNetwork(changeNetwork, computer.ip);
                    PacketNetwork packetNetwork = Network.getInstance(computer.getComputerHandler()).getNetworkInformation(changeNetwork);
                    computer.store = packetNetwork.getStoreIP();
                    computer.PA.setPacketNetwork(packetNetwork);
                    computer.network = changeNetwork;
                    computer.addMessage(MessageHandler.CHANGE_NETWORK_SUCCESS, new Object[]{changeNetwork});
                } else {
                    computer.addMessage(Network.getInstance(computer.getComputerHandler()).switchNetwork(computer.network, changeNetwork, computer.ip));
                }
            }
            computer.systemChange = true;
            return true;
        } else if (function.equals("changenetwork2")) {
            String changeNetwork = (String) applicationData.getParameters();
            if (computer.network.equals(changeNetwork)) {
                computer.addMessage(MessageHandler.CHANGE_NETWORK_FAIL_ALREADY_ON, new Object[]{computer.network});
            } else {
                if (true) {
                    computer.lastChangeNetwork = computer.MyTime.getCurrentTime();

                    Network.getInstance(computer.getComputerHandler()).removeFromNetwork(computer.network, computer.ip);
                    Network.getInstance(computer.getComputerHandler()).addToNetwork(changeNetwork, computer.ip);
                    PacketNetwork packetNetwork = Network.getInstance(computer.getComputerHandler()).getNetworkInformation(changeNetwork);
                    computer.store = packetNetwork.getStoreIP();
                    computer.PA.setPacketNetwork(packetNetwork);
                    computer.network = changeNetwork;
                    computer.addMessage(MessageHandler.CHANGE_NETWORK_SUCCESS, new Object[]{changeNetwork});
                }
            }
            computer.systemChange = true;
            return true;
        } else if (function.equals("questinformation")) {
            HashMap getParameters = (HashMap) ((Object[]) applicationData.getParameters())[0];
            ArrayList interestedQuests = (ArrayList) ((Object[]) applicationData.getParameters())[1];
            ArrayList questItems = computer.MyFileSystem.getFilesOfType(HackerFile.QUEST_ITEM);
            if (questItems != null) {
                for (int ii = 0; ii < questItems.size(); ii++) {
                    HackerFile file = (HackerFile) questItems.get(ii);
                    HashMap content = file.getContent();
                    int qFileQuantity = file.getQuantity();

                    String itemName = (String) content.get("itemname");
                    getParameters.put(itemName, "true");
                    getParameters.put(itemName + "_quantity", qFileQuantity + "");
                }
            }

            for (int i = 0; i < interestedQuests.size(); i++) {
                HashMap tasks = null;
                if (computer.CurrentQuests.get(interestedQuests.get(i)) != null) {
                    tasks = (HashMap) ((Object[]) computer.CurrentQuests.get(interestedQuests.get(i)))[0];
                }

                if (tasks != null) {
                    Iterator taskIterator = tasks.entrySet().iterator();
                    while (taskIterator.hasNext()) {
                        Map.Entry currentEntry = (Map.Entry) taskIterator.next();
                        getParameters.put(currentEntry.getKey(), "" + ((Object[]) currentEntry.getValue())[0]);
                    }
                }
            }

            for (int i = 0; i < computer.CompletedQuests.size(); i++) {
                getParameters.put("quest" + ((Object[]) computer.CompletedQuests.get(i))[0], "true");
            }

            Iterator iterator1 = computer.CurrentQuests.entrySet().iterator();
            while (iterator1.hasNext()) {
                Map.Entry currentEntry = (Map.Entry) iterator1.next();
                Integer questID = (Integer) currentEntry.getKey();
                getParameters.put("quest" + questID, "false");
            }

            for (int i = 0; i < computer.commodityAmount.length; i++) {
                getParameters.put("commodity" + i, "" + computer.commodityAmount[i]);
            }

            getParameters.put("Attack", "" + computer.getAttackLevel());
            getParameters.put("Bank", "" + computer.getBankLevel());
            getParameters.put("Watch", "" + computer.getWatchLevel());
            getParameters.put("Scanning", "" + computer.getScanningLevel());
            getParameters.put("FireWall", "" + computer.getFireWallLevel());
            getParameters.put("HTTP", "" + computer.getHTTPLevel());
            getParameters.put("pettycash", "" + computer.getPettyCash());
            getParameters.put("Redirecting", "" + computer.getRedirectingLevel());
            getParameters.put("Repair", "" + computer.getRepairLevel());

            getParameters.put("defaultattack", "" + computer.getDefaultAttack());
            getParameters.put("defaultbank", "" + computer.getDefaultBank());
            getParameters.put("defaulthttp", "" + computer.getDefaultHTTP());
            getParameters.put("defaultredirecting", "" + computer.getDefaultShipping());
            getParameters.put("repaired", "" + computer.getRepaired());

            getParameters.put(computer.getNetwork(), "true");

            if (computer.pageBody.length() > 1) {
                getParameters.put("websitemade", "true");
            } else {
                getParameters.put("websitemade", "false");
            }

            if (computer.checkFirewall()) {
                getParameters.put("firewallinstalled", "true");
            } else {
                getParameters.put("firewallinstalled", "false");
            }

            if (computer.MyWatchHandler.getWatchCount() > 0) {
                getParameters.put("watchinstalled", "true");
            } else {
                getParameters.put("watchinstalled", "false");
            }

            computer.getComputerHandler().addData(new ApplicationData("requestwebpage", getParameters, 0, computer.ip), applicationData.getSourceIP());
            return true;
        } else if (function.equals("giveexperience")) {
            String stat = ((String) ((Object[]) applicationData.getParameters())[0]).toLowerCase();
            float xp = (Float) ((Object[]) applicationData.getParameters())[1];
            float amount = 0.0f;

            float mult = 1.0f;
            xp *= mult;

            if (stat.equals("bank")) {
                amount = (Float) computer.Stats.get("Bank");
                amount += xp;
                if (mult < 0) {
                    if (amount <= 300.0f) {
                        amount = 300.0f;
                    }
                }
                computer.Stats.put("Bank", amount);
            } else if (stat.equals("attack")) {
                amount = (Float) computer.Stats.get("Attack");
                amount += xp;
                if (mult < 0) {
                    if (amount <= 300.0f) {
                        amount = 300.0f;
                    }
                }
                computer.Stats.put("Attack", amount);
            } else if (stat.equals("scanning")) {
                amount = (Float) computer.Stats.get("Scanning");
                amount += xp;
                if (mult < 0) {
                    if (amount <= 300.0f) {
                        amount = 300.0f;
                    }
                }
                computer.Stats.put("Scanning", amount);
            } else if (stat.equals("watch")) {
                amount = (Float) computer.Stats.get("Watch");
                amount += xp;
                if (mult < 0) {
                    if (amount <= 300.0f) {
                        amount = 300.0f;
                    }
                }
                computer.Stats.put("Watch", amount);
            } else if (stat.equals("firewall")) {
                amount = (Float) computer.Stats.get("FireWall");
                amount += xp;
                if (mult < 0) {
                    if (amount <= 300.0f) {
                        amount = 300.0f;
                    }
                }
                computer.Stats.put("FireWall", amount);
            } else if (stat.equals("http")) {
                amount = (Float) computer.Stats.get("Webdesign");
                amount += xp;
                if (mult < 0) {
                    if (amount <= 300.0f) {
                        amount = 300.0f;
                    }
                }
                computer.Stats.put("Webdesign", amount);
            } else if (stat.equals("redirecting")) {
                amount = (Float) computer.Stats.get("Redirecting");
                amount += xp;
                if (mult < 0) {
                    if (amount <= 300.0f) {
                        amount = 300.0f;
                    }
                }
                computer.Stats.put("Redirecting", amount);
            }

            if (stat.equals("repair")) {
                amount = (Float) computer.Stats.get("Repair");
                amount += xp;
                if (mult < 0) {
                    if (amount <= 300.0f) {
                        amount = 300.0f;
                    }
                }
                computer.Stats.put("Repair", amount);
            }

            computer.healthChange = true;
            return true;
        } else if (function.equals("givetask")) {
            Object[] params = (Object[]) applicationData.getParameters();
            String taskName = (String) params[0];
            String taskLabel = (String) params[1];
            Integer questID = (Integer) params[2];

            if (!computer.checkQuest(questID)) {
                HashMap currentQuest = null;
                String label = "";
                if (computer.CurrentQuests.get(questID) != null) {
                    currentQuest = (HashMap) ((Object[]) computer.CurrentQuests.get(questID))[0];
                    label = (String) ((Object[]) computer.CurrentQuests.get(questID))[1];
                }

                if (currentQuest == null) {
                    currentQuest = new HashMap();
                    currentQuest.put(taskName, new Object[]{new Boolean(false), taskLabel});
                    computer.CurrentQuests.put(questID, new Object[]{currentQuest, label});
                } else {
                    currentQuest.put(taskName, new Object[]{new Boolean(false), taskLabel});
                }
            }
            return true;
        } else if (function.equals("settask")) {
            Object[] params = (Object[]) applicationData.getParameters();
            String taskName = (String) params[0];
            Integer questID = (Integer) params[1];
            Boolean setTo = (Boolean) params[2];

            if (!computer.checkQuest(questID)) {
                HashMap currentQuest = null;
                String label = "";
                if (computer.CurrentQuests.get(questID) != null) {
                    currentQuest = (HashMap) ((Object[]) computer.CurrentQuests.get(questID))[0];
                    label = (String) ((Object[]) computer.CurrentQuests.get(questID))[1];
                }

                if (currentQuest == null) {
                    currentQuest = new HashMap();
                    params = (Object[]) computer.CurrentQuests.get(taskName);
                    currentQuest.put(taskName, new Object[]{setTo, params[1]});
                    computer.CurrentQuests.put(questID, new Object[]{currentQuest, label});
                } else {
                    if (currentQuest.get(taskName) == null) {
                        currentQuest.put(taskName, new Object[]{setTo, taskName});
                    } else {
                        params = (Object[]) currentQuest.get(taskName);
                        currentQuest.put(taskName, new Object[]{setTo, params[1]});
                    }
                }
            }

            HashMap tempHashMap = new HashMap();
            tempHashMap.put("packetid", -1);
            computer.getComputerHandler().addData(new ApplicationData("requestwebpage", tempHashMap, 0, computer.ip), applicationData.getSourceIP());
            return true;
        } else if (function.equals("completetask")) {
            Object[] params = (Object[]) applicationData.getParameters();
            String taskName = (String) params[0];
            Integer questID = (Integer) params[1];
            Boolean setTo = (Boolean) true;

            if (!computer.checkQuest(questID)) {
                HashMap currentQuest = null;
                String label = "";
                if (computer.CurrentQuests.get(questID) != null) {
                    currentQuest = (HashMap) ((Object[]) computer.CurrentQuests.get(questID))[0];
                    label = (String) ((Object[]) computer.CurrentQuests.get(questID))[1];
                }

                if (currentQuest == null) {
                    currentQuest = new HashMap();
                    params = (Object[]) computer.CurrentQuests.get(taskName);
                    currentQuest.put(taskName, new Object[]{setTo, params[1]});
                    computer.CurrentQuests.put(questID, new Object[]{currentQuest, label});
                } else {
                    if (currentQuest.get(taskName) == null) {
                        currentQuest.put(taskName, new Object[]{setTo, taskName});
                    } else {
                        params = (Object[]) currentQuest.get(taskName);
                        currentQuest.put(taskName, new Object[]{setTo, params[1]});
                    }
                }
            }
            return true;
        } else if (function.equals("givecommodity")) {
            Object[] params = (Object[]) applicationData.getParameters();
            int commodityType = (Integer) params[0];
            float amount = (Float) params[1];
            computer.setCommodityAmount(commodityType, computer.getCommodity(commodityType) + amount);
            return true;
        } else if (function.equals("giveaccess")) {
            String accessNetwork = (String) applicationData.getParameters();
            computer.AllowedNetworks.add(accessNetwork);
            return true;
        } else if (function.equals("givefile")) {
            Object[] params = (Object[]) applicationData.getParameters();
            String fileID = (String) params[0];
            int quantity = (int) ((Integer) params[1]);

            if (computer.MyDropTable == null) {
                computer.MyDropTable = new DropTable(computer.dropTable, computer);
            }

            HackerFile file = computer.MyDropTable.getQuestItem(fileID);

            if (file != null) {
                file.setQuantity(quantity);
                Object[] payload = new Object[]{"", file};
                computer.getComputerHandler().addData(new ApplicationData("savefile", payload, 0, computer.ip), applicationData.getSourceIP());
                computer.getComputerHandler().addData(new ApplicationData("message", new Object[]{MessageHandler.GIVEN_FILE, new Object[]{file.getName(), quantity}}, 0, computer.ip), applicationData.getSourceIP());
            }
            return true;
        } else if (function.equals("takefile")) {
            String fileID = (String) applicationData.getParameters();
            ArrayList files = computer.MyFileSystem.getFiles();
            if (files != null) {
                for (int i = 0; i < files.size(); i++) {
                    HashMap content = ((HackerFile) files.get(i)).getContent();
                    String itemname = (String) content.get("itemname");
                    if (itemname.equals(fileID)) {
                        Object[] payload = new Object[]{"", ((HackerFile) files.get(i)).getName()};
                        computer.getComputerHandler().addData(new ApplicationData("deletefile", payload, 0, computer.ip), computer.ip);
                    }
                }
            }
            return true;
        } else if (function.equals("takefile2")) {
            Object[] params = (Object[]) applicationData.getParameters();
            String fileID = (String) params[0];
            int quantity = (int) ((Integer) params[1]);
            ArrayList files = computer.MyFileSystem.getFilesOfType(HackerFile.QUEST_ITEM);
            if (files != null) {
                for (int i = 0; i < files.size(); i++) {
                    HashMap content = ((HackerFile) files.get(i)).getContent();
                    String itemname = (String) content.get("itemname");
                    if (itemname.equals(fileID)) {
                        HackerFile file = (HackerFile) files.get(i);
                        int currentQuantity = file.getQuantity();
                        int newQuantity = currentQuantity - quantity;
                        if (newQuantity > 0) {
                            HackerFile fileCheck = computer.MyFileSystem.getFile("", file.getName());
                            saveFileTemp(computer, file, fileCheck, "", newQuantity);
                            computer.getComputerHandler().addData(new ApplicationData("message", new Object[]{MessageHandler.FILE_TAKEN, new Object[]{file.getName(), quantity}}, 0, computer.ip), computer.ip);
                        } else if (newQuantity == 0) {
                            Object[] payload = new Object[]{"", ((HackerFile) files.get(i)).getName()};
                            computer.getComputerHandler().addData(new ApplicationData("deletefile", payload, 0, computer.ip), computer.ip);
                        }
                    }
                }
            }
            return true;
        } else if (function.equals("finishquest")) {
            Object[] params = (Object[]) applicationData.getParameters();
            Integer questID = (Integer) params[0];
            params = (Object[]) computer.CurrentQuests.remove(questID);
            if (params != null) {
                computer.CompletedQuests.add(new Object[]{questID, params[1]});
                computer.addMessage(MessageHandler.QUEST_COMPLETED, new Object[]{params[1]});
            }
            return true;
        } else if (function.equals("givequest")) {
            Object[] params = (Object[]) applicationData.getParameters();
            Integer questID = (Integer) params[0];
            String description = (String) params[1];
            computer.CurrentQuests.put(questID, new Object[]{new HashMap(), description});
            computer.addMessage(MessageHandler.QUEST_GIVEN, new Object[]{description});
            return true;
        } else if (function.equals("takemoney")) {
            float amount = (Float) ((Object[]) applicationData.getParameters())[0];
            if (computer.getPettyCash() >= amount) {
                computer.setPettyCash(computer.getPettyCash() - amount);
            }
            return true;
        } else if (function.equals("takecommodity")) {
            float amount = (Float) ((Object[]) applicationData.getParameters())[0];
            int commodityType = (Integer) ((Object[]) applicationData.getParameters())[1];

            if (computer.getCommodity(commodityType) >= amount) {
                computer.setCommodityAmount(commodityType, computer.getCommodity(commodityType) - amount);
            }
            return true;
        } else if (function.equals("exchangecommodity")) {
            Integer exchangeAmount = (Integer) ((Object[]) applicationData.getParameters())[0];
            Integer commodityType = (Integer) ((Object[]) applicationData.getParameters())[1];
            float exchangeCost = (Float) ((Object[]) applicationData.getParameters())[2];

            boolean failed = false;
            String name = "DuctTape";
            if (computer.checkBank() && computer.getPettyCash() >= exchangeCost && computer.MyFileSystem.getSpaceLeft() > 0) {
                if (computer.getCommodity(commodityType) >= exchangeAmount) {
                    computer.setPettyCash(computer.getPettyCash() - exchangeCost);
                    computer.setCommodityAmount(commodityType, computer.getCommodity(commodityType) - exchangeAmount);
                    HackerFile newHackerFile = new HackerFile(HackerFile.COMMODITY_SLIP);
                    newHackerFile.setDescription("This slip is can be exchanged for the given commodity.");
                    newHackerFile.setMaker("Ming");
                    newHackerFile.setQuantity(exchangeAmount);
                    HashMap attributes = new HashMap();
                    attributes.put("data", "" + commodityType);
                    attributes.put("level", "0");
                    newHackerFile.setContent(attributes);

                    if (commodityType == 0) {
                        name = "DuctTape.commodity";
                    } else if (commodityType == 1) {
                        name = "Germanium.commodity";
                    } else if (commodityType == 2) {
                        name = "Silicon.commodity";
                    } else if (commodityType == 3) {
                        name = "YBCO.commodity";
                    } else if (commodityType == 4) {
                        name = "Plutonium.commodity";
                    }
                    newHackerFile.setName(name);
                    Object[] parameter = new Object[]{"", newHackerFile};
                    computer.getComputerHandler().addData(new ApplicationData("savefile", parameter, 0, computer.ip), computer.ip);
                } else {
                    failed = true;
                    computer.addMessage(MessageHandler.EXCHANGE_COMMODITY_FAIL_NOT_ENOUGH_COMMODITY, new Object[]{name});
                }
            } else if (!computer.checkBank()) {
                failed = true;
                computer.addMessage(MessageHandler.EXCHANGE_COMMODITY_FAIL_NO_BANKING_PORT);
            } else if (computer.getPettyCash() <= exchangeCost) {
                failed = true;
                computer.addMessage(MessageHandler.EXCHANGE_COMMODITY_FAIL_NOT_ENOUGH_MONEY);
            } else if (computer.MyFileSystem.getSpaceLeft() <= 0) {
                failed = true;
                computer.addMessage(MessageHandler.EXCHANGE_COMMODITY_FAIL_HD_FULL);
            }

            if (!failed) {
                computer.addMessage(MessageHandler.EXCHANGE_COMMODITY_SUCCESS, new Object[]{exchangeAmount, name});
            }

            computer.systemChange = true;
            return true;
        } else if (function.equals("exchangefile")) {
            Integer exchangeAmount = (Integer) ((Object[]) applicationData.getParameters())[0];
            Integer commodityType = (Integer) ((Object[]) applicationData.getParameters())[1];
            float exchangeCost = (Float) ((Object[]) applicationData.getParameters())[2];

            boolean failed = false;

            HackerFile tempFile = null;
            if (computer.checkBank() && computer.getPettyCash() >= exchangeCost) {
                ArrayList commodityFiles = computer.MyFileSystem.getFilesOfType(HackerFile.COMMODITY_SLIP);
                int fileAmount = 0;
                if (commodityFiles != null) {
                    for (int i = 0; i < commodityFiles.size(); i++) {
                        tempFile = (HackerFile) commodityFiles.get(i);
                        int type = new Integer((String) tempFile.getContent().get("data"));
                        if (type == commodityType) {
                            if (tempFile.getQuantity() >= exchangeAmount) {
                                computer.setPettyCash(computer.getPettyCash() - exchangeCost);
                                computer.setCommodityAmount(commodityType, computer.getCommodity(commodityType) + exchangeAmount);
                                tempFile.setQuantity(tempFile.getQuantity() - exchangeAmount);
                                if (tempFile.getQuantity() == 0) {
                                    String path = "";
                                    String name = tempFile.getName();
                                    computer.MyFileSystem.deleteFile(path, name);
                                    computer.PA.setRequestPrimary(true, 1);
                                }
                            } else {
                                failed = true;
                            }
                            break;
                        }

                        if (i == commodityFiles.size() - 1) {
                            failed = true;
                        }
                    }
                }
            } else if (!computer.checkBank()) {
                failed = true;
                computer.addMessage(MessageHandler.EXCHANGE_COMMODITY_FAIL_NO_BANKING_PORT);
            } else if (computer.getPettyCash() <= exchangeCost) {
                failed = true;
                computer.addMessage(MessageHandler.EXCHANGE_COMMODITY_FAIL_NOT_ENOUGH_MONEY);
            }

            if (!failed) {
                computer.addMessage(MessageHandler.EXCHANGE_COMMODITY_SUCCESS_FROM_FILE, new Object[]{exchangeAmount, tempFile.getName()});
            }

            computer.systemChange = true;
            return true;
        }

        return false;
    }

    private int getWindowHandle(Port port) {
        int windowHandle = 0;
        if (port != null) {
            Program program = port.getProgram();
            if (program instanceof AttackProgram) {
                windowHandle = ((AttackProgram) program).getWindowHandle();
            } else if (program instanceof ShippingProgram) {
                windowHandle = ((ShippingProgram) program).getWindowHandle();
            }
        }
        return windowHandle;
    }

    private void saveFileTemp(Computer computer, HackerFile file, HackerFile existing, String path, int newQuantity) {
        file = computer.checkRename(file, path);

        int quantity = file.getQuantity();
        if (quantity == 0) {
            quantity = 1;
        }

        if (existing != null) {
            if (existing.getQuantity() == -1) {
                quantity = -1;
            } else if (existing.isStacking() && file.getName().equals(existing.getName())) {
                quantity = existing.getQuantity() + file.getQuantity();
            }
        }
        file.setQuantity(newQuantity);

        if (!computer.MyFileSystem.addFile(file, true)) {
            computer.addMessage(MessageHandler.HD_FULL);
        }

        if (path.equals("Public/") || path.equals("Store/")) {
            computer.PA.setRequestSecondary(true, 8);
            computer.PA.setRequestPrimary(true, 8);
        } else {
            computer.PA.setRequestPrimary(true, 1);
        }
        computer.systemChange = true;
    }
}
