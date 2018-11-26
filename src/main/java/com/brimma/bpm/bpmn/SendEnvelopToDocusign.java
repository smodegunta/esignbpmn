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
import org.springframework.stereotype.Component;

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

    @Value("${CONFIG}")
    private String tmpDir;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        byte[] envelopDefinition = (byte[]) execution.getVariable("message");
        DocumentContext context = JsonPath.parse(new String(envelopDefinition));
        addDocs(new File(tmpDir, context.read("$.loanId")), context );
        execution.setVariable("envelopId", uploadComponent.createAndSendEnvelop(context.jsonString(), execution));
    }

    private void addDocs(File file, DocumentContext context) {
        List<Map<String, Object>> docs = (List<Map<String, Object>>) context.read("$.documents", List.class).stream().map(doc -> {
            Map<String, Object> document = (Map<String, Object>) doc;
            String base64file = null;
            try {
                base64file = Base64.getEncoder().encodeToString(Files.readAllBytes(new File(file, ((String) document.get("title")).replaceAll("\\/", "")).toPath()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            Map<String, Object> res = new HashMap<>();
            res.put("title", document.get("title"));
            res.put("base64String", base64file);
            res.put("fileExtension", "pdf");
            return res;
        }).collect(Collectors.toList());
        context.put("$", "documents", docs);
    }
}
