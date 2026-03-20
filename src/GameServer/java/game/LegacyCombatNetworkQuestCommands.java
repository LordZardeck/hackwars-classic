package game;

import assignments.PacketNetwork;
import assignments.PacketPort;
import com.hackwars.rpc.DeleteFile;
import game.payload.*;

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
        String function = applicationData.getCommand().wireName();

        switch (function) {
            case "firewallxp":
                applyFirewallXp(computer, applicationData.requirePayload(CombatFirewallXpPayload.class));
                return true;
            case "opponentupdate":
                applyOpponentUpdate(computer, resolvedPort, applicationData.requirePayload(CombatOpponentUpdatePayload.class));
                return true;
            case "attackxp":
                if (CombatQuestSupport.isAttackXpAwardPayload(applicationData.getPayload())) {
                    applyAttackXpAward(computer, CombatQuestSupport.attackXpAwardAmount(applicationData.getPayload()));
                } else {
                    applyAttackXpUpdate(computer, resolvedPort, applicationData.requirePayload(CombatAttackXpUpdatePayload.class));
                }
                return true;
            case "miningdamageupdate":
                applyMiningDamageUpdate(computer, resolvedPort, applicationData.requirePayload(CombatMiningDamageUpdatePayload.class));
                return true;
            case "scan":
                applyScan(computer, applicationData, resolvedPort, applicationData.requirePayload(CombatScanPayload.class));
                return true;
            case "scansuccess":
                applicationData.requirePayload(CombatScanSuccessPayload.class);
                return true;
            case "requestscan":
                applyRequestScan(computer, applicationData, applicationData.requirePayload(CombatRequestScanPayload.class));
                return true;
            case "checkbounty":
                applyCheckBounty(computer, applicationData, applicationData.requirePayload(CombatCheckBountyPayload.class));
                return true;
            case "makebounty":
                applyMakeBounty(computer, applicationData, applicationData.requirePayload(CombatMakeBountyPayload.class));
                return true;
            case "changenetwork":
                applyChangeNetwork(computer, applicationData, applicationData.requirePayload(CombatChangeNetworkPayload.class));
                return true;
            case "changenetwork2":
                applyChangeNetwork2(computer, applicationData, applicationData.requirePayload(CombatChangeNetwork2Payload.class));
                return true;
            case "questinformation":
                applyQuestInformation(computer, applicationData, applicationData.requirePayload(CombatQuestInformationPayload.class));
                return true;
            case "giveexperience":
                applyGiveExperience(computer, applicationData.requirePayload(CombatGiveExperiencePayload.class));
                return true;
            case "givetask":
                CombatGiveTaskPayload giveTask = applicationData.requirePayload(CombatGiveTaskPayload.class);
                CombatQuestSupport.addTask(computer, giveTask.getQuestId(), giveTask.getTaskName(), giveTask.getTaskLabel());
                return true;
            case "settask":
                CombatSetTaskPayload setTask = applicationData.requirePayload(CombatSetTaskPayload.class);
                CombatQuestSupport.setTask(computer, setTask.getQuestId(), setTask.getTaskName(), setTask.getSetTo());
                queueQuestRefresh(computer, applicationData.getSourceIP());
                return true;
            case "completetask":
                CombatCompleteTaskPayload completeTask = applicationData.requirePayload(CombatCompleteTaskPayload.class);
                CombatQuestSupport.completeTask(computer, completeTask.getQuestId(), completeTask.getTaskName());
                return true;
            case "givecommodity":
                CombatGiveCommodityPayload giveCommodity = applicationData.requirePayload(CombatGiveCommodityPayload.class);
                computer.setCommodityAmount(giveCommodity.getCommodityType(), computer.getCommodity(giveCommodity.getCommodityType()) + giveCommodity.getAmount());
                return true;
            case "giveaccess":
                computer.AllowedNetworks.add(applicationData.requirePayload(CombatGiveAccessPayload.class).getAccessNetwork());
                return true;
            case "givefile":
                applyGiveFile(computer, applicationData, applicationData.requirePayload(CombatGiveFilePayload.class));
                return true;
            case "takefile":
                applyTakeFile(computer, applicationData.requirePayload(CombatTakeFilePayload.class));
                return true;
            case "takefile2":
                applyTakeFile2(computer, applicationData, applicationData.requirePayload(CombatTakeFile2Payload.class));
                return true;
            case "finishquest":
                applyFinishQuest(computer, applicationData.requirePayload(CombatFinishQuestPayload.class));
                return true;
            case "givequest":
                applyGiveQuest(computer, applicationData.requirePayload(CombatGiveQuestPayload.class));
                return true;
            case "takemoney":
                applyTakeMoney(computer, applicationData.requirePayload(CombatTakeMoneyPayload.class));
                return true;
            case "takecommodity":
                applyTakeCommodity(computer, applicationData.requirePayload(CombatTakeCommodityPayload.class));
                return true;
            case "exchangecommodity":
                applyExchangeCommodity(computer, applicationData, applicationData.requirePayload(CombatExchangeCommodityPayload.class));
                return true;
            case "exchangefile":
                applyExchangeFile(computer, applicationData, applicationData.requirePayload(CombatExchangeFilePayload.class));
                return true;
            default:
                return false;
        }
    }

    private void applyFirewallXp(Computer computer, CombatFirewallXpPayload payload) {
        computer.healthChange = true;
        float amount = payload.getAmount();
        if (amount > 110.0f) {
            amount = 110.0f;
        }
        amount += (Float) computer.Stats.get("FireWall");
        if (amount < 300.0f) {
            amount = 300.0f;
        }
        computer.Stats.put("FireWall", amount);
    }

    private void applyOpponentUpdate(Computer computer, int resolvedPort, CombatOpponentUpdatePayload payload) {
        CombatDamageValues values = payload.getValues();
        Port port = (Port) computer.Ports.get(resolvedPort);
        if (computer.connectionID != -1) {
            CombatQuestSupport.addDamage(
                computer,
                CombatQuestSupport.windowHandle(port),
                values.getDamage(),
                payload.getDamageFromFirewall(),
                payload.getMining()
            );
        }
        computer.healthChange = true;
        if (port != null) {
            port.setTargetHP(values.getHealth());
            port.setTargetPettyCash(values.getPettyCash());
            port.setTargetCPUCost(values.getCpuCost());
            port.setTargetWatch(payload.getTargetWatch());
        }
    }

    private void applyAttackXpAward(Computer computer, float amount) {
        float updated = amount + (Float) computer.Stats.get("Attack");
        if (updated < 300.0f) {
            updated = 300.0f;
        }
        if (computer.type != Computer.NPC) {
            computer.Stats.put("Attack", updated);
        }
        computer.healthChange = true;
    }

    private void applyAttackXpUpdate(Computer computer, int resolvedPort, CombatAttackXpUpdatePayload payload) {
        CombatDamageValues values = payload.getValues();
        Port port = (Port) computer.Ports.get(resolvedPort);
        if (payload.getTargetIp() == null) {
            if (computer.connectionID != -1) {
                CombatQuestSupport.addDamage(
                    computer,
                    CombatQuestSupport.windowHandle(port),
                    values.getDamage(),
                    payload.getDamageFromFirewall(),
                    payload.getMining()
                );
            }
            if (port != null) {
                port.setTargetHP(values.getHealth());
                port.setTargetPettyCash(values.getPettyCash());
                port.setTargetCPUCost(values.getCpuCost());
                port.setTargetWatch(payload.getTargetWatch());
            }
        } else if (computer.connectionID != -1) {
            CombatQuestSupport.addDamage(
                computer,
                CombatQuestSupport.windowHandle(port),
                values.getDamage(),
                payload.getTargetIp(),
                payload.getDamageFromFirewall(),
                false
            );
        }

        if (computer.type != Computer.NPC) {
            float updated = values.getXp() + (Float) computer.Stats.get("Attack");
            if (updated < 300.0f) {
                updated = 300.0f;
            }
            computer.Stats.put("Attack", updated);
        }
        computer.healthChange = true;
    }

    private void applyMiningDamageUpdate(Computer computer, int resolvedPort, CombatMiningDamageUpdatePayload payload) {
        CombatDamageValues values = payload.getValues();
        Port port = (Port) computer.Ports.get(resolvedPort);
        if (payload.getTargetIp() == null) {
            if (computer.connectionID != -1) {
                CombatQuestSupport.addDamage(
                    computer,
                    CombatQuestSupport.windowHandle(port),
                    values.getDamage(),
                    payload.getDamageFromFirewall(),
                    true
                );
            }
            if (port != null) {
                port.setTargetHP(values.getHealth());
                port.setTargetPettyCash(values.getPettyCash());
                port.setTargetCPUCost(values.getCpuCost());
                port.setTargetWatch(payload.getTargetWatch());
            }
        } else if (computer.connectionID != -1) {
            CombatQuestSupport.addDamage(
                computer,
                CombatQuestSupport.windowHandle(port),
                values.getDamage(),
                payload.getTargetIp(),
                payload.getDamageFromFirewall(),
                true
            );
        }
        computer.healthChange = true;
    }

    private void applyScan(Computer computer, ApplicationData applicationData, int resolvedPort, CombatScanPayload payload) {
        float scanXP = (Float) computer.Stats.get("Scanning");
        int scanLevel = computer.getLevel(scanXP);
        float xp = 60.0f;

        if (computer.getCPULoad() > computer.getMaximumCPULoad()) {
            computer.addMessage(MessageHandler.SCAN_FAIL_OVERHEATED);
        } else if (!computer.checkBank()) {
            computer.addMessage(MessageHandler.ACTIVE_BANK_NOT_FOUND);
        } else if (computer.getPettyCash() < 10.0f) {
            computer.addMessage(MessageHandler.SCAN_FAIL_NO_MONEY);
        } else {
            int opponentFirewall = payload.getOpponentFirewall();
            PacketPort[] packetPorts = payload.getPacketPorts();

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
                            if (packetPort.getNumber() == packetValueForPort(payload, packetPort.getParamIndex())) {
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

            computer.getComputerHandler().addData(new ApplicationData(new PettyCashDeltaPayload(-10.0f), 0, computer.ip), computer.ip);
            computer.getComputerHandler().addData(new ApplicationData(new CombatScanXpPayload(xp), 0, computer.ip), computer.ip);
            computer.getComputerHandler().addData(new ApplicationData(CombatScanSuccessPayload.INSTANCE, 0, computer.ip), applicationData.getSourceIP());
        }
        computer.systemChange = true;
    }

    private int packetValueForPort(CombatScanPayload payload, int index) {
        switch (index) {
            case 2:
                return payload.getDefaultBank();
            case 3:
                return payload.getDefaultAttack();
            case 4:
                return payload.getDefaultFtp();
            case 5:
                return payload.getDefaultHttp();
            case 7:
                return payload.getDefaultShipping();
            default:
                return -1;
        }
    }

    private void applyRequestScan(Computer computer, ApplicationData applicationData, CombatRequestScanPayload payload) {
        float firewallXP = (Float) computer.Stats.get("FireWall");
        int firewallLevel = computer.getLevel(firewallXP);

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

        CombatScanPayload scanPayload = new CombatScanPayload(
            firewallLevel,
            packetPorts,
            computer.defaultBank,
            computer.defaultAttack,
            computer.defaultFTP,
            computer.defaultHTTP,
            computer.type == Computer.NPC,
            computer.defaultShipping
        );
        computer.getComputerHandler().addData(new ApplicationData(scanPayload, 0, computer.ip), payload.getTargetIp());
    }

    private void applyCheckBounty(Computer computer, ApplicationData applicationData, CombatCheckBountyPayload payload) {
        HackerFile file = computer.MyFileSystem.getFile("Store/", payload.getFileName());
        if (file != null) {
            HashMap content = file.getContent();
            float reward = Float.parseFloat((String) content.get("reward"));
            computer.getComputerHandler().addData(new ApplicationData(new PettyCashDeltaPayload(reward), 0, computer.ip), applicationData.getSourceIP());
            computer.getComputerHandler().addData(
                    new ApplicationData(
                        new StructuredMessagePayload(
                            new Object[]{MessageHandler.BOUNTY_COMPLETED, new Object[]{NumberFormat.getCurrencyInstance().format(reward)}},
                            null,
                            null
                        ),
                    0,
                    computer.ip
                ),
                applicationData.getSourceIP()
            );
        } else {
            computer.getComputerHandler().addData(
                new ApplicationData(new StructuredMessagePayload(MessageHandler.BOUNTY_FAILED_ALREADY_COMPLETED, null, null), 0, computer.ip),
                applicationData.getSourceIP()
            );
        }
    }

    private void applyMakeBounty(Computer computer, ApplicationData applicationData, CombatMakeBountyPayload payload) {
        if (!computer.checkBank()) {
            computer.addMessage(MessageHandler.ACTIVE_BANK_NOT_FOUND);
            return;
        }

        float reward = payload.getReward();

        if (computer.getPettyCash() >= reward) {
            boolean anonymous = payload.getAnonymous();
            int type = payload.getType();
            String target = payload.getTarget();
            String fileName = payload.getFileName();
            String filePath = payload.getFilePath();
            int iterations = payload.getIterations();

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

            String description = "Bounty Type: " + MakeBounty.getTypeName(type) + "\n";
            if (!target.equals("*")) {
                description += "Target: " + target + "\n";
            } else {
                description += "Target: Any Player.\n";
            }
            description += "Reward: " + NumberFormat.getCurrencyInstance().format(reward) + "\n";

            if (type == computer.MyMakeBounty.INSTALL && fileName != null && !fileName.equals("")) {
                HackerFile checkFile = computer.MyFileSystem.getFile(filePath, fileName);
                if (checkFile != null) {
                    description += "Must Install: " + checkFile.getName() + " Maker: " + checkFile.getMaker() + "\n";
                    content.put("maker", checkFile.getMaker());
                    content.put("script", checkFile.getName());
                }
            } else {
                content.put("maker", "");
                content.put("script", "");
            }

            file.setDescription(description);
            file.setQuantity(-1);
            file.setContent(content);
            file.setLocation("Store/");

            computer.getComputerHandler().addData(new ApplicationData(new SaveFileRequestPayload("Store/", file), 0, computer.ip), computer.store);
            computer.getComputerHandler().addData(new ApplicationData(new PettyCashDeltaPayload(-1.0f * reward), 0, computer.ip), computer.ip);
        } else {
            computer.addMessage(MessageHandler.SCAN_FAIL_NO_MONEY);
        }
        computer.systemChange = true;
    }

    private void applyChangeNetwork(Computer computer, ApplicationData applicationData, CombatChangeNetworkPayload payload) {
        String changeNetwork = payload.getNetwork();

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
    }

    private void applyChangeNetwork2(Computer computer, ApplicationData applicationData, CombatChangeNetwork2Payload payload) {
        String changeNetwork = payload.getNetwork();
        if (computer.network.equals(changeNetwork)) {
            computer.addMessage(MessageHandler.CHANGE_NETWORK_FAIL_ALREADY_ON, new Object[]{computer.network});
        } else {
            computer.lastChangeNetwork = computer.MyTime.getCurrentTime();

            Network.getInstance(computer.getComputerHandler()).removeFromNetwork(computer.network, computer.ip);
            Network.getInstance(computer.getComputerHandler()).addToNetwork(changeNetwork, computer.ip);
            PacketNetwork packetNetwork = Network.getInstance(computer.getComputerHandler()).getNetworkInformation(changeNetwork);
            computer.store = packetNetwork.getStoreIP();
            computer.PA.setPacketNetwork(packetNetwork);
            computer.network = changeNetwork;
            computer.addMessage(MessageHandler.CHANGE_NETWORK_SUCCESS, new Object[]{changeNetwork});
        }
        computer.systemChange = true;
    }

    private void applyQuestInformation(Computer computer, ApplicationData applicationData, CombatQuestInformationPayload payload) {
        HashMap parameters = payload.getQuestParameters();
        HashMap response = CombatQuestSupport.buildQuestInformation(computer, parameters, payload.getInterestedQuests());
        computer.getComputerHandler().addData(new ApplicationData(new RequestWebPagePayload(response), 0, computer.ip), applicationData.getSourceIP());
    }

    private void applyGiveExperience(Computer computer, CombatGiveExperiencePayload payload) {
        String stat = payload.getStat().toLowerCase();
        float xp = payload.getAmount();
        float amount = 0.0f;

        switch (stat) {
            case "bank":
                amount = (Float) computer.Stats.get("Bank") + xp;
                if (amount < 300.0f && xp < 0) {
                    amount = 300.0f;
                }
                computer.Stats.put("Bank", amount);
                break;
            case "attack":
                amount = (Float) computer.Stats.get("Attack") + xp;
                if (amount < 300.0f && xp < 0) {
                    amount = 300.0f;
                }
                computer.Stats.put("Attack", amount);
                break;
            case "scanning":
                amount = (Float) computer.Stats.get("Scanning") + xp;
                if (amount < 300.0f && xp < 0) {
                    amount = 300.0f;
                }
                computer.Stats.put("Scanning", amount);
                break;
            case "watch":
                amount = (Float) computer.Stats.get("Watch") + xp;
                if (amount < 300.0f && xp < 0) {
                    amount = 300.0f;
                }
                computer.Stats.put("Watch", amount);
                break;
            case "firewall":
                amount = (Float) computer.Stats.get("FireWall") + xp;
                if (amount < 300.0f && xp < 0) {
                    amount = 300.0f;
                }
                computer.Stats.put("FireWall", amount);
                break;
            case "http":
                amount = (Float) computer.Stats.get("Webdesign") + xp;
                if (amount < 300.0f && xp < 0) {
                    amount = 300.0f;
                }
                computer.Stats.put("Webdesign", amount);
                break;
            case "redirecting":
                amount = (Float) computer.Stats.get("Redirecting") + xp;
                if (amount < 300.0f && xp < 0) {
                    amount = 300.0f;
                }
                computer.Stats.put("Redirecting", amount);
                break;
            case "repair":
                amount = (Float) computer.Stats.get("Repair") + xp;
                if (amount < 300.0f && xp < 0) {
                    amount = 300.0f;
                }
                computer.Stats.put("Repair", amount);
                break;
            default:
                break;
        }

        computer.healthChange = true;
    }

    private void applyGiveFile(Computer computer, ApplicationData applicationData, CombatGiveFilePayload payload) {
        if (computer.MyDropTable == null) {
            computer.MyDropTable = new DropTable(computer.dropTable, computer);
        }

        HackerFile file = computer.MyDropTable.getQuestItem(payload.getFileId());
        if (file != null) {
            file.setQuantity(payload.getQuantity());
            computer.getComputerHandler().addData(new ApplicationData(new SaveFileRequestPayload("", file), 0, computer.ip), applicationData.getSourceIP());
            computer.getComputerHandler().addData(
                    new ApplicationData(
                        new StructuredMessagePayload(
                            new Object[]{MessageHandler.GIVEN_FILE, new Object[]{file.getName(), payload.getQuantity()}},
                            null,
                            null
                        ),
                    0,
                    computer.ip
                ),
                applicationData.getSourceIP()
            );
        }
    }

    private void applyTakeFile(Computer computer, CombatTakeFilePayload payload) {
        ArrayList files = computer.MyFileSystem.getFiles();
        if (files != null) {
            for (int i = 0; i < files.size(); i++) {
                HashMap content = ((HackerFile) files.get(i)).getContent();
                String itemname = (String) content.get("itemname");
                if (itemname.equals(payload.getFileId())) {
                    computer.getComputerHandler().addData(new ApplicationData(new DeleteFile(computer.ip, "", ((HackerFile) files.get(i)).getName()), 0, computer.ip), computer.ip);
                }
            }
        }
    }

    private void applyTakeFile2(Computer computer, ApplicationData applicationData, CombatTakeFile2Payload payload) {
        ArrayList files = computer.MyFileSystem.getFilesOfType(HackerFile.QUEST_ITEM);
        if (files != null) {
            for (int i = 0; i < files.size(); i++) {
                HackerFile file = (HackerFile) files.get(i);
                HashMap content = file.getContent();
                String itemname = (String) content.get("itemname");
                if (itemname.equals(payload.getFileId())) {
                    int currentQuantity = file.getQuantity();
                    int newQuantity = currentQuantity - payload.getQuantity();
                    if (newQuantity > 0) {
                        HackerFile fileCheck = computer.MyFileSystem.getFile("", file.getName());
                        saveFileTemp(computer, file, fileCheck, "", newQuantity);
                        computer.getComputerHandler().addData(
                            new ApplicationData(
                                new StructuredMessagePayload(
                                    new Object[]{MessageHandler.FILE_TAKEN, new Object[]{file.getName(), payload.getQuantity()}},
                                    null,
                                    null
                                ),
                                0,
                                computer.ip
                            ),
                            computer.ip
                        );
                    } else if (newQuantity == 0) {
                        computer.getComputerHandler().addData(new ApplicationData(new DeleteFile(computer.ip, "", file.getName()), 0, computer.ip), computer.ip);
                    }
                }
            }
        }
    }

    private void applyFinishQuest(Computer computer, CombatFinishQuestPayload payload) {
        CombatQuestState state = CombatQuestSupport.currentQuest(computer, payload.getQuestId());
        if (state != null) {
            CombatQuestSupport.finishQuest(computer, payload.getQuestId());
            computer.addMessage(MessageHandler.QUEST_COMPLETED, new Object[]{state.getLabel()});
        }
    }

    private void applyGiveQuest(Computer computer, CombatGiveQuestPayload payload) {
        CombatQuestSupport.putCurrentQuest(computer, payload.getQuestId(), new CombatQuestState(new HashMap(), payload.getDescription()));
        computer.addMessage(MessageHandler.QUEST_GIVEN, new Object[]{payload.getDescription()});
    }

    private void applyTakeMoney(Computer computer, CombatTakeMoneyPayload payload) {
        float amount = payload.getAmount();
        if (computer.getPettyCash() >= amount) {
            computer.setPettyCash(computer.getPettyCash() - amount);
        }
    }

    private void applyTakeCommodity(Computer computer, CombatTakeCommodityPayload payload) {
        float amount = payload.getAmount();
        int commodityType = payload.getCommodityType();
        if (computer.getCommodity(commodityType) >= amount) {
            computer.setCommodityAmount(commodityType, computer.getCommodity(commodityType) - amount);
        }
    }

    private void applyExchangeCommodity(Computer computer, ApplicationData applicationData, CombatExchangeCommodityPayload payload) {
        boolean failed = false;
        String name = "DuctTape";
        if (computer.checkBank() && computer.getPettyCash() >= payload.getExchangeCost() && computer.MyFileSystem.getSpaceLeft() > 0) {
            if (computer.getCommodity(payload.getCommodityType()) >= payload.getExchangeAmount()) {
                computer.setPettyCash(computer.getPettyCash() - payload.getExchangeCost());
                computer.setCommodityAmount(payload.getCommodityType(), computer.getCommodity(payload.getCommodityType()) - payload.getExchangeAmount());
                HackerFile newHackerFile = new HackerFile(HackerFile.COMMODITY_SLIP);
                newHackerFile.setDescription("This slip is can be exchanged for the given commodity.");
                newHackerFile.setMaker("Ming");
                newHackerFile.setQuantity(payload.getExchangeAmount());
                HashMap attributes = new HashMap();
                attributes.put("data", "" + payload.getCommodityType());
                attributes.put("level", "0");
                newHackerFile.setContent(attributes);

                if (payload.getCommodityType() == 0) {
                    name = "DuctTape.commodity";
                } else if (payload.getCommodityType() == 1) {
                    name = "Germanium.commodity";
                } else if (payload.getCommodityType() == 2) {
                    name = "Silicon.commodity";
                } else if (payload.getCommodityType() == 3) {
                    name = "YBCO.commodity";
                } else if (payload.getCommodityType() == 4) {
                    name = "Plutonium.commodity";
                }
                newHackerFile.setName(name);
                computer.getComputerHandler().addData(new ApplicationData(new SaveFileRequestPayload("", newHackerFile), 0, computer.ip), computer.ip);
            } else {
                failed = true;
                computer.addMessage(MessageHandler.EXCHANGE_COMMODITY_FAIL_NOT_ENOUGH_COMMODITY, new Object[]{name});
            }
        } else if (!computer.checkBank()) {
            failed = true;
            computer.addMessage(MessageHandler.EXCHANGE_COMMODITY_FAIL_NO_BANKING_PORT);
        } else if (computer.getPettyCash() <= payload.getExchangeCost()) {
            failed = true;
            computer.addMessage(MessageHandler.EXCHANGE_COMMODITY_FAIL_NOT_ENOUGH_MONEY);
        } else if (computer.MyFileSystem.getSpaceLeft() <= 0) {
            failed = true;
            computer.addMessage(MessageHandler.EXCHANGE_COMMODITY_FAIL_HD_FULL);
        }

        if (!failed) {
            computer.addMessage(MessageHandler.EXCHANGE_COMMODITY_SUCCESS, new Object[]{payload.getExchangeAmount(), name});
        }

        computer.systemChange = true;
    }

    private void applyExchangeFile(Computer computer, ApplicationData applicationData, CombatExchangeFilePayload payload) {
        boolean failed = false;
        HackerFile tempFile = null;
        if (computer.checkBank() && computer.getPettyCash() >= payload.getExchangeCost()) {
            ArrayList commodityFiles = computer.MyFileSystem.getFilesOfType(HackerFile.COMMODITY_SLIP);
            if (commodityFiles != null) {
                for (int i = 0; i < commodityFiles.size(); i++) {
                    tempFile = (HackerFile) commodityFiles.get(i);
                    int type = Integer.parseInt((String) tempFile.getContent().get("data"));
                    if (type == payload.getCommodityType()) {
                        if (tempFile.getQuantity() >= payload.getExchangeAmount()) {
                            computer.setPettyCash(computer.getPettyCash() - payload.getExchangeCost());
                            computer.setCommodityAmount(payload.getCommodityType(), computer.getCommodity(payload.getCommodityType()) + payload.getExchangeAmount());
                            tempFile.setQuantity(tempFile.getQuantity() - payload.getExchangeAmount());
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
        } else if (computer.getPettyCash() <= payload.getExchangeCost()) {
            failed = true;
            computer.addMessage(MessageHandler.EXCHANGE_COMMODITY_FAIL_NOT_ENOUGH_MONEY);
        }

        if (!failed && tempFile != null) {
            computer.addMessage(MessageHandler.EXCHANGE_COMMODITY_SUCCESS_FROM_FILE, new Object[]{payload.getExchangeAmount(), tempFile.getName()});
        }

        computer.systemChange = true;
    }

    private void queueQuestRefresh(Computer computer, String sourceIp) {
        HashMap tempHashMap = new HashMap();
        tempHashMap.put("packetid", -1);
        computer.getComputerHandler().addData(new ApplicationData(new RequestWebPagePayload(tempHashMap), 0, computer.ip), sourceIp);
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
