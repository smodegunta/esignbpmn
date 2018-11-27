package com.brimma.bpm;

import com.brimma.bpm.docusign.callback.UpdateSignStatusTransformer;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.variable.Variables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;

@RestController
public class DocusignHookController {
    @Autowired
    private UpdateSignStatusTransformer transformer;

    @Autowired
    private RuntimeService runtimeService;

    @PostMapping(value = "esign/status", consumes = MediaType.TEXT_XML)
    public String updateDocusignInterimStatus(@RequestBody String body) throws Exception {
        Map<String, Object> vars = new HashMap<>();
        String result = transformer.transform(body, loan -> vars.put("loanData", Variables.objectValue(loan).serializationDataFormat(Variables.SerializationDataFormats.JSON).create()));
        vars.put("updateMessage", result.getBytes());
        ProcessInstance instance = runtimeService.startProcessInstanceByMessage("borrowersSigned", "borrowersSigned", vars);
        return instance.getProcessInstanceId();
    }
}
