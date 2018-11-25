package com.brimma.bpm.docusign;

import com.brimma.bpm.vo.brimma.Loan;
import com.docusign.esign.api.EnvelopesApi;
import com.docusign.esign.client.ApiClient;
import com.docusign.esign.client.ApiException;
import com.docusign.esign.client.Configuration;
import com.docusign.esign.model.*;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.sun.jersey.api.client.ClientHandlerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.client.HttpClientErrorException;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Docusign component that does operations like create envelop, send envelop, create esign view url, download docs etc.
 */
@Component
public class DocusignComponent {
    @Value("${RedirectURI}")
    private String redirectURI;
    @Value("${ClientSecret}")
    private String clientSecret;
    @Value("${IntegratorKey}")
    private String integratorKey;
    @Value("${BaseUrl}")
    private String baseUrl;
    @Value("${AccountId}")
    private String accountId;
    @Value("${ClientUserId}")
    private String clientUserId;
    @Value("${KeyPairId}")
    private String keyPairId;
    @Value("${OAuthBaseUrl}")
    private String oAuthBaseUrl;
    @Value("${RedirectAfterSign}")
    private String redirectAfterSign;
    private String accountIdFromCall;
    @Value("${CONFIG}")
    private String configDir;

    private RetryTemplate retryTemplate;

    private ApiClient apiClient;

    @Autowired
    public DocusignComponent(RetryTemplate retryTemplate, ApiClient apiClient) {
        this.retryTemplate = retryTemplate;
        this.apiClient = apiClient;
    }

    private static final Logger LOG = LoggerFactory.getLogger(DocusignComponent.class);

