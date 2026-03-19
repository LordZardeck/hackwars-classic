package game;

import game.computer.session.ComputerSessionService;
import hackscript.model.TypeBoolean;
import hackscript.model.TypeFloat;
import hackscript.model.TypeInteger;
import hackscript.model.TypeString;
import hackscript.model.Variable;
import util.LocalWebConfig;

import java.lang.reflect.Field;
import java.util.HashMap;

/**
 * Extracted legacy filesystem and inventory command branches from Computer.
 */
public class LegacyFilesystemInventoryCommands implements LegacyApplicationDataHandler {
    @Override
    public boolean dispatch(Computer computer, ApplicationData applicationData, int resolvedPort) {
        String function = applicationData.getFunction();

        if (function.equals("requestdirectory")) {
            String path = (String) ((Object[]) applicationData.getParameters())[0];
            Object[] directory = computer.MyFileSystem.getDirectory(path);
            Object[] response = new Object[directory.length + 1];
            response[0] = ((Object[]) applicationData.getParameters())[1];
            for (int i = 0; i < directory.length; i++) {
                response[i + 1] = directory[i];
            }
            computer.PA.setDirectory(response);
            computer.systemChange = true;
            return true;
        }

        if (function.equals("delivereddirectory")) {
            Object[] params = (Object[]) applicationData.getParameters();
            Object[] directory = null;
            if (params.length == 1) {
                directory = (Object[]) applicationData.getParameters();
            } else if (params.length == 2) {
                directory = (Object[]) params[0];
                boolean npcBool = (Boolean) params[1];
                computer.PA.setAllowedDir(!npcBool || computer.isNPC());
            }
            computer.PA.setSecondaryDirectory(directory);
            computer.systemChange = true;
            return true;
        }

        if (function.equals("requestfile")) {
            String path = ((String[]) applicationData.getParameters())[0];
            String name = ((String[]) applicationData.getParameters())[1];
            HackerFile file = computer.MyFileSystem.getFile(path, name);
            if (file != null) {
                if (file.getType() == HackerFile.GAME || file.getType() == HackerFile.GAME_PROJECT) {
                    file = file.clone();
                    file.setContent(null);
                }
            }
            computer.PA.setFile(file);
            computer.systemChange = true;
            return true;
        }

        if (function.equals("requestgame")) {
            String path = ((String[]) applicationData.getParameters())[0];
            String name = ((String[]) applicationData.getParameters())[1];
            HackerFile file = computer.MyFileSystem.getFile(path, name);

            HashMap loadFile = new HashMap();
            HackerFile saveFile = computer.MyFileSystem.getFile("", name + ".save");
            if (saveFile != null) {
                String data = (String) saveFile.getContent().get("data");
                if (data != null) {
                    String[] entries = data.split("\n");
                    try {
                        for (int i = 0; i < entries.length; i++) {
                            String[] entryData = entries[i].split("\t");
                            String key = entryData[0];
                            String type = entryData[1];
                            Variable variable = null;
                            if (type.equals("string")) {
                                variable = new TypeString(entryData[2]);
                            } else if (type.equals("bool")) {
                                variable = new TypeBoolean(Boolean.valueOf(entryData[2]));
                            } else if (type.equals("int")) {
                                variable = new TypeInteger(Integer.valueOf(entryData[2]));
                            } else if (type.equals("float")) {
                                variable = new TypeFloat(Float.valueOf(entryData[2]));
                            }

                            if (variable != null) {
                                loadFile.put(key, variable);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            computer.PA.setLoadFile(loadFile);
            computer.PA.setFile(file);
            computer.systemChange = true;
            return true;
        }

        if (function.equals("setfiledescription")) {
            String path = (String) ((Object[]) applicationData.getParameters())[0];
            String name = (String) ((Object[]) applicationData.getParameters())[1];
            String description = (String) ((Object[]) applicationData.getParameters())[2];
            HackerFile file = computer.MyFileSystem.getFile(path, name);
            if (file != null && file.getType() != HackerFile.CLUE) {
                file.setDescription(description);
                computer.PA.setFile(file);
            }
            computer.systemChange = true;
            return true;
        }

        if (function.equals("setfileprice")) {
            String path = (String) ((Object[]) applicationData.getParameters())[0];
            String name = (String) ((Object[]) applicationData.getParameters())[1];
            Float price = (Float) ((Object[]) applicationData.getParameters())[2];
            HackerFile file = computer.MyFileSystem.getFile(path, name);
            if (file != null) {
                file.setPrice(price);
                computer.PA.setFile(file);
            }
            computer.systemChange = true;
            return true;
        }

        if (function.equals("deletefile")) {
            String path = (String) ((Object[]) applicationData.getParameters())[0];
            String name = (String) ((Object[]) applicationData.getParameters())[1];
            computer.MyFileSystem.deleteFile(path, name);
            computer.PA.setRequestPrimary(true, 1);
            computer.systemChange = true;
            return true;
        }

        if (function.equals("deletemulti")) {
            Object[] allFiles = (Object[]) ((Object[]) applicationData.getParameters())[0];
            for (int i = 0; i < allFiles.length; i++) {
                String[] file = (String[]) allFiles[i];
                String path = file[0];
                String name = file[1];
                if (file[2].equals("directory")) {
                    computer.MyFileSystem.deleteDirectory(path + "/" + name + "/");
                } else {
                    computer.MyFileSystem.deleteFile(path, name);
                }
            }
            computer.PA.setRequestPrimary(true, 1);
            computer.systemChange = true;
            return true;
        }

        if (function.equals("decompilefile")) {
            String path = (String) ((Object[]) applicationData.getParameters())[0];
            String fileName = (String) ((Object[]) applicationData.getParameters())[1];
            HackerFile existingFile = computer.MyFileSystem.getFile(path, fileName);
            HackerFile file = existingFile.clone();
            if (existingFile.getTypeString().equals("compiled") && (existingFile.getMaker().toUpperCase().equals(computer.userName.toUpperCase()))) {
                if (existingFile != null) {
                    float compilePrice = (Float) ((Object[]) applicationData.getParameters())[2];
                    HashMap levels = new HashMap();
                    levels.put("Attack", Integer.valueOf(100));
                    levels.put("Merchanting", Integer.valueOf(100));
                    levels.put("Watch", Integer.valueOf(100));
                    try {
                        HashMap result = executeCompileApplication(computer, existingFile.getType(), existingFile.getContent(), levels);
                        if (result != null && ((String) (result.get("error"))).length() == 0) {
                            compilePrice = (float) (double) (Double) result.get("price");
                        }
                    } catch (Exception ignored) {
                    }
                    existingFile.setQuantity(existingFile.getQuantity() - 1);
                    if (existingFile.getQuantity() <= 0) {
                        computer.MyFileSystem.deleteFile(path, existingFile.getName());
                    }

                    float xp = compilePrice / 100.0f;
                    String xpType = "";

                    if (existingFile.getType() == HackerFile.BANKING_COMPILED) {
                        xpType = "bankxp";
                    } else if (existingFile.getType() == HackerFile.ATTACKING_COMPILED) {
                        xpType = "attackxp";
                    } else if (existingFile.getType() == HackerFile.SHIPPING_COMPILED) {
                        xpType = "redirectxp";
                    } else if (existingFile.getType() == HackerFile.HTTP) {
                        xpType = "httpxp";
                    } else if (existingFile.getType() == HackerFile.WATCH_COMPILED) {
                        xpType = "watchxp";
                    }

                    computer.MyComputerHandler.addData(new ApplicationData(xpType, Float.valueOf((-1.0f) * xp), 0, computer.ip), computer.ip);

                    if (file.getType() != HackerFile.HTTP) {
                        file.setType(file.getType() + 1);
                    }
                    if (existingFile.getType() != HackerFile.HTTP_SCRIPT) {
                        file.setType(existingFile.getType() + 1);
                    } else {
                        file.setType(HackerFile.HTTP_SCRIPT);
                    }
                    file.setName(existingFile.getName().replaceAll("\\.bin", ""));
                    computer.MyComputerHandler.addData(new ApplicationData("pettycash", Float.valueOf(compilePrice), 0, computer.ip), computer.ip);
                    computer.saveFile(file, file, path);
                }
            }
            return true;
        }

        if (function.equals("sellfilemulti")) {
            Object[] allFiles = (Object[]) ((Object[]) applicationData.getParameters())[0];
            String ip = (String) ((Object[]) applicationData.getParameters())[1];
            float compileCost = 0.0f;
            float totalPay = 0.0f;
            if (computer.checkBank()) {
                for (int i = 0; i < allFiles.length; i++) {
                    Object[] fileData = (Object[]) allFiles[i];
                    String path = (String) fileData[0];
                    String name = (String) fileData[1];
                    String maker = (String) fileData[2];
                    Integer quantity = (Integer) fileData[3];
                    HackerFile file = computer.MyFileSystem.getFile(path, name);
                    if (file.getType() != HackerFile.NEW_FIREWALL) {
                        totalPay += (Float) Computer.makers.get(maker) * quantity;
                    } else {
                        HashMap content = file.getContent();
                        Object price = content.get("store_price");
                        if (price != null) {
                            totalPay += Float.parseFloat("" + price);
                        }
                    }
                    if (file != null && file.getQuantity() >= quantity) {
                        file.setQuantity(file.getQuantity() - quantity);
                        if (file.getQuantity() <= 0) {
                            computer.MyFileSystem.deleteFile(path, file.getName());
                        }

                        Object[] payload = new Object[]{path, file.clone(), compileCost, ip, quantity};
                        computer.MyComputerHandler.addData(new ApplicationData("sellfile", payload, 0, "store" + computer.getServerID()), computer.store);
                    }
                }
                computer.pettyCash += totalPay;
            } else {
                computer.addMessage(MessageHandler.SELL_FAIL_BANK_PORT);
            }
            computer.PA.setRequestPrimary(true, 1);
            computer.systemChange = true;
            return true;
        }

        if (function.equals("savefile")) {
            Object[] parameters = (Object[]) applicationData.getParameters();
            String path = (String) parameters[0];
            HackerFile file = (HackerFile) parameters[1];
            HackerFile existingFile = computer.MyFileSystem.getFile(path, file.getName());
            boolean stolenFile = false;
            String stolenFromIP = "";
            int stolenFromPort = -1;
            if (parameters.length == 4) {
                if (parameters[2] instanceof String) {
                    stolenFile = true;
                    stolenFromIP = (String) parameters[2];
                    stolenFromPort = (Integer) parameters[3];
                }
            }

            computer.saveFile(file, existingFile, path);
            if (stolenFile) {
                computer.addMessage(MessageHandler.FILE_SUCCESSFULLY_STOLEN, new Object[]{file.getName(), stolenFromIP}, new Object[]{stolenFromPort, stolenFromIP});
                computer.addMessage(MessageHandler.FILE_SUCCESSFULLY_STOLEN_GAME, new Object[]{file.getName(), stolenFromIP});
            }
            if (file.getType() == HackerFile.PCI || file.getType() == HackerFile.AGP) {
                computer.PA.setRequestHardware(true);
            }
            return true;
        }

        if (function.equals("compilefile")) {
            boolean success = true;
            Object[] parameters = (Object[]) applicationData.getParameters();
            String path = (String) parameters[0];
            HackerFile file = (HackerFile) parameters[1];
            HackerFile existingFile = computer.MyFileSystem.getFile(path, file.getName());

            float price = (Float) ((Object[]) applicationData.getParameters())[2];

            HashMap playerLevels = new HashMap();
            playerLevels.put("Attack", Integer.valueOf(computer.getLevel((float) (Float) computer.Stats.get("Attack"))));
            playerLevels.put("Merchanting", Integer.valueOf(computer.getLevel((float) (Float) computer.Stats.get("Bank"))));
            playerLevels.put("Watch", Integer.valueOf(computer.getLevel((float) (Float) computer.Stats.get("Watch"))));
            playerLevels.put("HTTP", Integer.valueOf(computer.getLevel((float) (Float) computer.Stats.get("Webdesign"))));
            playerLevels.put("Redirecting", Integer.valueOf(computer.getLevel((float) (Float) computer.Stats.get("Redirecting"))));

            try {
                HashMap result = executeCompileApplication(computer, file.getType(), file.getContent(), playerLevels);
                if (result != null && ((String) (result.get("error"))).length() == 0) {
                    float cpuCost = (float) (double) (Double) result.get("cpucost");
                    price = (float) (double) (Double) result.get("price");
                    file.setCPUCost(cpuCost);
                }
            } catch (Exception e) {
                success = false;
            }

            if (!computer.checkBank()) {
                success = false;
                computer.addMessage(MessageHandler.ACTIVE_BANK_NOT_FOUND);
            }

            if (existingFile != null) {
                if (file.checkSumFailed(existingFile)) {
                    success = false;
                    computer.addMessage(MessageHandler.FILE_CHANGED_SINCE_LAST_SAVE);
                }
            }

            if (computer.pettyCash < price) {
                success = false;
                computer.addMessage(MessageHandler.COMPILE_FAIL_NOT_ENOUGH_MONEY);
            } else if ((computer.MyFileSystem.getSpaceLeft() < 1) && existingFile == null) {
                success = false;
                computer.addMessage(MessageHandler.COMPILE_FAIL_HD_FULL);
            } else if (success) {
                if (file.getType() != HackerFile.FTP_COMPILED) {
                    float xp = price / 100.0f;
                    String xpType = "";

                    if (file.getType() == HackerFile.BANKING_COMPILED) {
                        xpType = "bankxp";
                    } else if (file.getType() == HackerFile.ATTACKING_COMPILED) {
                        xpType = "attackxp";
                    } else if (file.getType() == HackerFile.SHIPPING_COMPILED) {
                        xpType = "redirectxp";
                    } else if (file.getType() == HackerFile.WATCH_COMPILED) {
                        xpType = "watchxp";
                    } else if (file.getType() == HackerFile.HTTP) {
                        xpType = "httpxp";
                    }

                    computer.MyComputerHandler.addData(new ApplicationData(xpType, Float.valueOf(xp), 0, computer.ip), computer.ip);
                }
                computer.MyComputerHandler.addData(new ApplicationData("pettycash", Float.valueOf(price * -1.0f), 0, computer.ip), computer.ip);
                file.setMaker(computer.userName);
                computer.saveFile(file, existingFile, path);
            }
            return true;
        }

        if (function.equals("sellfile")) {
            boolean success = true;
            Object[] parameters = (Object[]) applicationData.getParameters();
            String path = (String) parameters[0];
            HackerFile file = (HackerFile) parameters[1];
            HackerFile existingFile = computer.MyFileSystem.getFile(path, file.getName());

            float sellPrice = 0.0f;
            float minimumSellPrice = 0.0f;
            if (file.getType() != HackerFile.NEW_FIREWALL) {
                minimumSellPrice = (Float) Computer.makers.get(file.getMaker());
            }

            file.setQuantity(1);
            float compilePrice = (Float) ((Object[]) applicationData.getParameters())[2];
            HashMap playerLevels = new HashMap();
            playerLevels.put("Attack", Integer.valueOf(100));
            playerLevels.put("Merchanting", Integer.valueOf(100));
            playerLevels.put("Watch", Integer.valueOf(100));
            playerLevels.put("HTTP", Integer.valueOf(100));
            playerLevels.put("Redirecting", Integer.valueOf(100));

            try {
                HashMap result = executeCompileApplication(computer, file.getType(), file.getContent(), playerLevels);
                if (result != null && ((String) (result.get("error"))).length() == 0) {
                    compilePrice = (float) (double) (Double) result.get("price");
                }
            } catch (Exception e) {
                compilePrice = 0.0f;
            }

            int quantity = (Integer) ((Object[]) applicationData.getParameters())[4];
            if (file.getType() == HackerFile.AGP || file.getType() == HackerFile.PCI) {
                if (file.getMaker().equals("Medium")) {
                    sellPrice = 2000.0f;
                } else if (file.getMaker().equals("High")) {
                    sellPrice = 20000.0f;
                } else if (file.getMaker().equals("Rare")) {
                    sellPrice = 200000.0f;
                }
            } else {
                if (existingFile != null) {
                    sellPrice = compilePrice * 2.0f - (compilePrice * 0.01f * (1.0f + existingFile.getQuantity()));
                } else {
                    sellPrice = compilePrice * 2.0f - (compilePrice * 0.01f);
                }
            }

            if (sellPrice < minimumSellPrice) {
                sellPrice = minimumSellPrice;
            }

            file.setPrice(sellPrice);

            if (file.getType() == HackerFile.NEW_FIREWALL) {
                success = false;
            }

            if (success) {
                file.setLocation("Store/");
                computer.saveFile(file, existingFile, path);

                if (file.getType() == HackerFile.PCI || file.getType() == HackerFile.AGP) {
                    computer.PA.setRequestHardware(true);
                }
            }
            computer.systemChange = true;
            return true;
        }

        return false;
    }

    private HashMap executeCompileApplication(Computer computer, int type, HashMap content, HashMap levels) throws Exception {
        Object[] params = new Object[]{Integer.valueOf(type), content, levels};
        return (HashMap) getSessionService(computer).executeRemote(LocalWebConfig.getXmlRpcUrl(), "hackerRPC.compileApplication", params);
    }

    private ComputerSessionService getSessionService(Computer computer) throws Exception {
        Field field = Computer.class.getDeclaredField("sessionService");
        field.setAccessible(true);
        return (ComputerSessionService) field.get(computer);
    }
}
