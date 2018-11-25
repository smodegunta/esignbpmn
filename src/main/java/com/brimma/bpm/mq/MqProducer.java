package com.brimma.bpm.mq;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

/**
 * Reusable producer that produces messages to Active Mq in the mentioned queue
 */
@Component
public class MqProducer {
    @Autowired
    private JmsTemplate template;

    @Autowired
    private RetryTemplate retryTemplate;

    public void send(String queue, String message){
        retryTemplate.execute(callback -> {
            template.convertAndSend(queue, message);
            return null;
        });
    }
}
