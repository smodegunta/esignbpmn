package com.brimma.bpm.mq;

import com.brimma.bpm.docusign.EsignHandlerConstants;
import com.brimma.bpm.util.DisclosureUtil;
import com.brimma.bpm.util.auth.bss.AuthClientManager;
import com.brimma.bpm.vo.Borrower;
import com.brimma.bpm.vo.Loan;
import com.brimma.bpm.vo.bss.BSSAddDocPackageReq;
import com.brimma.bpm.vo.bss.SinerEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import javax.jms.Message;
import java.util.Optional;

/**
 * This is the active mq consumer that listens to the reply queue from which it receives the update status message
 * from SDK Wrapper after interim and final status update for the signed disclosure
 */
@Component
public class EncompassSignStatus {
    private static final Logger LOG = LoggerFactory.getLogger(EncompassSignStatus.class);

    @Autowired
    @Qualifier("bssAuthClientManager")
    private AuthClientManager manager;

    @Value("${bss.addDocPackageDetailUrl}")
    private String addPkgDetUrl;

    /**
     * The queue listener that does send the events to BSS uon recieving successful signed document updates to encompass
     * @param message
     * @throws Throwable
     */
    @JmsListener(destination = "${encompass.out.queue.disclosure.status}", concurrency = "10", subscription = "encompass", id = "encompass.update", containerFactory = "jmsListenerContainerFactory")
    public void onMessage(Message message) throws Throwable {
        LOG.debug("Consuming the disclosure status messages.");
        ObjectMapper mapper = new ObjectMapper();
        Loan loanObject = mapper.readValue(((ActiveMQTextMessage) message).getText(), Loan.class);
        if (loanObject.getId() == null || loanObject.getId().isEmpty())
            throw new EsignHandlerException("loan Id missing");
        BSSAddDocPackageReq docPackageReq = null;
        if ("INTERIM_STATUS_UPDATE".equalsIgnoreCase(loanObject.getOperation()) && loanObject.getBorrowers() != null) {
            Optional<Borrower> borrowerOpt = loanObject.getBorrowers().stream().filter(borrower -> "COMPLETED".equalsIgnoreCase(borrower.getSigned())).findFirst();
            if (borrowerOpt.isPresent()) {
                docPackageReq = new BSSAddDocPackageReq();
                docPackageReq.setLoanId(loanObject.getLoanNumber());
                docPackageReq.setEventToken(SinerEvent.EsignSignAndSubmit);
                docPackageReq.setBorrowerId(borrowerOpt.get().getBssId());
                docPackageReq.setDocumentPackageId(DisclosureUtil.getDisclosureForBSS(loanObject.getDisclosureType()));
            } else {
                throw new RuntimeException("No borrower is found to be esigned");
            }
        } else if ("FINAL_STATUS_UPDATE".equalsIgnoreCase(loanObject.getOperation())) {
            docPackageReq = new BSSAddDocPackageReq();
            docPackageReq.setLoanId(loanObject.getLoanNumber());
            docPackageReq.setEventToken(SinerEvent.EsignSignAndSubmitAllComplete);
            docPackageReq.setDocumentPackageId(loanObject.getDisclosureType());
        } else {
            throw new RuntimeException("the status update type :" + loanObject.getOperation() + " is not known");
        }
        if (EsignHandlerConstants.FAILED.equalsIgnoreCase(loanObject.getReason())) {
            LOG.error("Disclosure status update failed with Reason " + loanObject.getStatus());
        } else {
            String response = manager.invoke(
                    addPkgDetUrl,
                    HttpMethod.POST,
                    docPackageReq,
                    String.class
            );
        }
        message.acknowledge();
    }
}
