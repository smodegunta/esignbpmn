package com.brimma.bpm.mq;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.camunda.bpm.engine.RuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import javax.jms.Message;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Disclosure events are consued from activemq
 */
@Component
public class EncompassEventConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(EncompassEventConsumer.class);

    @Value("${CONFIG}")
    private String tmpDir;

    @Autowired
    private RuntimeService runtimeService;

    @JmsListener(destination = "${encompass.event.queue.disclosure}", subscription = "encompass", id = "encompass.event", containerFactory = "jmsListenerContainerFactory")
    public void onMessage(Message message) throws Exception {
        LOG.debug("Got the disclosure event from encompass server");
        Map<String, Object> vars = new HashMap<>();
        String msg = ((ActiveMQTextMessage) message).getText();
        DocumentContext context = JsonPath.parse(msg);
        String loanId = context.read("$.loanId");
        File docLocation = new File(tmpDir, loanId);
        if (!docLocation.exists()) docLocation.mkdirs();
        addDocLocations(docLocation, context);
        vars.put("message", context.jsonString().getBytes());
        runtimeService.startProcessInstanceByMessage("disclosureEvent", "disclosureEvent", vars);
        message.acknowledge();
    }

    private void addDocLocations(File docLocation, DocumentContext context) {
        List<Map<String, Object>> files = context.read("$.documents", List.class);
        context.delete("$.documents[?(@.signatureType != 'eSignable')]");
        files.forEach(file -> {
            try {
                File doc = new File(docLocation, ((String) file.get("title")).replaceAll("\\/", ""));
                if(doc.exists()) Files.delete(doc.toPath());
                Files.write(doc.toPath(), Base64.getDecoder().decode(((String) file.get("base64String")).getBytes()), StandardOpenOption.CREATE_NEW);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        context.delete("$.documents.*.base64String");
    }
}
