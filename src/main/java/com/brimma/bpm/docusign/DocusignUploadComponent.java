package com.brimma.bpm.docusign;

import com.brimma.bpm.util.DisclosureUtil;
import com.brimma.bpm.util.auth.bss.AuthClientManager;
import com.brimma.bpm.vo.brimma.Loan;
import com.brimma.bpm.vo.brimma.Signer;
import com.brimma.bpm.vo.bss.BSSBorrower;
import com.brimma.bpm.vo.bss.BSSLoan;
import com.brimma.bpm.vo.bss.DocPackageResponse;
import com.docusign.esign.client.ApiException;
import com.docusign.esign.model.EnvelopeDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This component triggers the send envelop to docusign upon receiving the disclosure event
 */
@Component
public class DocusignUploadComponent {
    private static final Logger LOG = LoggerFactory.getLogger(DocusignUploadComponent.class);

    @Value("${endpoint.esign.host}")
    private String endpoint_host;

    @Value("${endpoint.esign.path}")
    private String endpoint_path;

    @Value("${bss.createDocPackageUrl}")
    private String bssCreateDocPackageUrl;

    private DocusignComponent eSignComponent;

    private AuthClientManager manager;

    private RetryTemplate retryTemplate;

    @Autowired
    public DocusignUploadComponent(DocusignComponent eSignComponent, @Qualifier("bssAuthClientManager") AuthClientManager manager, RetryTemplate retryTemplate) {
        this.eSignComponent = eSignComponent;
        this.manager = manager;
        this.retryTemplate = retryTemplate;
    }

    /**
     * sends the envelop, creates the handle for getting the sign url and send it to BSS API
     *
     * @param message event json
     * @param execution
     * @throws IOException
     * @throws ClassNotFoundException
     * @throws ApiException
     */
    public String createAndSendEnvelop(String message, DelegateExecution execution) throws IOException, ClassNotFoundException, ApiException {
        DocumentContext context = JsonPath.parse(message);
        String loanId = context.read("$.loanId", String.class);
        if (loanId == null || loanId.isEmpty()) throw new RuntimeException("Loan Id missing in the incoming request");
        String disclosureType = context.read("$.eDisclosureType", String.class);
        int loanNumber = context.read("$.bssLoanId", Integer.class);
        Loan loan = new Loan();
        loan.setLoanId(loanId);
        loan.setBssLoanNum(loanNumber);
        loan.setDisclosureType(DisclosureUtil.getDisclosureForBSS(disclosureType));
        updateSigners(loan, context);
        //TODO: revist the following statement
        String envelopeId = eSignComponent.createEmbeddedEnvelop(context.read("$.documents[?(@.signatureType == 'eSignable')]", List.class), loan);
        ObjectMapper mapper = new ObjectMapper();
        execution.setVariable("loanData", mapper.writeValueAsString(loan));
        return envelopeId;
    }


    /**
     * send the disclosure data and esign handle through BSS API
     *
     * @param bssLoan
     * @throws IOException
     */
    public boolean createDocPackageForBSS(BSSLoan bssLoan) throws IOException {
        String response = retryTemplate.execute(context1 -> {
            return manager.invoke(bssCreateDocPackageUrl, HttpMethod.POST, bssLoan, String.class);
        });
        ObjectMapper mapper = new ObjectMapper();
        DocPackageResponse docPackageResponse = mapper.readValue(response, DocPackageResponse.class);
        return docPackageResponse.isSuccess();
    }


    /**
     * Create the payload data for BSS API call
     *
     * @param loan
     * @param signers
     * @param envelopId
     */
    public void addESignHandle(BSSLoan loan, List<Signer> signers, String envelopId) {
        loan.setDocPackageBorrowers(signers.stream().map(signer -> {
            BSSBorrower borrower = new BSSBorrower();
            borrower.setId(signer.getId());
            String[] hostData = endpoint_host.split("://");
            borrower.setUrl(UriComponentsBuilder.newInstance()
                    .scheme(hostData[0])
                    .host(hostData[1])
                    .path(endpoint_path)
                    .queryParam("borrowerId", signer.getId())
                    .queryParam("envelopId", envelopId)
                    .build().toUriString());
            LOG.debug("uri : " + borrower.getUrl());
            return borrower;
        }).collect(Collectors.toList()));
    }

    /**
     * update signers before sending it to docuesign component. because we dont get the signer data in different objects.
     *
     * @param loan
     * @param context
     */
    private void updateSigners(Loan loan, DocumentContext context) {
        String borrowerName = context.read("$.eDisclosureBorrowerName");
        String coBorrowerName = context.read("$.eDisclosureCoBorrowerName");
        Integer borrowerId = context.read("$.bssBorrowerId.UnformattedValue", Integer.class);
        String borrowerEmail = context.read("$.eDisclosureBorrowerEmail");
        String coBorrowerEmail = context.read("$.eDisclosureCoBorrowerEmail");
        String coBorrowerIdStr = context.read("$.bssCoBorrowerId.UnformattedValue");
        List<Signer> signers = new ArrayList<>(2);
        if (borrowerName == null) throw new RuntimeException("Borrower details missing");
        Signer borrower = new Signer();
        borrower.setName(borrowerName);
        borrower.setEmail(borrowerEmail);
        borrower.setType("Borrower");
        borrower.setId(borrowerId);
        signers.add(borrower);
        //new loan signing entry
        if (!coBorrowerIdStr.trim().isEmpty() && coBorrowerName != null && !coBorrowerName.trim().isEmpty()) {
            Signer coBorrower = new Signer();
            coBorrower.setName(coBorrowerName);
            coBorrower.setEmail(coBorrowerEmail);
            coBorrower.setType("CoBorrower");
            coBorrower.setId(Integer.parseInt(coBorrowerIdStr));
            signers.add(coBorrower);
        }
        loan.setSigners(signers);
    }

    public boolean validate(String eventMsg) {
        DocumentContext context = JsonPath.parse(eventMsg);
        String loanId = context.read("$.loanId", String.class);
        return  (loanId != null && !loanId.isEmpty());
    }
}
