package com.brimma.bpm.vo.brimma;

import java.util.List;

public class Loan {
    private String loanId;
    private int bssLoanNum;
    private String disclosureType;
    private List<Signer> signers;

    public String getLoanId() {
        return loanId;
    }

    public void setLoanId(String loanId) {
        this.loanId = loanId;
    }

    public int getBssLoanNum() {
        return bssLoanNum;
    }

    public void setBssLoanNum(int bssLoanNum) {
        this.bssLoanNum = bssLoanNum;
    }

    public String getDisclosureType() {
        return disclosureType;
    }

    public void setDisclosureType(String disclosureType) {
        this.disclosureType = disclosureType;
    }

    public List<Signer> getSigners() {
        return signers;
    }

    public void setSigners(List<Signer> signers) {
        this.signers = signers;
    }
}
