package com.hackwars.integration;

import com.hackwars.client.DeterministicClientAuthAccount;
import com.hackwars.data.model.JsonProfileWrite;
import com.hackwars.data.model.PersistedProfileSave;
import game.computer.persistence.ComputerSnapshot;
import game.computer.persistence.ComputerWebsiteSnapshot;
import game.computer.persistence.ComputerStatsSnapshot;
import game.computer.persistence.JsonComputerPersistence;
import game.computer.persistence.JsonComputerPersistenceSupport;
import game.computer.persistence.XmlComputerPersistence;
import game.LegacyComputerPersistenceSupport;
import util.PlayFabTokenVerifier;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

public final class SeedScenario {
    private final DeterministicClientAuthAccount account;
    private final String playerIp;
    private final String playerName;
    private final String offlineEncryptedIp;
    private final String websiteTitle;
    private final String websiteBody;
    private final PersistedProfileSave saveProfile;

    public SeedScenario(
        DeterministicClientAuthAccount account,
        String playerIp,
        String playerName,
        String offlineEncryptedIp,
        String websiteTitle,
        String websiteBody,
        PersistedProfileSave saveProfile
    ) {
        this.account = account;
        this.playerIp = playerIp;
        this.playerName = playerName;
        this.offlineEncryptedIp = offlineEncryptedIp;
        this.websiteTitle = websiteTitle;
        this.websiteBody = websiteBody;
        this.saveProfile = saveProfile;
    }

    public static SeedScenario defaultScenario() {
        DeterministicClientAuthAccount account = new DeterministicClientAuthAccount(
            "localuser",
            "password1234",
            "PF-LOCALUSER",
            "SESSION-LOCALUSER"
        );
        String title = "Integration Hub";
        String body = IntegrationFixture.websiteBody();
        ComputerSnapshot snapshot = new ComputerSnapshot(
            "100.10.1.42",
            "Integration Tester",
            0,
            0,
            "password1234",
            0,
            0,
            0,
            null,
            0.0f,
            1.0f,
            0.0f,
            0.0f,
            0,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            0,
            0L,
            250.0f,
            500.0f,
            3,
            4,
            6,
            8,
            9,
            new ComputerStatsSnapshot(),
            Arrays.asList(0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            Arrays.asList(0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            "<ports>\n</ports>\n",
            "<watches>\n</watches>\n",
            "<files>\n</files>\n",
            new ComputerWebsiteSnapshot(0, "", null, title, body),
            "<equipment>\n</equipment>\n<equipment>\n</equipment>\n<equipment>\n</equipment>\n",
            Collections.singletonMap("network", "true")
        );
        XmlComputerPersistence xmlPersistence = new XmlComputerPersistence();
        String saveXml = xmlPersistence.serialize(snapshot);
        JsonProfileWrite jsonWrite = new JsonComputerPersistenceSupport(
            new JsonComputerPersistence(),
            new LegacyComputerPersistenceSupport(xmlPersistence)
        ).exportSnapshot(snapshot, Collections.emptyMap(), LocalDateTime.now());
        PersistedProfileSave saveProfile = new PersistedProfileSave(
            1,
            "100.10.1.42",
            saveXml,
            jsonWrite.getManifestJson(),
            jsonWrite.getVersion(),
            jsonWrite.getMigratedAt(),
            jsonWrite.getBlobs()
        );
        return new SeedScenario(account, "100.10.1.42", "localuser", "LOCAL-IP", title, body, saveProfile);
    }

    public DeterministicClientAuthAccount getAccount() {
        return account;
    }

    public String getPlayerIp() {
        return playerIp;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getOfflineEncryptedIp() {
        return offlineEncryptedIp;
    }

    public String getWebsiteTitle() {
        return websiteTitle;
    }

    public String getWebsiteBody() {
        return websiteBody;
    }

    public String getSaveXml() {
        return saveProfile.getLegacyXml();
    }

    public PersistedProfileSave getSaveProfile() {
        return saveProfile;
    }

    public PlayFabTokenVerifier.AuthResult authResultForSession(String sessionTicket) {
        if (!account.getSessionTicket().equals(sessionTicket)) {
            return null;
        }
        return new PlayFabTokenVerifier.AuthResult(account.getPlayFabId(), playerName, playerIp);
    }
}
