package com.brimma.bpm.handler;

import com.jayway.jsonpath.DocumentContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * update the docs directly by hitting the sdk wrapper API.
 */
@Component
public class EncompassRequestHandler {
    @Autowired
    private RestTemplate template;

    public void send(String url, DocumentContext payload, HttpMethod method){
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String loanId = payload.read("$.loanId");
        HttpEntity<String> entity = new HttpEntity<String>(payload.jsonString(), headers);

        template.exchange(
                url
                , method
                , entity
                , String.class
                , loanId);
        System.out.println("Documents Successfully updated");
    }
}
