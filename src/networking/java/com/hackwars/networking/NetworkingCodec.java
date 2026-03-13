package com.hackwars.networking;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hackwars.networking.v1.NetworkEnvelope;
import com.hackwars.networking.v1.PingRequest;

public final class NetworkingCodec {
    private NetworkingCodec() {
    }

    public static byte[] encode(NetworkEnvelope envelope) {
        return envelope.toByteArray();
    }

    public static NetworkEnvelope decode(byte[] payload) throws InvalidProtocolBufferException {
        return NetworkEnvelope.parseFrom(payload);
    }

    public static NetworkEnvelope newPingRequest(String clientId, String messageId, long unixTimeMs) {
        return NetworkEnvelope.newBuilder()
                .setMessageId(messageId)
                .setSentUnixMs(unixTimeMs)
                .setPingRequest(PingRequest.newBuilder().setClientId(clientId).build())
                .build();
    }
}
