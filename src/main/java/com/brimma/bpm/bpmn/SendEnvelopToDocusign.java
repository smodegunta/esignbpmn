package com.brimma.bpm.bpmn;

import com.brimma.bpm.docusign.DocusignUploadComponent;
import com.brimma.bpm.handler.Handler;
import com.docusign.esign.model.EnvelopeDefinition;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class SendEnvelopToDocusign implements JavaDelegate {
    @Autowired
    private DocusignUploadComponent uploadComponent;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        EnvelopeDefinition envDefinition = (EnvelopeDefinition) execution.getVariable("envelopDefinition");
        execution.setVariable("loanData", execution.getVariable("loanData"));
        execution.setVariable("envelopId", uploadComponent.sendEnvelop(envDefinition));
    }
}
