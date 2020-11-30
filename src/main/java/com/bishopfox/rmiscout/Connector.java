package com.bishopfox.rmiscout;

import java.io.Serializable;

public interface Connector {
    public void exploit(String payloadName, String command);
    public boolean execute(Serializable[] payloads, boolean preserveStrings);
    public void cleanup();
    public void invoke(String[] params);
    public void checkIfPresent();
}
