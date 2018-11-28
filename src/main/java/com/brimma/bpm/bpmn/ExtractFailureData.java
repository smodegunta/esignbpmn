package com.brimma.bpm.bpmn;

import com.brimma.bpm.vo.Borrower;
import com.brimma.bpm.vo.Loan;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

import java.util.Optional;

public class ExtractFailureData implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) throws Exception {
        Loan loan = (Loan) execution.getVariableTyped("loanData").getValue();
        Optional<Borrower> borrowerOptional = loan.getBorrowers().stream().filter(signer -> !"Completed".equals(signer.getStatus())).findFirst();
        Borrower borrower = borrowerOptional.orElse(new Borrower());
        execution.setVariable("status", borrower.getStatus());
        execution.setVariable("reason", borrower.getDeclineReason());
    }
}
