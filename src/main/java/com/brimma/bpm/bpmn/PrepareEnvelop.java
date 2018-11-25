package com.brimma.bpm.bpmn;

import com.brimma.bpm.docusign.DocusignUploadComponent;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;

public class PrepareEnvelop implements JavaDelegate {
    @Autowired
    private DocusignUploadComponent uploadComponent;

    public void execute(DelegateExecution delegateExecution) throws Exception {
        String eventMsg = (String) delegateExecution.getVariable("message");
        uploadComponent.createEnvelop(eventMsg, delegateExecution);
    }
}
