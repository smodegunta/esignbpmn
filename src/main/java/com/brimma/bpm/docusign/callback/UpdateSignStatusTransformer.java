package com.brimma.bpm.docusign.callback;


import com.brimma.bpm.util.auth.bss.AuthClientManager;
import com.brimma.bpm.vo.Borrower;
import com.brimma.bpm.vo.Loan;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Interim status update from docusign is converted to json and sent to sdk wrapper through active mq
 */
@Component
public class UpdateSignStatusTransformer {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateSignStatusTransformer.class);

    /**
     * Transform the payload xml from docusign after every sign complete
     *
     * @param in
     * @return
     * @throws Exception
     */
    public String transform(String in, Consumer<Loan> loanConsumer) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new ByteArrayInputStream(in.getBytes()));
        XPath xPath = XPathFactory.newInstance().newXPath();
        return getEncompassMsgFromPayload(document, xPath, loanConsumer).jsonString();
    }

    /**
     * Transform and notify the BSS API that the signer signed the disclosure
     *
     * @param document
     * @param xPath
     * @param loanConsumer
     * @return
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws IOException
     * @throws XPathExpressionException
     */
    public DocumentContext getEncompassMsgFromPayload(Document document, XPath xPath, Consumer<Loan> loanConsumer) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {

        String loanId = (String) xPath.evaluate("/DocuSignEnvelopeInformation/EnvelopeStatus/CustomFields/CustomField[Name='guid']/Value/text()", document, XPathConstants.STRING);
        LOG.debug("The loan Id received for interim update is " + loanId + ".");
        String disclosureType = (String) xPath.evaluate("/DocuSignEnvelopeInformation/EnvelopeStatus/CustomFields/CustomField[Name='disclosureType']/Value/text()", document, XPathConstants.STRING);
        String bssLoanId = (String) xPath.evaluate("/DocuSignEnvelopeInformation/EnvelopeStatus/CustomFields/CustomField[Name='BssLoanId']/Value/text()", document, XPathConstants.STRING);
        DocumentContext context = JsonPath.parse("{}");
        context.put("$", "loanId", loanId);
        context.put("$", "bssLoanId", bssLoanId);
        context.put("$", "disclosureType", disclosureType);

        NodeList recepientsStatuses = (NodeList) xPath.evaluate("/DocuSignEnvelopeInformation/EnvelopeStatus/RecipientStatuses/RecipientStatus", document, XPathConstants.NODESET);
        for (int i = 0; i < recepientsStatuses.getLength(); i++) {
            Node borrowerStatusObj = recepientsStatuses.item(i);
            String borrowerEmail = (String) xPath.evaluate("Email", borrowerStatusObj, XPathConstants.STRING);
            String borrowerId = (String) xPath.evaluate("ClientUserId", borrowerStatusObj, XPathConstants.STRING);
            String borrowerName = (String) xPath.evaluate("UserName", borrowerStatusObj, XPathConstants.STRING);
            String borrowerStatus = (String) xPath.evaluate("Status", borrowerStatusObj, XPathConstants.STRING);
            String declineReason = (String) xPath.evaluate("DeclineReason", borrowerStatusObj, XPathConstants.STRING);
            String borrowerIPAddress = (String) xPath.evaluate("RecipientIPAddress", borrowerStatusObj, XPathConstants.STRING);
            String customFieldBorrowerType = (String) xPath.evaluate("/DocuSignEnvelopeInformation/EnvelopeStatus/CustomFields/CustomField[Value='" + borrowerId + "']/Name", document, XPathConstants.STRING);
            context.put("$", "eDisclosure" + customFieldBorrowerType + "Email", borrowerEmail);
            context.put("$", "eDisclosure" + customFieldBorrowerType + "Name", borrowerName);
            context.put("$", "bss" + customFieldBorrowerType + "Id", borrowerId);
            context.put("$", "eDisclosure" + customFieldBorrowerType + "eSigned", borrowerStatus.equals("Completed"));
            context.put("$", "eDisclosure" + customFieldBorrowerType + "AuthenticatedIP", borrowerIPAddress);
            context.put("$", "eDisclosure" + customFieldBorrowerType + "Authenticated", borrowerStatus.equals("Completed"));
            context.put("$", "eDisclosure" + customFieldBorrowerType + "DocumentViewed", borrowerStatus.equals("Completed"));
            context.put("$", "eDisclosure" + customFieldBorrowerType + "eSignedIP", borrowerIPAddress);

            if (borrowerStatus.equals("Completed") || borrowerStatus.equals("Declined")) {
                Loan loan = new Loan();
                loan.setId(loanId);
//                loan.setLoanNumber(Integer.parseInt(bssLoanId));
                loan.setDisclosureType(disclosureType);
                Borrower borrower = new Borrower();
                borrower.setBssId(Integer.parseInt(borrowerId));
                borrower.setStatus(borrowerStatus);
                borrower.setDeclineReason(declineReason);
                loan.setBorrowers(Arrays.asList(borrower));
                loanConsumer.accept(loan);
            }
            //TODO :: rejected case needs to be added
        }
        return context;
    }
}
