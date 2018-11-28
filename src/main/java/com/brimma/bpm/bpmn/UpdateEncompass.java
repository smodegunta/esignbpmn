package com.brimma.bpm.bpmn;

import com.brimma.bpm.mq.MqProducer;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UpdateEncompass implements JavaDelegate {

    @Autowired
    private MqProducer producer;

    @Value("${encompass.out.queue.disclosure.interim}")
    private String queue;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        byte[] message = (byte[]) execution.getVariable("updateMessage");
        String msgStr = new String(message);
        producer.send(queue, msgStr);
        execution.setVariable("updateMessage", "***removed***");
    }
}
