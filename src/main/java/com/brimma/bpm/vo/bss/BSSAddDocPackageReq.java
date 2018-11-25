package com.brimma.bpm.vo.bss;

public class BSSAddDocPackageReq {
    private int loanId;
    private String documentPackageId;
    private int borrowerId;
    private SinerEvent eventToken;

    public int getLoanId() {
        return loanId;
    }

    public void setLoanId(int loanId) {
        this.loanId = loanId;
    }

    public String getDocumentPackageId() {
        return documentPackageId;
    }

    public void setDocumentPackageId(String documentPackageId) {
        this.documentPackageId = documentPackageId;
    }

    public int getBorrowerId() {
        return borrowerId;
    }

    public void setBorrowerId(int borrowerId) {
        this.borrowerId = borrowerId;
    }

    public SinerEvent getEventToken() {
        return eventToken;
    }

    public void setEventToken(SinerEvent eventToken) {
        this.eventToken = eventToken;
    }
}
