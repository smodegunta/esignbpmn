package com.brimma.bpm.vo.bss;

public class DocPackageResponse {
    private int homeLoanDocPackageId;
    private boolean success;

    public int getHomeLoanDocPackageId() {
        return homeLoanDocPackageId;
    }

    public void setHomeLoanDocPackageId(int homeLoanDocPackageId) {
        this.homeLoanDocPackageId = homeLoanDocPackageId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
