package com.brimma.bpm.util.auth.bss;

import org.springframework.http.HttpMethod;

/**
 * BSS API handler interface
 */
public interface AuthClientManager {
    void exchangeCredentialsForToken(String username, String password, String authUrl);
    <T extends AuthClientManager> void exchangeCredentialsForToken(String username, String password, String authUrl, TokenExpirationHandler<T> expirationHandler);
    <T, R> R invoke(String url, HttpMethod method, T payloadObj, Class<R> responseType);
    void addTokenExpirationHandler(TokenExpirationHandler handler);
}
