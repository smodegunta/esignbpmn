package com.brimma.bpm.vo.bss;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public class BSSLoan {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private int loanId;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String documentPackageId;
    private List<BSSBorrower> docPackageBorrowers;

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

    public List<BSSBorrower> getDocPackageBorrowers() {
        return docPackageBorrowers;
    }

    public void setDocPackageBorrowers(List<BSSBorrower> docPackageBorrowers) {
        this.docPackageBorrowers = docPackageBorrowers;
    }
}
