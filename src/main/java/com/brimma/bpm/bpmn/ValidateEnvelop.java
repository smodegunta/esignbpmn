package com.brimma.bpm.bpmn;

import com.brimma.bpm.docusign.DocusignUploadComponent;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.model.dmn.instance.Variable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ValidateEnvelop implements JavaDelegate {
    @Autowired
    private DocusignUploadComponent uploadComponent;

    public void execute(DelegateExecution delegateExecution) throws Exception {
        byte[] eventMsg = (byte[]) delegateExecution.getVariable("message");
        delegateExecution.setVariable("valid", Variables.booleanValue(uploadComponent.validate(new String(eventMsg))));
    }
}
