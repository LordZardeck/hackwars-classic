package game;

import assignments.LoginFailedAssignment;
import assignments.LoginSuccessAssignment;
import game.computer.persistence.ComputerSnapshot;
import game.computer.persistence.XmlComputerPersistence;
import game.computer.session.ComputerSessionService;
import game.computer.session.RemoteFunctionPackResult;

class ComputerLoadCoordinator {
    private final ComputerSessionService sessionService;
    private final XmlComputerPersistence xmlComputerPersistence;
    private final LegacyComputerPersistenceSupport persistenceSupport;
    private final ComputerPostLoadBootstrap postLoadBootstrap;

    ComputerLoadCoordinator(
        ComputerSessionService sessionService,
        XmlComputerPersistence xmlComputerPersistence,
        LegacyComputerPersistenceSupport persistenceSupport
    ) {
        this(sessionService, xmlComputerPersistence, persistenceSupport, new ComputerPostLoadBootstrap());
    }

    ComputerLoadCoordinator(
        ComputerSessionService sessionService,
        XmlComputerPersistence xmlComputerPersistence,
        LegacyComputerPersistenceSupport persistenceSupport,
        ComputerPostLoadBootstrap postLoadBootstrap
    ) {
        this.sessionService = sessionService;
        this.xmlComputerPersistence = xmlComputerPersistence;
        this.persistenceSupport = persistenceSupport;
        this.postLoadBootstrap = postLoadBootstrap;
    }

    void execute(Computer computer) {
        try {
            authenticatePendingConnection(computer);

            boolean activeLoad = computer.loadRequester != null && !computer.loadRequester.equals("");
            if (!activeLoad) {
                computer.loggedIn = true;
                computer.logInTime = computer.MyTime.getCurrentTime();
            }

            computer.upgradedAccount = false;
            computer.inactive = false;
            computer.MAX_OPS = Computer.FREE_MAX_OPS;
            computer.FILE_SIZE_LIMIT = Computer.FREE_FILE_SIZE_LIMIT;

            RemoteFunctionPackResult functionPackResult = sessionService.requestFunctionPacks(computer.ip);
            computer.upgradedAccount = functionPackResult.getUpgradedAccount();
            computer.inactive = functionPackResult.getInactive();
            computer.MAX_OPS = functionPackResult.getMaxOps();
            computer.FILE_SIZE_LIMIT = functionPackResult.getFileSizeLimit();

            String xml = sessionService.loadLocalSaveXml(computer.ip, activeLoad);
            ComputerSnapshot snapshot = xmlComputerPersistence.parse(xml);
            persistenceSupport.restoreSnapshot(computer, snapshot);
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null || message.equals("")) {
                message = "Unable to load local account data for ip=" + computer.ip + ".";
            }
            computer.errorMessage = message;
            e.printStackTrace();
            computer.LOAD_FAILURE = true;
        }

        computer.Loaded = true;
        computer.Loading = false;
        postLoadBootstrap.apply(computer);
    }

    private void authenticatePendingConnection(Computer computer) {
        if (computer.connectionID == -1) {
            return;
        }

        if (!computer.checkLogin()) {
            Object[] payload = new Object[]{new LoginFailedAssignment(0), Integer.valueOf(computer.connectionID)};
            computer.MyHackerServer.addData(payload);
            computer.connectionID = -1;
            return;
        }

        Object[] randomKey = computer.MyHackerServer.getRandomKey(computer.ip, computer.getClientHash(), computer.publicKey);
        LoginSuccessAssignment loginSuccessAssignment = new LoginSuccessAssignment(0, computer.ip, (String) randomKey[0], computer.isNPC());
        loginSuccessAssignment.setPublicKey((byte[]) randomKey[1]);
        computer.MyHackerServer.addData(new Object[]{loginSuccessAssignment, Integer.valueOf(computer.connectionID)});
    }
}
