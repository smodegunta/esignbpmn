package com.brimma.bpm.bpmn;

import com.brimma.bpm.docusign.DocusignUploadComponent;
import com.brimma.bpm.handler.Handler;
import com.brimma.bpm.vo.brimma.Loan;
import com.docusign.esign.model.EnvelopeDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SendEnvelopToDocusign implements JavaDelegate {
    @Autowired
    private DocusignUploadComponent uploadComponent;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${esign-handler-endpoint}")
    private String esignInitiateDisclosureEndpoint;

    @Value("${CONFIG}")
    private String tmpDir;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        byte[] envelopDefinition = (byte[]) execution.getVariable("message");
        DocumentContext context = JsonPath.parse(new String(envelopDefinition));
        addDocs(new File(tmpDir, context.read("$.loanId")), context );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<String>(context.jsonString(), headers);
        ResponseEntity<String> responseEntity = restTemplate.exchange(esignInitiateDisclosureEndpoint, HttpMethod.POST, entity, String.class);
        execution.setVariable("envelopSent", responseEntity.getStatusCodeValue());
    }

    private void addDocs(File file, DocumentContext context) {
        context.read("$.documents", List.class).forEach(doc -> {
            Map<String, Object> document = (Map<String, Object>) doc;
            String base64file = null;
            try {
                base64file = Base64.getEncoder().encodeToString(Files.readAllBytes(new File(file, ((String) document.get("title")).replaceAll("\\/", "")).toPath()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            document.put("base64String", base64file);
        });
    }
}
