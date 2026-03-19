package game;

import assignments.PacketNetwork;

class ComputerPostLoadBootstrap {
    void apply(Computer computer) {
        if (computer.type != Computer.NPC) {
            try {
                computer.RawComputerHandler.incrementPlayers();
                Network.getInstance(computer.MyComputerHandler).addToNetwork(Network.ROOT_NETWORK, computer.ip);
                PacketNetwork packetNetwork = Network.getInstance(computer.MyComputerHandler).getNetworkInformation(Network.ROOT_NETWORK);
                computer.store = packetNetwork.getStoreIP();
                computer.PA.setPacketNetwork(packetNetwork);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        computer.systemChange = true;
        computer.healthChange = true;
        computer.sendPreferences = true;
        computer.MyEquipmentSheet.degradeEquipment();
        computer.MyComputerHandler.addData(new ApplicationData("requestequipment", Integer.valueOf(13), 0, computer.ip), computer.ip);
    }
}
