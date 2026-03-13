package Game;

public interface HackerServerBridge {
    String getServerID();

    void removeRandomKey(String ip);

    Object[] getRandomKey(String ip, String clientHash, byte[] publicKey);

    void addData(Object o);
}
