package game;

import com.hackwars.game.program.AttackProgram;
import com.hackwars.game.program.HTTPProgram;
import com.hackwars.game.program.Program;
import com.hackwars.game.program.ShippingProgram;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashMap;

public class LegacyEconomyWebSocialCommands implements LegacyApplicationDataHandler {
    @Override
    public boolean dispatch(Computer computer, ApplicationData applicationData, int resolvedPort) {
        String function = applicationData.getFunction();

        if (function.equals("setpreferences")) {
            HashMap preferences = (HashMap) ((Object[]) applicationData.getParameters())[1];
            computer.preferences = preferences;
            return true;
        } else if (function.equals("message")) {
            Object messageObject = applicationData.getParameters();
            if (messageObject instanceof String) {
                computer.addMessage((String) messageObject);
            } else if (messageObject instanceof Object[]) {
                Object[] messageArray = (Object[]) messageObject;
                Object[] message = (Object[]) messageArray[0];
                if (messageArray[1] instanceof Object[]) {
                    Object[] parameters = (Object[]) messageArray[1];
                    if (messageArray.length > 2) {
                        Object[] portInfo = (Object[]) messageArray[2];
                        computer.addMessage(message, parameters, portInfo);
                    } else {
                        computer.addMessage(message, parameters);
                    }
                } else {
                    computer.addMessage(messageArray);
                }
            }
            computer.systemChange = true;
            return true;
        } else if (function.equals("sendemail")) {
            String message = (String) applicationData.getParameters();
            if (computer.checkBank()) {
                if (computer.pettyCash >= 100.0f) {
                    try {
                        Object[] params = new Object[]{computer.ip, message};
                        computer.getSessionService().executeRemote("http://www.hackwars.net/xmlrpc/mail.php", "sendEmail", params);
                    } catch (Exception ignored) {
                    }
                    computer.getComputerHandler().addData(new ApplicationData("pettycash", -100.0f, 0, computer.ip), computer.ip);
                }
            }
            return true;
        } else if (function.equals("sendfacebook")) {
            String message = (String) ((Object[]) applicationData.getParameters())[0];
            String targetIP = (String) ((Object[]) applicationData.getParameters())[1];
            try {
                Object[] params = new Object[]{computer.ip, targetIP, message};
                computer.getSessionService().executeRemote("http://www.hackwars.net/xmlrpc/facebook.php", "sendFacebook", params);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return true;
        } else if (function.equals("facebookupdate")) {
            String message = (String) applicationData.getParameters();
            try {
                Object[] params = new Object[]{
                    computer.ip,
                    Double.valueOf(computer.pettyCash),
                    Double.valueOf(computer.bankMoney),
                    Integer.valueOf(computer.defaultBank)
                };
                computer.getSessionService().executeRemote("http://www.hackwars.net/xmlrpc/facebook.php", "updateFacebook", params);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return true;
        } else if (function.equals("dailypayset")) {
            String bountyip = (String) applicationData.getParameters();
            computer.MyMakeBounty.checkBounty(computer, null, computer.MyMakeBounty.CHANGE, applicationData.getSourceIP(), false, bountyip);
            return true;
        } else if (function.equals("pettycash")) {
            DecimalFormat nf = new DecimalFormat("#.00");
            float value = 0.0f;
            float returnValue = 0.0f;
            boolean sendMessage = true;
            Object parameters = applicationData.getParameters();
            if (parameters instanceof Float) {
                value = (Float) parameters;
            } else if (parameters instanceof Object[]) {
                Object[] params = (Object[]) parameters;
                value = (Float) params[0];
                Object secondEntry = params[1];
                if (secondEntry instanceof Float) {
                    returnValue = (Float) params[1];
                } else if (secondEntry instanceof Boolean) {
                    sendMessage = false;
                }
            }
            int noobLevel = computer.getNoobSafety();
            if (computer.getTotalLevel() < noobLevel && !applicationData.getSourceIP().equals(computer.ip)) {
                computer.addMessage(MessageHandler.TRANSFER_FAIL_NOOB_LEVEL, new Object[]{Integer.valueOf(noobLevel)});
                if (!applicationData.getSourceIP().equals(computer.ip)) {
                    computer.getComputerHandler().addData(applicationData, applicationData.getSourceIP());
                }
            } else if (computer.checkBank()) {
                computer.setPettyCash(computer.pettyCash + value);
                CentralLogging.getInstance().addOutput(computer.ip + "\t" + applicationData.getSourceIP() + "\t1\t" + value + "\n");
                if (!applicationData.getSourceIP().equals(computer.ip) && sendMessage) {
                    ApplicationData message = new ApplicationData(
                        "message",
                        new Object[]{MessageHandler.TRANSFER_SENT_SUCCESSFUL, new Object[]{nf.format(value)}},
                        0,
                        applicationData.getSourceIP()
                    );
                    computer.getComputerHandler().addData(message, applicationData.getSourceIP());
                    computer.addMessage(MessageHandler.TRANSFER_RECEIVED, new Object[]{nf.format(value), applicationData.getSourceIP()});
                }
                if (computer.pettyCash < 0) {
                    computer.pettyCash = 0;
                }
            } else {
                computer.addMessage(MessageHandler.TRANSFER_RECEIVE_FAIL_BANK_PORT, new Object[]{nf.format(value), applicationData.getSourceIP()});
                if (!applicationData.getSourceIP().equals(computer.ip)) {
                    ApplicationData pettyCash = new ApplicationData("pettycash", returnValue, 0, applicationData.getSourceIP());
                    ApplicationData message = new ApplicationData("message", MessageHandler.TRANSFER_SEND_FAIL_BANK_PORT, 0, applicationData.getSourceIP());
                    computer.getComputerHandler().addData(pettyCash, applicationData.getSourceIP());
                    computer.getComputerHandler().addData(message, applicationData.getSourceIP());
                }
            }

            if (computer.pettyCash < computer.respawnMoney) {
                computer.respawn(Port.BANKING);
            }

            computer.systemChange = true;
            return true;
        } else if (function.equals("commodity")) {
            if (computer.checkShipping()) {
                Object[] parameters = (Object[]) applicationData.getParameters();
                int commodity = (Integer) parameters[0];
                float value = (Float) parameters[1];
                int redirectPort = (Integer) parameters[2];
                Port port = (Port) computer.Ports.get(Integer.valueOf(redirectPort));
                int windowHandle = getWindowHandle(port);
                computer.setCommodityAmount(commodity, computer.getCommodity(commodity) + value);
                String targetIP = (String) parameters[3];
                computer.addMessage(
                    MessageHandler.RECEIVED_COMMODITY,
                    new Object[]{Integer.valueOf((int) value), Computer.commodityString[commodity]},
                    new Object[]{Integer.valueOf(windowHandle), computer.ip}
                );
                computer.addMessage(
                    MessageHandler.RECEIVED_COMMODITY_GAME,
                    new Object[]{Integer.valueOf((int) value), Computer.commodityString[commodity], targetIP}
                );
            } else {
                computer.addMessage(MessageHandler.RECEIVED_COMMODITY_FAIL);
                if (!applicationData.getSourceIP().equals(computer.ip)) {
                    computer.getComputerHandler().addData(applicationData, applicationData.getSourceIP());
                }
            }

            computer.systemChange = true;
            return true;
        } else if (function.equals("bank")) {
            float value = (Float) applicationData.getParameters();
            if (computer.checkBank()) {
                computer.bankMoney += value;
                if (value > 0) {
                    CentralLogging.getInstance().addOutput(computer.ip + "\t" + applicationData.getSourceIP() + "\t0\t" + value + "\n");
                }
            } else if (value > 0.0f) {
                computer.addMessage(MessageHandler.ACTIVE_BANK_NOT_FOUND);
            }
            computer.systemChange = true;
            return true;
        } else if (function.equals("requestpage")) {
            computer.getPacketAssignment().setBody(computer.getBody());
            computer.getPacketAssignment().setTitle(computer.getTitle());
            computer.systemChange = true;
            return true;
        } else if (function.equals("requestpurchase")) {
            String file = (String) ((Object[]) applicationData.getParameters())[0];
            int quantity = (Integer) ((Object[]) applicationData.getParameters())[1];

            if (quantity > 0) {
                HackerFile hackerFile = computer.getFileSystem().getFile("Store/", file);
                if (!(hackerFile == null)) {
                    if (hackerFile.getQuantity() < quantity && hackerFile.getQuantity() != -1) {
                        quantity = hackerFile.getQuantity();
                    }

                    HackerFile purchasedFile = hackerFile.clone();
                    purchasedFile.setQuantity(quantity);

                    if (hackerFile.getQuantity() != -1) {
                        hackerFile.setQuantity(hackerFile.getQuantity() - quantity);
                        if (hackerFile.getQuantity() <= 0) {
                            computer.MyFileSystem.deleteFile("Store/", file);
                        }
                    }

                    Object[] payload = new Object[]{purchasedFile, computer.storeRevenueTarget, Integer.valueOf(computer.type)};
                    computer.getComputerHandler().addData(new ApplicationData("continuepurchase", payload, 0, computer.ip), applicationData.getSourceIP());
                } else {
                    computer.getComputerHandler().addData(new ApplicationData("message", MessageHandler.PURCHASE_FAIL_FILE_NOT_FOUND, 0, computer.ip), applicationData.getSourceIP());
                }
            }
            computer.systemChange = true;
            return true;
        } else if (function.equals("continuepurchase")) {
            HackerFile hackerFile = (HackerFile) ((Object[]) applicationData.getParameters())[0];
            String payTarget = (String) ((Object[]) applicationData.getParameters())[1];
            int sellerType = (Integer) ((Object[]) applicationData.getParameters())[2];
            int quantity = hackerFile.getQuantity();
            HackerFile existingFile = computer.MyFileSystem.getFile("", hackerFile.getName());

            float level = 0.0f;
            if (hackerFile != null) {
                if (hackerFile.getType() == HackerFile.CPU) {
                    level = new Float((String) hackerFile.getContent().get("level"));
                } else if (hackerFile.getType() == HackerFile.HD) {
                    level = new Float((String) hackerFile.getContent().get("level"));
                } else if (hackerFile.getType() == HackerFile.FIREWALL) {
                    level = new Float((String) hackerFile.getContent().get("level"));
                } else if (hackerFile.getType() == HackerFile.MEMORY) {
                    level = new Float((String) hackerFile.getContent().get("level"));
                }
            }

            float totalLevel = 0.0f;
            if (!(hackerFile.getType() == HackerFile.FIREWALL)) {
                totalLevel += computer.getLevel((Float) computer.Stats.get("Attack"));
                totalLevel += computer.getLevel((Float) computer.Stats.get("Bank"));
                totalLevel += computer.getLevel((Float) computer.Stats.get("Watch"));
                totalLevel += computer.getLevel((Float) computer.Stats.get("Scanning"));
                totalLevel += computer.getLevel((Float) computer.Stats.get("Webdesign"));
                totalLevel += computer.getLevel((Float) computer.Stats.get("Redirecting"));
                totalLevel += computer.getLevel((Float) computer.Stats.get("Repair"));
            }
            totalLevel += computer.getLevel((Float) computer.Stats.get("FireWall"));

            float mult = (computer.getLevel((Float) computer.Stats.get("Bank")) - 50.0f) / 100.0f;
            float price = (quantity * hackerFile.getPrice()) - (quantity * hackerFile.getPrice() * mult);

            if (computer.MyFileSystem.getSpaceLeft() <= 0) {
                computer.addMessage(MessageHandler.PURCHASE_FAIL_HD_FULL);
                Object[] payload = new Object[]{"Store/", hackerFile};
                computer.getComputerHandler().addData(new ApplicationData("savefile", payload, 0, computer.ip), applicationData.getSourceIP());
            } else if (!computer.checkBank() && price > 0) {
                computer.addMessage(MessageHandler.ACTIVE_BANK_NOT_FOUND);
                Object[] payload = new Object[]{"Store/", hackerFile};
                computer.getComputerHandler().addData(new ApplicationData("savefile", payload, 0, computer.ip), applicationData.getSourceIP());
            } else if (computer.pettyCash < price) {
                computer.addMessage(MessageHandler.PURCHASE_FAIL_NOT_ENOUGH_MONEY);
                Object[] payload = new Object[]{"Store/", hackerFile};
                computer.getComputerHandler().addData(new ApplicationData("savefile", payload, 0, computer.ip), applicationData.getSourceIP());
            } else if (computer.MyFileSystem.getSpaceLeft() < 1 && !((hackerFile.getType() == HackerFile.CPU || hackerFile.getType() == HackerFile.HD || hackerFile.getType() == HackerFile.MEMORY) || existingFile != null)) {
                computer.addMessage(MessageHandler.PURCHASE_FAIL_HD_FULL);
                Object[] payload = new Object[]{"Store/", hackerFile};
                computer.getComputerHandler().addData(new ApplicationData("savefile", payload, 0, computer.ip), applicationData.getSourceIP());
            } else if (totalLevel < level && sellerType == 1) {
                Object[] payload = new Object[]{"Store/", hackerFile};
                computer.getComputerHandler().addData(new ApplicationData("savefile", payload, 0, computer.ip), applicationData.getSourceIP());
                computer.addMessage(MessageHandler.PURCHASE_FAIL_NOT_HIGH_ENOUGH_LEVEL);
            } else {
                boolean buyFail = false;
                Object[] payload = new Object[]{"", hackerFile};
                if (hackerFile.getType() == HackerFile.CPU) {
                    int type = new Integer((String) hackerFile.getContent().get("data"));
                    if (Computer.CPU_CHART[type] > Computer.CPU_CHART[computer.cputype]) {
                        computer.cputype = type;
                        computer.addMessage(MessageHandler.PURCHASE_NEW_CPU, new Object[]{hackerFile.getName()});
                    } else {
                        buyFail = true;
                        computer.addMessage(MessageHandler.PURCHASE_FAIL_OLDER_CPU);
                    }
                } else if (hackerFile.getType() == HackerFile.HD) {
                    int type = new Integer((String) hackerFile.getContent().get("data"));
                    if (computer.MyFileSystem.checkType(type)) {
                        computer.MyFileSystem.setHDType(type);
                        computer.addMessage(MessageHandler.PURCHASE_NEW_HD, new Object[]{hackerFile.getName()});
                    } else {
                        buyFail = true;
                        computer.addMessage(MessageHandler.PURCHASE_FAIL_OLDER_HD);
                    }
                } else if (hackerFile.getType() == HackerFile.MEMORY) {
                    int type = new Integer((String) hackerFile.getContent().get("data"));
                    if ((Computer.MEMORY_CHART[type] > Computer.MEMORY_CHART[computer.memorytype]) || (Computer.WATCH_CHART[type] > Computer.WATCH_CHART[computer.memorytype])) {
                        computer.memorytype = type;
                        computer.addMessage(MessageHandler.PURCHASE_NEW_MEMORY, new Object[]{hackerFile.getName()});
                    } else {
                        buyFail = true;
                        computer.addMessage(MessageHandler.PURCHASE_FAIL_OLDER_MEMORY);
                    }
                } else {
                    hackerFile.setLocation("");
                    if (hackerFile.getType() == HackerFile.FIREWALL) {
                        HashMap setLevel = hackerFile.getContent();
                        setLevel.put("level", "0");
                    }
                    computer.getComputerHandler().addData(new ApplicationData("savefile", payload, 0, computer.ip), computer.ip);
                    NumberFormat format = NumberFormat.getCurrencyInstance();
                    computer.addMessage(MessageHandler.PURCHASE_SUCCESS, new Object[]{Integer.valueOf(quantity), hackerFile.getName(), format.format(price)});
                }

                if (price > 0 && !buyFail) {
                    computer.getComputerHandler().addData(new ApplicationData("pettycash", new Object[]{new Float(price * -1.0f), Boolean.FALSE}, 0, computer.ip), computer.ip);
                    computer.getComputerHandler().addData(new ApplicationData("pettycash", new Object[]{new Float(price), Boolean.FALSE}, 0, computer.ip), payTarget);
                }
                computer.getComputerHandler().addData(new ApplicationData("requestwebpage", null, 0, computer.ip), applicationData.getSourceIP());
                computer.getComputerHandler().addData(new ApplicationData("requestequipment", new Integer(13), 0, computer.ip), computer.ip);
            }
            computer.systemChange = true;
            return true;
        } else if (function.equals("requestwebpage")) {
            Port port = (Port) computer.Ports.get(new Integer(computer.defaultHTTP));
            if (port != null && port.getType() == Port.HTTP) {
                if (port.getProgram() != null && port.getOn()) {
                    Object attack = null;
                    if (applicationData.getParameters() != null) {
                        attack = ((HashMap) applicationData.getParameters()).get("Attack");
                    }
                    if (attack != null || computer.type != Computer.NPC) {
                        port.getProgram().execute(applicationData);
                    } else {
                        computer.getComputerHandler().addData(
                            new ApplicationData("questinformation", new Object[]{applicationData.getParameters(), computer.InvolvedQuests}, 0, computer.ip),
                            applicationData.getSourceIP()
                        );
                    }
                } else {
                    HTTPProgram httpProgram = new HTTPProgram(computer, computer.getComputerHandler());
                    httpProgram.serveWebPage(applicationData, (Integer) ((HashMap) applicationData.getParameters()).get("packetid"));
                }
            } else {
                HTTPProgram httpProgram = new HTTPProgram(computer, computer.getComputerHandler());
                httpProgram.serveWebPage(applicationData, (Integer) ((HashMap) applicationData.getParameters()).get("packetid"));
            }
            computer.systemChange = true;
            return true;
        } else if (function.equals("webpage")) {
            String pageTitle = (String) ((Object[]) applicationData.getParameters())[0];
            String pageBody = (String) ((Object[]) applicationData.getParameters())[1];
            Object[] files = (Object[]) ((Object[]) applicationData.getParameters())[2];
            Object[] temp;
            if (files != null) {
                temp = new Object[files.length + 1];
            } else {
                temp = new Object[1];
            }
            temp[0] = (Integer) ((Object[]) applicationData.getParameters())[3];

            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    temp[i + 1] = files[i];
                }
            }

            files = temp;

            computer.getPacketAssignment().setBody(pageBody);
            computer.getPacketAssignment().setTitle(pageTitle);
            computer.getPacketAssignment().setDirectory(files);
            computer.systemChange = true;
            return true;
        } else if (function.equals("savepage")) {
            if (((String) ((Object[]) applicationData.getParameters())[1]).length() > 30000) {
                computer.addMessage(MessageHandler.WEBSITE_SAVE_FAIL_TOO_BIG);
            } else {
                computer.pageTitle = (String) ((Object[]) applicationData.getParameters())[0];
                computer.pageBody = (String) ((Object[]) applicationData.getParameters())[1];
                computer.pageChanged = true;
            }
            computer.systemChange = true;
            return true;
        } else if (function.equals("submit")) {
            Port port = (Port) computer.Ports.get(new Integer(computer.defaultHTTP));
            if (port != null && port.getType() == Port.HTTP) {
                if (port.getProgram() != null && port.getOn()) {
                    port.getProgram().execute(applicationData);
                }
            }
            computer.systemChange = true;
            return true;
        } else if (function.equals("exit")) {
            Port port = (Port) computer.Ports.get(new Integer(computer.defaultHTTP));
            if (port != null && port.getType() == Port.HTTP) {
                if (port.getProgram() != null) {
                    port.getProgram().execute(applicationData);
                }
            }
            computer.systemChange = true;
            return true;
        } else if (function.equals("vote")) {
            int noobLevel = computer.getNoobSafety();
            if (computer.getTotalLevel() < noobLevel) {
                computer.addMessage(MessageHandler.VOTE_FAIL_NOOB_LEVEL, new Object[]{Integer.valueOf(noobLevel)});
            }
            if (applicationData.getSourceIP().equals(computer.ip)) {
                computer.addMessage(MessageHandler.VOTE_FAIL_OWN_SITE);
            } else if (computer.myVotes > 0) {
                computer.MyMakeBounty.checkBounty(computer, null, MakeBounty.VOTE, applicationData.getSourceIP(), false, "");
                computer.myVotes -= 1;
                computer.getComputerHandler().addData(new ApplicationData("httpxp", new Float(500.7337f), 0, computer.ip), applicationData.getSourceIP());
                computer.addMessage(MessageHandler.VOTE_SUCCESS, new Object[]{Integer.valueOf(computer.myVotes)});
            } else {
                computer.addMessage(MessageHandler.VOTE_FAIL_NO_VOTES);
            }
            computer.systemChange = true;
            return true;
        }

        return false;
    }

    private static int getWindowHandle(Port port) {
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

}