    /**
     * Download the signed/unsigned documents from docusign and update the signed documents to the message alongside the signed status.
     * @param envelopeId docusign enveop Id
     * @param context json with signed status encompass update
     * @throws ApiException
     */
    public void downloadDocuments(String envelopeId, DocumentContext context) throws ApiException {
        long start = System.currentTimeMillis();
        EnvelopesApi envelopesApi = new EnvelopesApi();
        EnvelopeDocumentsResult docsList = null;
        try {
            docsList = envelopesApi.listDocuments(accountId, envelopeId);
        } catch (ApiException ex) {
            try {
                handleExpiredTokens(ex);
            } catch (IOException e) {
                LOG.error("public and private keys are missing in server for docusign");
            }
        }
        context.set("$.eDisclosureBorrowerWetSigned", false);
        context.set("$.eDisclosureCoBorrowerWebSigned", false);
        List<Map<String, Object>> documents = docsList.getEnvelopeDocuments().parallelStream().filter(doc -> "CONTENT".equalsIgnoreCase(doc.getType())).map(doc -> {
            Map<String, Object> document = new HashMap<>(3);
            try {
                LOG.debug("Downloading " + doc.getName() + ".pdf from Docusign");
                byte[] bytes = envelopesApi.getDocument(accountId, envelopeId, doc.getDocumentId());
                String base64file = Base64.getEncoder().encodeToString(bytes);
                document.put("title", doc.getName());
                document.put("base64String", base64file);
                document.put("fileExtension", "pdf");
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
            return document;
        }).collect(Collectors.toList());
        context.put("$", "documents", documents);
        context.delete("$.documents[?(!@.fileExtension)]");
        long end = System.currentTimeMillis();
        LOG.debug("downloadDocuments from docusign takes " + (end - start) + " milli seconds");
    }

    /**
     * re-auth if the token expires
     * @param ex ApiException thrown if there is any issue from docusign
     * @throws IOException
     * @throws ApiException
     */
    private void handleExpiredTokens(ApiException ex) throws IOException, ApiException {
        String errorCode = JsonPath.parse(ex.getResponseBody()).read("errorCode", String.class);
        if ("USER_AUTHENTICATION_FAILED".equals(errorCode)) {
            //TODO:: handle
            apiClient.getAuthentications().clear();
            doAuth();
            throw ex;
        }else{
            LOG.error("Issue in refreshing token ", ex);
        }
    }

    /*@PostConstruct
    public void init() throws IOException, ApiException {
        accountIdFromCall = doAuth();
    }*/

    /**
     * This method creates the envelop from the disclosure package received from the event handler plugin and sends it to docusign.
     * @param base64Data
     * @param loan
     * @return
     * @throws ApiException
     */
    public EnvelopeDefinition createEmbeddedEnvelop(List<Map<String, String>> base64Data, Loan loan) throws ApiException {
        long start = System.currentTimeMillis();

        EnvelopeDefinition envDef = new EnvelopeDefinition();

        envDef.setEmailSubject("Please Sign the form");
        envDef.setEmailBlurb("Hello, Please sign the form");
        CustomFields fields = new CustomFields();
        TextCustomField guid = new TextCustomField();
        guid.setName("guid");
        guid.setValue(loan.getLoanId());
        fields.addTextCustomFieldsItem(guid);

        TextCustomField loanNum = new TextCustomField();
        loanNum.setName("BssLoanId");
        loanNum.setValue(String.valueOf(loan.getBssLoanNum()));
        fields.addTextCustomFieldsItem(loanNum);

        TextCustomField disclosureType = new TextCustomField();
        disclosureType.setName("disclosureType");
        disclosureType.setValue(loan.getDisclosureType());
        fields.addTextCustomFieldsItem(disclosureType);

        addBase64DocumentToEnvelop(base64Data, envDef);
        createAndAddSignerToEnvelop(envDef, loan.getSigners(), base64Data);
        //store the borrower and coborrower details also in the envelop custom fields
        loan.getSigners().forEach(signer -> {
            TextCustomField field = new TextCustomField();
            field.setName(signer.getType());
            field.setValue(String.valueOf(signer.getId()));
            fields.addTextCustomFieldsItem(field);
        });
        envDef.setCustomFields(fields);
        envDef.setStatus("sent");
        return envDef;
        //retry sending the envelop in case of any exception
    }

    /**
     * Sends the envelop to docusign and in case if the access token expires, it gets the new token and tries again.
     * @param envDef
     * @return envelopId with which the docusign can be queried for the required data
     * @throws ClientHandlerException
     * @throws ApiException
     */
    public String sendEnvelop(EnvelopeDefinition envDef) throws ClientHandlerException, ApiException {
        String envelopId = null;
        LOG.debug("trying to send the envelop to docusign");
        EnvelopesApi envelopesApi = new EnvelopesApi(apiClient);
        try {
            EnvelopeSummary envelopeSummary =
                    envelopesApi.createEnvelope(accountIdFromCall, envDef);
            envelopId = envelopeSummary.getEnvelopeId();
            LOG.debug("Latest envelop :: " + envelopId);
        } catch (ApiException ex) {
            LOG.debug("sending the envelop to docusign failed");
            try {
                handleExpiredTokens(ex);
            } catch (IOException e) {
                LOG.error("public and private keys are missing in server for docusign");
            }
        }
        LOG.debug("sent the envelop to docusign");
        return envelopId;
    }

    /**
     * Creates recipient view url (docusign signing session for borrowers)
     * @param envelopId
     * @param borrowerId
     * @return the view Url for a particular borrower
     * @throws ApiException
     */
    public String getRecipientViewUrl(String envelopId, String borrowerId) throws ApiException {
        return getRecipientViewUrl(envelopId, borrowerId, null);
    }

    /**
     * Creates recipient view url (docusign signing session for borrowers)
     * @param envelopId
     * @param borrowerId
     * @param consumer a caalback to access the consumer data from the caaller of this method
     * @return the view Url for a particular borrower
     * @throws ApiException
     */
    public String getRecipientViewUrl(String envelopId, String borrowerId, BiConsumer<Signer, List<TextCustomField>> consumer) throws ApiException {
        try {
            EnvelopesApi envelopesApi = new EnvelopesApi(apiClient);
            Recipients recipients = envelopesApi.listRecipients(accountId, envelopId);
            CustomFieldsEnvelope customFields = envelopesApi.listCustomFields(accountId, envelopId);
            Signer signer = recipients.getSigners()
                    .stream()
                    .filter(s -> s.getClientUserId().equals(borrowerId))
                    .findFirst()
                    .get();
            if(consumer!=null) consumer.accept(signer, customFields.getTextCustomFields());
            RecipientViewRequest recipientView = new RecipientViewRequest();
            recipientView.setReturnUrl(redirectAfterSign);
            recipientView.setClientUserId(signer.getClientUserId());
            recipientView.setAuthenticationMethod("email");
            recipientView.setUserName(signer.getName());
            recipientView.setEmail(signer.getEmail());

            return envelopesApi.createRecipientView(accountId, envelopId, recipientView).getUrl();
        } catch (ApiException ex) {
            try {
                handleExpiredTokens(ex);
            } catch (IOException e) {
                LOG.error("public and private keys are missing in server for docusign");
            }
        }
        return null;
    }

    /**
     * Download documents from docusign
     * @param envelopId
     * @return combined disclosure documents stream
     * @throws ApiException
     */
    public byte[] downloadDocuments(String envelopId) throws ApiException {
        byte[] docs = null;
        try {
            EnvelopesApi envelopesApi = new EnvelopesApi(apiClient);
            CustomFieldsEnvelope envelop = envelopesApi.listCustomFields(accountId, envelopId);
            Optional<TextCustomField> loanOpt = envelop.getTextCustomFields().stream().filter(field -> field.getName().equalsIgnoreCase("guid")).findFirst();
            if (!loanOpt.isPresent()) {
                LOG.debug("no loan is associated with the given envelop id.");
                throw new HttpClientErrorException(HttpStatus.FORBIDDEN);
            }
            docs = envelopesApi.getDocument(accountId, envelopId, "combined");
        }catch (ApiException ex){
            try {
                handleExpiredTokens(ex);
            } catch (IOException e) {
                LOG.error("public and private keys are missing in server for docusign");
            }
        }
        return docs;
    }

    /**
     * se the api client with required data after auth call
     * @return docusign Account Id
     * @throws IOException
     * @throws ApiException
     */
    private String doAuth() throws IOException, ApiException {
        authFlow();
        apiClient.setBasePath(baseUrl);
        Configuration.setDefaultApiClient(apiClient);
        return accountId;
    }

    /**
     * do auth and get the required token data
     * @throws IOException
     * @throws ApiException
     */
    private void authFlow() throws IOException, ApiException {
//        File file1 = new File(configDir, "public_key.txt");
//        File file2 = new File(configDir, "private_key.txt");
        File file1 = new ClassPathResource("public_key.txt").getFile();
        File file2 = new ClassPathResource("private_key.txt").getFile();
        String publicKeyPath = file1.getAbsolutePath();
        String privateKeyPath = file2.getAbsolutePath();
//        makeLocalCopiesOfKeysFromJar(file1, file2);
        LOG.debug("BEFORE AUTH FLOW");
        LOG.debug(apiClient.getJWTUri(integratorKey, redirectURI, oAuthBaseUrl));
        apiClient.configureJWTAuthorizationFlow(publicKeyPath, privateKeyPath, oAuthBaseUrl, integratorKey, clientUserId, 3600L);
        LOG.debug("AFTER AUTH FLOW");
    }

    /**
     * Since the deployment is archive we need the docusign public and private keys outside the jar.
     * This will be changed once we finalize the deployment archive
     * @param file1
     * @param file2
     * @throws IOException
     */
    private void makeLocalCopiesOfKeysFromJar(File file1, File file2) throws IOException {
        FileOutputStream stream1 = new FileOutputStream(file1);
        FileOutputStream stream2 = new FileOutputStream(file2);
        FileCopyUtils.copy(new ClassPathResource("public_key.txt").getInputStream(), stream1);
        FileCopyUtils.copy(new ClassPathResource("private_key.txt").getInputStream(), stream2);
        try {
            stream1.close();
        } catch (Exception e) {
            LOG.debug("Already closed the file streams");
        }
        try {
            stream2.close();
        } catch (Exception e) {
            LOG.debug("Already closed the file streams");
        }
    }

    /**
     * creates signers and signer tags for each document
     * @param envDef envelop definition
     * @param signers
     * @param docsFromEvent documents recieved as part of disclosure event
     */
    private void createAndAddSignerToEnvelop(EnvelopeDefinition envDef, List<com.brimma.bpm.vo.brimma.Signer> signers, List<Map<String, String>> docsFromEvent) {
        envDef.setRecipients(new Recipients());
        envDef.getRecipients().setSigners(new ArrayList<Signer>());
        signers.stream().forEach(signer -> {
            Signer docusignSigner = createSigner(signer);
            envDef.getRecipients().getSigners().add(docusignSigner);
        });

        envDef.getRecipients().getSigners().stream().forEach(signer -> signer.setTabs(new Tabs()));
        envDef.getDocuments().forEach(doc -> {
            try {
                createSignerData(
                        docsFromEvent.stream().filter((entry) -> entry.containsKey("title") && entry.get("title").equals(doc.getName())).findFirst(),
                        signers,
                        doc.getDocumentId(),
                        envDef.getRecipients().getSigners()
                );
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * add the disclosure documents to envelop
     * @param base64Docs
     * @param envDef
     */
    private void addBase64DocumentToEnvelop(List<Map<String, String>> base64Docs, EnvelopeDefinition envDef) {
        envDef.setDocuments(IntStream.range(0, base64Docs.size())
                .parallel()
                .mapToObj(index -> {
                    Map<String, String> document = base64Docs.get(index);
                    Document doc = new Document();
                    doc.setDocumentBase64(document.get("base64String"));
                    doc.setName(document.get("title"));
                    doc.setDocumentId(String.valueOf(index + 1));
                    NameValue docIdEncompass = new NameValue();//TODO:: remove
                    docIdEncompass.setName("id");
                    docIdEncompass.setValue(document.get("id"));
                    doc.setDocumentFields(Arrays.asList(docIdEncompass));
                    return doc;
                }).collect(Collectors.toList()));
    }


    /**
     * create the docusign tags for all the documents for each signer
     * @param docFromEvent
     * @param signers
     * @param documentId
     * @param docuSigners
     * @throws IOException
     */
    private void createSignerData(Optional<Map<String, String>> docFromEvent, List<com.brimma.bpm.vo.brimma.Signer> signers, final String documentId, List<Signer> docuSigners) throws IOException {
        String tagMetadata = docFromEvent.get().get("esignMetadata");
        if (!docFromEvent.isPresent() ||  tagMetadata== null || tagMetadata.trim().isEmpty()) return;
        DocumentContext context = JsonPath.parse(tagMetadata);
        List data = context.read("$.signers", List.class);
        IntStream.range(0, data.size()).forEach(index -> {
            Map<String, Object> signerData = (Map<String, Object>) data.get(index);
            String signerType = (String) signerData.get("type");
            Optional<com.brimma.bpm.vo.brimma.Signer> ellieSigner = signers.stream().filter(signer -> signer.getType().equalsIgnoreCase(signerType)).findFirst();
            if (ellieSigner.isPresent()) {//Only the signing party with
                Optional<Signer> signerOpt = docuSigners.stream().filter(signer -> signer.getClientUserId().equalsIgnoreCase(String.valueOf(ellieSigner.get().getId()))).findFirst();
                List<SignHere> signHereTabs = new ArrayList<SignHere>();
                List<DateSigned> dateSignedList = new ArrayList<DateSigned>();
                List<RadioGroup> radioGroupTabs = new ArrayList<RadioGroup>();
                List<InitialHere> initialHereTabs = new ArrayList<InitialHere>();
                List<Text> textTabs = new ArrayList<Text>();

                ((List<Map<String, Object>>) signerData.get("fields")).forEach(sign -> {
                    if (sign.get("type").equals("SignHere")) {
                        SignHere signHere = createSignedTag(sign, documentId);
                        signHereTabs.add(signHere);
                    } else if (sign.get("type").equals("SignAndDate")) {
                        SignHere signHere = createSignedTag(sign, documentId);
                        signHereTabs.add(signHere);
                        DateSigned dateSignHere = createDateSignedTag(sign, documentId);
                        dateSignedList.add(dateSignHere);
                    } else if (sign.get("type").equals("RadioGroup")) {
                        RadioGroup checkHere = createRadioGroup(sign, documentId);
                        radioGroupTabs.add(checkHere);
                    } else if (sign.get("type").equals("Initials")) {
                        InitialHere initialHere = createInitialHereMarker(sign, documentId);
                        initialHereTabs.add(initialHere);
                    } else if (sign.get("type").equals("Text")) {
                        Text text = createTextMarker(sign, documentId);
                        textTabs.add(text);
                    }
                });
                Tabs tabs = signerOpt.get().getTabs();
                tabs.getSignHereTabs().addAll(signHereTabs);
                tabs.getDateSignedTabs().addAll(dateSignedList);
                tabs.getRadioGroupTabs().addAll(radioGroupTabs);
                tabs.getInitialHereTabs().addAll(initialHereTabs);
                tabs.getTextTabs().addAll(textTabs);
            }
        });
    }

    private Text createTextMarker(Map<String, Object> sign, String documentId) {
        Text text = new Text();
        Double top = (Double) sign.get("top");
        Double bottom = (Double) sign.get("bottom");
        Double x = top - (bottom - top);
        Double left = (Double) sign.get("left");
        Double right = (Double) sign.get("right");
        text.setDocumentId(documentId);
        text.setXPosition(String.valueOf(left).replaceAll("\\..*$", ""));
        text.setYPosition(String.valueOf(x).replaceAll("\\..*$", ""));
        text.setPageNumber(String.valueOf(sign.get("page")));
        text.maxLength(Double.valueOf(right - left).intValue());
        if (sign.get("conditionalField") != null) {
            Map<String, Object> condition = (Map<String, Object>) sign.get("conditionalField");
            text.setConditionalParentLabel((String) condition.get("id"));
            text.setConditionalParentValue((String) condition.get("value"));
        }
        return text;
    }

    private Signer createSigner(com.brimma.bpm.vo.brimma.Signer borrowerData) {
        Signer signer = new Signer();
        signer.setEmail(borrowerData.getEmail());
        signer.setName(borrowerData.getName());
        signer.setClientUserId(String.valueOf(borrowerData.getId()));
        signer.setRecipientId(String.valueOf(borrowerData.getId()));
        return signer;
    }

    private InitialHere createInitialHereMarker(Map<String, Object> sign, String docId) {
        InitialHere initialHere = new InitialHere();
        Double top = (Double) sign.get("top");
        Double bottom = (Double) sign.get("bottom");
        Double x = top - (bottom - top);
        initialHere.setDocumentId(docId);
        initialHere.setXPosition(String.valueOf(sign.get("left")).replaceAll("\\..*$", ""));
        initialHere.setYPosition(String.valueOf(x).replaceAll("\\..*$", ""));
        initialHere.setPageNumber(String.valueOf(sign.get("page")));
        initialHere.setScaleValue(String.valueOf(sign.get("scale")));
        return initialHere;
    }

    private RadioGroup createRadioGroup(Map<String, Object> sign, String docId) {
        List<Map<String, Object>> itemFields = (List<Map<String, Object>>) sign.get("itemFields");
        RadioGroup group = new RadioGroup();
        group.setDocumentId(docId);
        group.setGroupName((String) sign.get("id"));
        group.setRadios(itemFields.stream().map(item -> {
            Double top = (Double) item.get("top");
            Double left = (Double) item.get("left");
            Double right = (Double) item.get("right");
            Double bottom = (Double) item.get("bottom");
            Double x = left - (right - left) / 2;
            Double y = top - (bottom - top) / 2;
            Radio radio = new Radio();
            radio.setPageNumber(String.valueOf(item.get("page")));
            radio.setValue(String.valueOf(item.get("value")));
            radio.setXPosition(String.valueOf(x).replaceAll("\\..*$", ""));
            radio.setYPosition(String.valueOf(y).replaceAll("\\..*$", ""));
            return radio;
        }).collect(Collectors.toList()));

        return group;
    }

    private SignHere createSignedTag(Map<String, Object> sign, String docId) {
        SignHere signHere = new SignHere();
        Double top = (Double) sign.get("top") - 5;
        Double bottom = (Double) sign.get("bottom");
        Double y = top - (bottom - top);
        signHere.setDocumentId(docId);
        signHere.setXPosition(String.valueOf(sign.get("left")).replaceAll("\\..*$", ""));
        signHere.setYPosition(String.valueOf(y).replaceAll("\\..*$", ""));
        signHere.setPageNumber(String.valueOf(sign.get("page")));
        signHere.setTabLabel((String) sign.get("name"));
        return signHere;
    }

    private DateSigned createDateSignedTag(Map<String, Object> sign, String docId) {
        DateSigned dateSignHere = new DateSigned();
        Double right = (Double) sign.get("right");
        Double left = (Double) sign.get("left");
        Double x = right - (right - left) / 2;
        Double top = (Double) sign.get("top");
        Double bottom = (Double) sign.get("bottom");
        Double someApproxPositionBruteforce = top - (bottom - top) + (bottom - top) * 0.23;
        Double y = someApproxPositionBruteforce + (bottom - someApproxPositionBruteforce) / 2;
        dateSignHere.setDocumentId(docId);
        dateSignHere.setXPosition(String.valueOf(x).replaceAll("\\..*$", ""));
        dateSignHere.setYPosition(String.valueOf(y).replaceAll("\\..*$", ""));
        dateSignHere.setPageNumber(String.valueOf(sign.get("page")));
        dateSignHere.setTabLabel((String) sign.get("name"));
        return dateSignHere;
    }
}
