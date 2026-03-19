package game;

import assignments.PacketPort;
import com.hackwars.game.program.AttackProgram;
import com.hackwars.game.program.Banking;
import com.hackwars.game.program.FTPProgram;
import com.hackwars.game.program.HTTPProgram;
import com.hackwars.game.program.Program;
import com.hackwars.game.program.ShippingProgram;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Legacy run-loop handler for port/application/firewall commands that still
 * live in {@link Computer#processQueuedItem(Object, long)}.
 */
public class LegacyPortApplicationCommands implements LegacyApplicationDataHandler {
    @Override
    public boolean dispatch(Computer computer, ApplicationData applicationData, int resolvedPort) {
        String function = applicationData.getFunction();

        if ("deletefirewall".equals(function)) {
            handleDeleteFirewall(computer, applicationData);
            return true;
        }
        if ("installfirewall".equals(function)) {
            handleInstallFirewall(computer, applicationData, resolvedPort);
            return true;
        }
        if ("replaceapplication".equals(function)) {
            handleReplaceApplication(computer, applicationData, resolvedPort);
            return true;
        }
        if ("installapplication".equals(function)) {
            handleInstallApplication(computer, applicationData, resolvedPort);
            return true;
        }
        if ("fetchports".equals(function)) {
            handleFetchPorts(computer);
            return true;
        }
        if ("requestsecondarydirectory".equals(function)) {
            // This still belongs to FTP port execution after pre-dispatch FTP normalization.
            return false;
        }

        return false;
    }

    private void handleDeleteFirewall(Computer computer, ApplicationData applicationData) {
        float maxCPU = Computer.CPU_CHART[computer.cputype] + computer.MyEquipmentSheet.getCPUBonus();
        if (computer.getCPULoad() <= maxCPU) {
            int targetPort = (Integer) applicationData.getParameters();

            Iterator portIterator = computer.Ports.entrySet().iterator();
            while (portIterator.hasNext()) {
                Port tempPort = (Port) (((Map.Entry) portIterator.next()).getValue());
                if (tempPort.getNumber() == targetPort) {
                    if (computer.MyFileSystem.getSpaceLeft() > 0) {
                        HackerFile installedAlready = tempPort.getFireWall().getHackerFile();
                        installedAlready = computer.checkRename(installedAlready, "");
                        installedAlready.setLocation("");
                        if (installedAlready.getQuantity() == 0) {
                            installedAlready.setQuantity(1);
                        }
                        computer.MyFileSystem.addFile(installedAlready, false);
                        tempPort.setFireWall(NewFireWall.createNoneFirewall());
                        computer.addMessage(MessageHandler.REMOVE_FIREWALL_SUCCESS, new Object[]{installedAlready.getName()});
                    } else {
                        computer.addMessage(MessageHandler.REMOVE_FIREWALL_FAIL_HD_FULL);
                    }
                    break;
                }
            }

            refreshPorts(computer);
        }
    }

    private void handleInstallFirewall(Computer computer, ApplicationData applicationData, int resolvedPort) {
        Object[] parameters = (Object[]) applicationData.getParameters();
        String path = (String) parameters[0];
        String name = (String) parameters[1];
        HackerFile hackerFile = computer.MyFileSystem.getFile(path, name);

        if (hackerFile != null) {
            int equipLevel = Integer.parseInt((String) hackerFile.getContent().get("equip_level"));
            if (computer.getLevel((float) (Float) computer.Stats.get("FireWall")) >= equipLevel) {
                float cpuCheck = computer.getCPULoad() + hackerFile.getCPUCost();
                float maxCPU = Computer.CPU_CHART[computer.cputype] + computer.MyEquipmentSheet.getCPUBonus();
                if (cpuCheck <= maxCPU) {
                    Port port = findPort(computer, resolvedPort);
                    if (port != null) {
                        hackerFile.setQuantity(hackerFile.getQuantity() - 1);
                        if (hackerFile.getQuantity() <= 0) {
                            computer.MyFileSystem.deleteFile(path, name);
                        }

                        HackerFile installedAlready = port.getFireWall().getHackerFile();
                        if (!installedAlready.getName().equals("None")) {
                            installedAlready = computer.checkRename(installedAlready, "");
                            installedAlready.setLocation("");
                            if (installedAlready.getQuantity() == 0) {
                                installedAlready.setQuantity(1);
                            }
                            computer.MyFileSystem.addFile(installedAlready, false);
                            port.setFireWall(hackerFile);
                            computer.addMessage(MessageHandler.FIREWALL_REPLACED, new Object[]{installedAlready.getName()});
                        } else {
                            port.setFireWall(hackerFile);
                        }
                    }
                } else {
                    computer.addMessage(MessageHandler.CPU_TOO_HIGH);
                }
            } else {
                computer.addMessage(MessageHandler.INSTALL_FIREWALL_FAIL_LEVEL, new Object[]{equipLevel});
            }
        }
        refreshPorts(computer);
    }

    private void handleReplaceApplication(Computer computer, ApplicationData applicationData, int resolvedPort) {
        Object[] parameters = (Object[]) applicationData.getParameters();
        String path = (String) parameters[0];
        String name = (String) parameters[1];
        HackerFile hackerFile = computer.MyFileSystem.getFile(path, name);

        if (hackerFile != null) {
            Port port = findPort(computer, resolvedPort);

            float cpuCheck = computer.getCPULoad() + hackerFile.getCPUCost() - port.getBaseCPUCost();
            float maxCPU = Computer.CPU_CHART[computer.cputype] + computer.MyEquipmentSheet.getCPUBonus();
            if (cpuCheck <= maxCPU) {
                if (port != null) {
                    boolean allow = true;
                    Object[] message = null;

                    if (!port.getAccessing().equals("")) {
                        allow = false;
                        message = MessageHandler.REPLACE_APPLICATION_UNDER_ATTACK;
                    }
                    if (port.getAttacking()) {
                        allow = false;
                        message = MessageHandler.REPLACE_APPLICATION_ATTACKING;
                    }
                    if (port.getOverHeated()) {
                        allow = false;
                        message = MessageHandler.REPLACE_APPLICATION_OVERHEATED;
                    }

                    if (!allow) {
                        computer.addMessage(message);
                        port = null;
                    }
                }

                if (port != null) {
                    hackerFile.setQuantity(hackerFile.getQuantity() - 1);
                    if (hackerFile.getQuantity() <= 0) {
                        computer.MyFileSystem.deleteFile(path, name);
                    }

                    int portType = hackerFile.getPortType();
                    if (portType >= 0) {
                        port.setType(portType);
                        port.setMaliciousTarget(computer.ip);
                        HashMap script = hackerFile.getContent();
                        Program newProgram = null;
                        port.setCPUCost(hackerFile.getCPUCost());

                        if (portType == Port.ATTACK) {
                            newProgram = new AttackProgram(computer, computer.MyComputerHandler, port, computer.Choices, computer.MyMakeBounty);
                        } else if (portType == Port.SHIPPING) {
                            newProgram = new ShippingProgram(computer, computer.MyComputerHandler, port);
                        } else if (portType == Port.BANKING) {
                            newProgram = new Banking(computer, computer.MyComputerHandler, port);
                        } else if (portType == Port.FTP) {
                            newProgram = new FTPProgram(computer, computer.MyComputerHandler, computer.MyFileSystem, port);
                        } else if (portType == Port.HTTP) {
                            computer.adRevenueTarget = computer.ip;
                            computer.storeRevenueTarget = computer.ip;
                            newProgram = new HTTPProgram(computer, computer.MyComputerHandler);
                            computer.dailyPayReduction = 1.0f;
                        }

                        newProgram.installScript(script);
                        port.setProgram(newProgram);
                        computer.addMessage(MessageHandler.REPLACE_APPLICATION_SUCCESS);
                    }
                }
            } else {
                computer.addMessage(MessageHandler.CPU_TOO_HIGH);
            }
        }
        refreshPorts(computer);
    }

    private void handleInstallApplication(Computer computer, ApplicationData applicationData, int resolvedPort) {
        if (resolvedPort < Computer.MEMORY_CHART[computer.memorytype]) {
            Object[] parameters = (Object[]) applicationData.getParameters();
            String path = (String) parameters[0];
            String name = (String) parameters[1];
            HackerFile hackerFile = computer.MyFileSystem.getFile(path, name);

            if (hackerFile != null) {
                float cpuCheck = computer.getCPULoad() + hackerFile.getCPUCost();
                float maxCPU = Computer.CPU_CHART[computer.cputype] + computer.MyEquipmentSheet.getCPUBonus();

                if (cpuCheck <= maxCPU) {
                    hackerFile.setQuantity(hackerFile.getQuantity() - 1);
                    if (hackerFile.getQuantity() <= 0) {
                        computer.MyFileSystem.deleteFile(path, name);
                    }

                    int portType = hackerFile.getPortType();
                    if (portType >= 0) {
                        Port port = new Port(computer, computer.MyComputerHandler);
                        port.setNumber(resolvedPort);
                        port.setType(portType);
                        port.setHealth(100.0f);
                        port.setCPUCost(hackerFile.getCPUCost());
                        port.setNote("");
                        NewFireWall firewall = new NewFireWall(computer.MyComputerHandler);
                        firewall.loadHackerFile(NewFireWall.createNoneFirewall());
                        firewall.setParentPort(port);
                        port.setFireWall(firewall);
                        port.setDummy(false);
                        port.setOn(true);
                        port.setMaliciousTarget(computer.ip);
                        HashMap script = hackerFile.getContent();
                        Program newProgram = null;

                        if (portType == Port.ATTACK) {
                            if (computer.defaultAttack == 0) {
                                computer.defaultAttack = resolvedPort;
                            }
                            newProgram = new AttackProgram(computer, computer.MyComputerHandler, port, computer.Choices, computer.MyMakeBounty);
                        } else if (portType == Port.SHIPPING) {
                            if (computer.defaultShipping == 0) {
                                computer.defaultShipping = resolvedPort;
                            }
                            newProgram = new ShippingProgram(computer, computer.MyComputerHandler, port);
                        } else if (portType == Port.BANKING) {
                            if (computer.defaultBank == 0) {
                                computer.defaultBank = resolvedPort;
                            }
                            newProgram = new Banking(computer, computer.MyComputerHandler, port);
                        } else if (portType == Port.FTP) {
                            if (computer.defaultFTP == 0) {
                                computer.defaultFTP = resolvedPort;
                            }
                            newProgram = new FTPProgram(computer, computer.MyComputerHandler, computer.MyFileSystem, port);
                        } else if (portType == Port.HTTP) {
                            if (computer.defaultHTTP == 0) {
                                computer.defaultHTTP = resolvedPort;
                            }
                            computer.adRevenueTarget = computer.ip;
                            computer.storeRevenueTarget = computer.ip;
                            newProgram = new HTTPProgram(computer, computer.MyComputerHandler);
                        }

                        if (newProgram != null) {
                            newProgram.installScript(script);
                            port.setProgram(newProgram);
                        }
                        computer.Ports.put(Integer.valueOf(resolvedPort), port);
                    }
                } else {
                    computer.addMessage(MessageHandler.CPU_TOO_HIGH);
                }
            } else {
                computer.addMessage(MessageHandler.APPLICATION_NOT_FOUND);
            }

            refreshPorts(computer);
        } else {
            computer.systemChange = true;
            computer.addMessage(MessageHandler.MAX_PROGRAMS_REACHED);
        }
    }

    private void handleFetchPorts(Computer computer) {
        PacketPort[] packetPorts = new PacketPort[computer.Ports.size()];
        Iterator portIterator = computer.Ports.entrySet().iterator();
        int index = 0;
        while (portIterator.hasNext()) {
            Port tempPort = (Port) (((Map.Entry) portIterator.next()).getValue());
            packetPorts[index] = tempPort.getPacketPort();
            index++;
        }
        computer.PA.setPacketPorts(packetPorts);
        computer.systemChange = true;
    }

    private Port findPort(Computer computer, int portNumber) {
        Iterator portIterator = computer.Ports.entrySet().iterator();
        while (portIterator.hasNext()) {
            Port tempPort = (Port) (((Map.Entry) portIterator.next()).getValue());
            if (tempPort.getNumber() == portNumber) {
                return tempPort;
            }
        }
        return null;
    }

    private void refreshPorts(Computer computer) {
        computer.MyComputerHandler.addData(new ApplicationData("fetchports", null, 0, computer.ip), computer.ip);
    }
}
