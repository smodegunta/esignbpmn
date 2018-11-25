package com.brimma.bpm.util.auth.bss;

import com.brimma.bpm.vo.bss.auth.ResponseAuth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;

/**
 * BSS Auth/api handler that takes care of invoking the api and renewing the access token
 */
@Component("bssAuthClientManager")
public class BSSAuthClientManager implements AuthClientManager{
    @Autowired
    private RestTemplate restTemplate;
    private ResponseAuth tokenData;
    private TokenExpirationHandler<AuthClientManager> expirationHandler;
    @Value("${bss.user}") private String username;
    @Value("${bss.pass}") private String password;
    @Value("${bss.authUrl}") private String authUrl;

    /**
     * Setup Auth manager with the proper deafault error handler that renews the token
     */
    @PostConstruct
    public void setup() {
        exchangeCredentialsForToken(username, password, authUrl, new TokenExpirationHandler<AuthClientManager>() {
            @Override
            public String handle(String oldToken, AuthClientManager authClientManager) {
                exchangeCredentialsForToken(username, password, authUrl);
                return tokenData.getAccessToken();
            }
        });
    }

    /**
     * exchange the credentials and get the access token
     * @param username
     * @param password
     * @param authUrl
     */
    @Override
    public void exchangeCredentialsForToken(String username, String password, String authUrl) {
        this.username = username;
        this.password = password;
        this.authUrl = authUrl;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<String>("{\"username\":\""+username+"\",\"password\":\""+password+"\"}", headers);
        ResponseEntity<ResponseAuth> response = restTemplate.exchange(authUrl, HttpMethod.POST, entity, ResponseAuth.class);
        if(response.getStatusCode()!= HttpStatus.OK){
            throw new RestClientException("Got status :: " + response.getStatusCode());
        }
        tokenData = response.getBody();
    }

    /**
     * exchange credentials for token with expiration handler
     * @param username
     * @param password
     * @param authUrl
     * @param expirationHandler
     */
    @Override
    public void exchangeCredentialsForToken(String username, String password, String authUrl, TokenExpirationHandler expirationHandler) {
        this.expirationHandler = expirationHandler;
        exchangeCredentialsForToken(username, password, authUrl);
    }

    //TODO - Need to refactor the code

    /**
     * Add the bearer token in the request header and invoke BSS API for notifying the events and Disclosures
     * @param url
     * @param method
     * @param payloadObj
     * @param responseType
     * @param <T>
     * @param <R>
     * @return
     */
    @Override
    public <T, R> R invoke(String url, HttpMethod method, T payloadObj, Class<R> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", tokenData.getTokenType()+" "+tokenData.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<T> entity = new HttpEntity<T>(payloadObj, headers);
        ResponseEntity<R> respone = null;
        try {
            respone = restTemplate.exchange(url, HttpMethod.POST, entity, responseType);
        }catch (RestClientException ex){
            ex.printStackTrace();
            expirationHandler.handle(tokenData.getAccessToken(), this);
            throw ex;
        }
        if (respone.getStatusCode() != HttpStatus.OK) {
            throw new RestClientException("Got status :: " + respone.getStatusCode());
        }
        return respone.getBody();
    }

    @Override
    public void addTokenExpirationHandler(TokenExpirationHandler handler) {
        this.expirationHandler = handler;
    }
}
