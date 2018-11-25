package com.brimma.bpm.util.auth.bss;

public interface TokenExpirationHandler<T extends AuthClientManager> {
    String handle(String oldToken, T authClientManager);
}
