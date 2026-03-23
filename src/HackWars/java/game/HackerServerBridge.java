package game;

public interface HackerServerBridge {
    String getServerID();

    void removeRandomKey(String ip);

    Object[] getRandomKey(String ip, String clientHash, byte[] publicKey);

    String resolveEncryptedIp(String token);

    void addData(Object o);
}
