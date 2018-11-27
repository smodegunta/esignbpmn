package com.brimma.bpm.bpmn;

import com.brimma.bpm.mq.MqProducer;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.brimma.bpm.docusign.callback.UpdateSignStatusTransformer;

@Component
public class UpdateEncompass implements JavaDelegate {

    @Autowired
    private MqProducer producer;

    @Value("${encompass.out.queue.disclosure.interim}")
    private String queue;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String message = (String) execution.getVariable("updateMessage");
        DocumentContext context = JsonPath.parse(message);
        producer.send(queue, message);
        execution.setVariable("updateMessage", "***removed***");
    }
}
