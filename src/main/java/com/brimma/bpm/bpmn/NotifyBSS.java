package com.brimma.bpm.bpmn;

import com.brimma.bpm.docusign.DocusignUploadComponent;
import com.brimma.bpm.vo.brimma.Loan;
import com.brimma.bpm.vo.bss.BSSLoan;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;

public class NotifyBSS implements JavaDelegate {
    @Autowired
    private DocusignUploadComponent uploadComponent;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        Loan loan = execution.getVariableTyped("loanData");
        String envelopId = execution.getVariableTyped("envelopId");
        BSSLoan bssLoan = new BSSLoan();
        bssLoan.setLoanId(loan.getBssLoanNum());
        bssLoan.setDocumentPackageId(loan.getDisclosureType());
        uploadComponent.addESignHandle(bssLoan, loan.getSigners(), envelopId);
        execution.setVariable("nofitiedBss", uploadComponent.createDocPackageForBSS(bssLoan));
    }
}
