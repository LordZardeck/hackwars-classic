package com.hackwars.integration;

import assignments.RemoteFunctionCall;
import com.hackwars.state.GameState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecordingGameState extends GameState {
    private final List<RemoteFunctionCall> remoteCalls = Collections.synchronizedList(new ArrayList<RemoteFunctionCall>());
    private final List<String> requestedFunctions = Collections.synchronizedList(new ArrayList<String>());

    public void setFunction(String function) {
        requestedFunctions.add(function);
    }

    @Override
    public void addFunctionCall(RemoteFunctionCall remoteFunctionCall) {
        remoteCalls.add(remoteFunctionCall);
    }

    public void clearRemoteCalls() {
        remoteCalls.clear();
        requestedFunctions.clear();
    }

    public List<RemoteFunctionCall> getRemoteCallsSnapshot() {
        synchronized (remoteCalls) {
            return new ArrayList<RemoteFunctionCall>(remoteCalls);
        }
    }

    public List<String> getRequestedFunctionsSnapshot() {
        synchronized (requestedFunctions) {
            return new ArrayList<String>(requestedFunctions);
        }
    }

    public RemoteFunctionCall lastRemoteCall() {
        synchronized (remoteCalls) {
            return remoteCalls.isEmpty() ? null : remoteCalls.get(remoteCalls.size() - 1);
        }
    }
}
