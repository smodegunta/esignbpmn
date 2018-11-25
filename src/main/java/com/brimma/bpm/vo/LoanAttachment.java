package com.brimma.bpm.vo;

public class LoanAttachment {
    /*{
  "loanNumber": "9a06733f-b10c-4d45-9970-a481763cbb6c",
  "document": {
    "fileName": "CKs & Marlboro LL Clinic 2017 (1).pdf",
    "base64Encoded": "DEV/1j9gRwgMQ9yOQak4lFg2_CKs & Marlboro LL Clinic 2017 (1).pdf"
  },
  "documentName": "Bank Statements"
}*/
    public static class Document{
        private String fileName;
        private String base64Encoded;

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getBase64Encoded() {
            return base64Encoded;
        }

        public void setBase64Encoded(String base64Encoded) {
            this.base64Encoded = base64Encoded;
        }
    }

    private String loanNumber;
    private Document document;
    private String documentName;

    public String getLoanNumber() {
        return loanNumber;
    }

    public void setLoanNumber(String loanNumber) {
        this.loanNumber = loanNumber;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }
}
