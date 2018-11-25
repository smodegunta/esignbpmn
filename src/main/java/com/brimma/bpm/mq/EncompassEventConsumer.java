package com.brimma.bpm.mq;

import org.apache.activemq.command.ActiveMQTextMessage;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RuntimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import javax.jms.Message;

/**
 * Disclosure events are consued from activemq
 *
 */
@Component
public class EncompassEventConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(EncompassEventConsumer.class);

    @Autowired
    private RuntimeService runtimeService;

    @JmsListener(destination = "${encompass.event.queue.disclosure}", subscription = "encompass", id = "encompass.event", containerFactory = "jmsListenerContainerFactory")
    public void onMessage(Message message) throws Exception {
        LOG.debug("Got the disclosure event from encompass server");
        runtimeService.startProcessInstanceByMessage("disclosureEvent", ((ActiveMQTextMessage) message).getText());
        message.acknowledge();
    }
}
