package com.brimma.bpm.bpmn;

import com.brimma.bpm.docusign.DocusignUploadComponent;
import com.brimma.bpm.vo.brimma.Loan;
import com.brimma.bpm.vo.bss.BSSLoan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NotifyBSS implements JavaDelegate {
    @Autowired
    private DocusignUploadComponent uploadComponent;

    @Autowired
    private ObjectMapper mapper;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        Loan loan = mapper.readValue((String) execution.getVariable("loanData"), Loan.class);
        String envelopId = (String) execution.getVariable("envelopId");
        BSSLoan bssLoan = new BSSLoan();
        bssLoan.setLoanId(loan.getBssLoanNum());
        bssLoan.setDocumentPackageId(loan.getDisclosureType());
        uploadComponent.addESignHandle(bssLoan, loan.getSigners(), envelopId);
        execution.setVariable("notfiedBss", Variables.booleanValue(uploadComponent.createDocPackageForBSS(bssLoan)));
    }
}
