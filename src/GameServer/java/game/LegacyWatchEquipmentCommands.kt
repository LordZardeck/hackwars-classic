package game;

import assignments.PacketWatch;
import com.hackwars.game.program.Program;
import com.hackwars.game.program.WatchProgram;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Legacy watch/equipment command extraction from {@link Computer#processQueuedItem}.
 */
public class LegacyWatchEquipmentCommands implements LegacyApplicationDataHandler {
    @Override
    public boolean dispatch(Computer computer, ApplicationData applicationData, int resolvedPort) {
        String function = applicationData.getFunction();

        // changes watch type
        if (function.equals("changewatchtype")) {
            int target_watch = (Integer) ((Integer[]) applicationData.getParameters())[0];
            int new_type = (Integer) ((Integer[]) applicationData.getParameters())[1];

            if (target_watch < computer.MyWatchHandler.getWatches().size()) {
                Watch MyWatch = (Watch) computer.MyWatchHandler.getWatch(target_watch);
                MyWatch.setType(new_type);
                queueFetchWatches(computer);
            }
            return true;
        } else

        // INSTALL A WATCH.
        if (function.equals("installwatch")) {
            Object Parameter[] = (Object[]) applicationData.getParameters();
            String path = (String) ((Object[]) applicationData.getParameters())[0];
            String name = (String) ((Object[]) applicationData.getParameters())[1];
            int type = (Integer) ((Object[]) applicationData.getParameters())[2];

            HackerFile HF = computer.getFileSystem().getFile(path, name);

            if (computer.MyWatchHandler.getWatches().size() < 21) {
                if (HF != null && HF.getType() == HF.WATCH_COMPILED) {

                    float cpuCheck = computer.getCPULoad() + HF.getCPUCost();
                    float maxCPU = Computer.CPU_CHART[computer.cputype] + computer.MyEquipmentSheet.getCPUBonus();
                    if (cpuCheck <= maxCPU) {

                        HF.setQuantity(HF.getQuantity() - 1);
                        if (HF.getQuantity() <= 0) {
                            computer.getFileSystem().deleteFile(path, name);
                        }


                        Watch twatch = new Watch(computer);
                        twatch.setType(type);


                        twatch.setSearchFireWall(0);
                        twatch.setCPUCost(HF.getCPUCost());
                        // set the watch note to be the filename by default.
                        twatch.setNote(name);
                        twatch.setOn(false);
                        twatch.setQuantity(0.0f);

                        if (twatch.getType() == Watch.PETTY_CASH) {//Make sure initial quantities are correct.
                            twatch.setInitialQuantity(computer.getPettyCash());
                        } else if (twatch.getType() == Watch.HEALTH) {
                            twatch.setInitialQuantity(100.0f);
                        }

                        twatch.setPort(applicationData.getPort());
                        HashMap Script = HF.getContent();
                        Program MyProgram = new WatchProgram(computer, computer.MyComputerHandler, twatch);
                        MyProgram.setComputerHandler(computer.MyComputerHandler);
                        MyProgram.installScript(Script);
                        twatch.setProgram(MyProgram);
                        computer.MyWatchHandler.addWatch(twatch);
                    } else
                        computer.addMessage(MessageHandler.CPU_TOO_HIGH);
                } else
                    computer.addMessage(MessageHandler.FILE_NOT_FOUND);
            } else {
                computer.addMessage(MessageHandler.MAX_WATCHES_REACHED);
            }

            queueFetchWatches(computer);
            return true;
        } else

        // REQUEST AN EQUIPMENT UPDATE
        if (function.equals("requestequipment")) {//Request a directory listing.
            Object O[] = computer.MyFileSystem.getEquipment("");//Update the value to take into account ID.
            Object Temp[] = new Object[O.length + 1];
            Temp[0] = applicationData.getParameters();
            for (int i = 0; i < O.length; i++) {
                if (O[i] != null)
                    computer.MyEquipmentSheet.describeCard((HackerFile) O[i]);//Testing outputting a description of the bonus.
                Temp[i + 1] = O[i];
            }
            O = Temp;
            computer.PA.setDirectory(O);

            O = computer.MyEquipmentSheet.getEquipment();//Update the value to take into account ID.
            Temp = new Object[O.length + 1];
            Temp[0] = applicationData.getParameters();
            for (int i = 0; i < O.length; i++) {
                if (O[i] != null)
                    computer.MyEquipmentSheet.describeCard((HackerFile) O[i]);//Testing outputting a description of the bonus.
                Temp[i + 1] = O[i];
            }
            O = Temp;

            computer.PA.setSecondaryDirectory(O);
            computer.systemChange = true;
            return true;
        } else

        // Install equipment.
        if (function.equals("installequipment")) {//Request a directory listing.
            int position = (Integer) ((Object[]) applicationData.getParameters())[0];
            String name = (String) ((Object[]) applicationData.getParameters())[1];
            computer.MyEquipmentSheet.equip(position, name);

            Object O[] = computer.MyFileSystem.getEquipment("");//Update the value to take into account ID.
            Object Temp[] = new Object[O.length + 1];
            Temp[0] = (Integer) ((Object[]) applicationData.getParameters())[2];
            for (int i = 0; i < O.length; i++)
                Temp[i + 1] = O[i];
            O = Temp;
            computer.PA.setDirectory(O);

            O = computer.MyEquipmentSheet.getEquipment();//Update the value to take into account ID.
            Temp = new Object[O.length + 1];
            Temp[0] = (Integer) ((Object[]) applicationData.getParameters())[2];
            for (int i = 0; i < O.length; i++)
                Temp[i + 1] = O[i];
            O = Temp;

            computer.PA.setSecondaryDirectory(O);
            computer.systemChange = true;
            return true;
        } else

        // Repair equipment.
        if (function.equals("repairequipment")) {//Request a directory listing.

            int position = (Integer) ((Object[]) applicationData.getParameters())[0];
            String name = (String) ((Object[]) applicationData.getParameters())[1];

            if (position != -1)
                computer.MyEquipmentSheet.repair(position);
            else {
                HackerFile Equipment = computer.MyFileSystem.getFile("", name);
                if (Equipment != null)
                    computer.MyEquipmentSheet.repair(Equipment);
            }

            computer.MyComputerHandler.addData(new ApplicationData("requestequipment", new Integer(13), 0, computer.ip), computer.ip);
            return true;
        } else

        // RETURNS AN ARRAY OF THE WATCHES THAT ARE CURRENTLY INSTALLED ON THIS PROGRAM.
        if (function.equals("fetchwatches")) {
            PacketWatch PacketWatches[] = new PacketWatch[computer.MyWatchHandler.getWatches().size()];
            Iterator WatchIterator = computer.MyWatchHandler.getWatches().iterator();
            int ii = 0;
            while (WatchIterator.hasNext()) {
                Watch TempWatch = (Watch) WatchIterator.next();
                PacketWatches[ii] = TempWatch.getPacketWatch();
                ii++;
            }
            computer.PA.setPacketWatches(PacketWatches);
            computer.systemChange = true;
            return true;
        } else

        // SET THE QUANTITY ASSOCIATED WITH THE WATCH.
        if (function.equals("setwatchquantity")) {
            int watchID = (Integer) ((Object[]) applicationData.getParameters())[0];
            float quantity = (Float) ((Object[]) applicationData.getParameters())[1];
            Watch twatch = (Watch) computer.MyWatchHandler.getWatches().get(watchID);
            if (twatch != null) {
                twatch.setQuantity(quantity);
            }
            queueFetchWatches(computer);
            return true;
        } else

        // SeT WHETHER THE GIVEN WATCH IS ON OR OFF.
        if (function.equals("setwatchonoff")) {
            int watchID = (Integer) ((Object[]) applicationData.getParameters())[0];
            boolean state = (Boolean) ((Object[]) applicationData.getParameters())[1];
            Watch twatch = (Watch) computer.MyWatchHandler.getWatches().get(watchID);
            if (twatch != null) {
                float cpuCheck = computer.getCPULoad() + twatch.getActualCPUCost();
                float maxCPU = Computer.CPU_CHART[computer.cputype] + computer.MyEquipmentSheet.getCPUBonus();
                if (state) {
                    if (computer.MyWatchHandler.getWatchCount() < Computer.WATCH_CHART[computer.memorytype] + computer.MyEquipmentSheet.getWatchBonus()) {
                        if (cpuCheck <= maxCPU) {
                            twatch.setOn(state);
                        }
                    } else {
                        computer.addMessage(MessageHandler.WATCH_ON_FAIL);
                    }
                } else {
                    if (computer.getCPULoad() <= maxCPU)
                        twatch.setOn(state);
                }
            }
            queueFetchWatches(computer);
            return true;
        } else

        // SET THE FIRE WALL THAT SHOULD BE SEARCHED FOR BY THE WATCH.
        if (function.equals("setwatchsearchfirewall")) {
            int watchID = (Integer) ((Object[]) applicationData.getParameters())[0];
            Integer searchFireWall = (Integer) ((Object[]) applicationData.getParameters())[1];
            Watch twatch = (Watch) computer.MyWatchHandler.getWatches().get(watchID);
            if (twatch != null) {
                twatch.setSearchFireWall(searchFireWall);
            }
            queueFetchWatches(computer);
            return true;
        } else

        // SET THE NOTE ASSOCIATED WITH THIS WATCH.
        if (function.equals("setwatchnote")) {
            int watchID = (Integer) ((Object[]) applicationData.getParameters())[0];
            String note = (String) ((Object[]) applicationData.getParameters())[1];
            Watch twatch = (Watch) computer.MyWatchHandler.getWatches().get(watchID);
            if (twatch != null) {
                twatch.setNote(note);
            }
            queueFetchWatches(computer);
            return true;
        } else

        // DELETE A WATCH FROM THE WATCH HANDLER.
        if (function.equals("deletewatch")) {
            float maxCPU = Computer.CPU_CHART[computer.cputype] + computer.MyEquipmentSheet.getCPUBonus();
            if (computer.getCPULoad() <= maxCPU) {
                int watchID = (Integer) ((Object[]) applicationData.getParameters())[0];
                computer.MyWatchHandler.removeWatch(watchID);
                queueFetchWatches(computer);
            }
            return true;
        } else

        // SET THE PORTS BEING OBSERVED BY THIS WATCH.
        if (function.equals("setwatchobservedports")) {
            int watchID = (Integer) ((Object[]) applicationData.getParameters())[0];
            Integer ObservedPorts[] = (Integer[]) ((Object[]) applicationData.getParameters())[1];
            Watch twatch = (Watch) computer.MyWatchHandler.getWatches().get(watchID);
            if (twatch != null) {
                twatch.setObservedPorts(ObservedPorts);
            }
            queueFetchWatches(computer);
            return true;
        }

        return false;
    }

    private void queueFetchWatches(Computer computer) {
        computer.MyComputerHandler.addData(new ApplicationData("fetchwatches", null, 0, computer.ip), computer.ip);
    }
}
