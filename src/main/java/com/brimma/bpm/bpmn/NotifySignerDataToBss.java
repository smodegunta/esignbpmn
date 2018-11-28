package com.brimma.bpm.bpmn;

import com.brimma.bpm.docusign.callback.UpdateSignStatusTransformer;
import com.brimma.bpm.util.DisclosureUtil;
import com.brimma.bpm.util.auth.bss.AuthClientManager;
import com.brimma.bpm.vo.Borrower;
import com.brimma.bpm.vo.Loan;
import com.brimma.bpm.vo.bss.BSSAddDocPackageReq;
import com.brimma.bpm.vo.bss.SinerEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class NotifySignerDataToBss implements JavaDelegate {
    private static final Logger LOG = LoggerFactory.getLogger(NotifySignerDataToBss.class);

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    @Qualifier("bssAuthClientManager")
    private AuthClientManager manager;

    @Value("${bss.addDocPackageDetailUrl}")
    private String addPkgDetUrl;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        Loan loan = (Loan) execution.getVariableTyped("loanData").getValue();
        BSSAddDocPackageReq docPackageReq = new BSSAddDocPackageReq();
        docPackageReq.setLoanId(loan.getLoanNumber());
        Optional<Borrower> borrowerOptional = loan.getBorrowers().stream().filter(signer -> "Completed".equals(signer.getStatus()) || "Declined".equals(signer.getStatus())).findFirst();
        if(borrowerOptional.isPresent()) {
            Borrower borrower = borrowerOptional.get();
                docPackageReq.setEventToken(
                        borrower.getStatus().equals("Completed")?SinerEvent.EsignSignAndSubmit:(
                            borrower.getStatus().equals("Declined")?SinerEvent.EsignDenyConsent:null
                        ));
            docPackageReq.setBorrowerId(borrower.getBssId());
            docPackageReq.setDocumentPackageId(DisclosureUtil.getDisclosureForBSS(loan.getDisclosureType()));
            String response = manager.invoke(addPkgDetUrl, HttpMethod.POST, docPackageReq, String.class);
            LOG.debug(response);
        }
    }
}
