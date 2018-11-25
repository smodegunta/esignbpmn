package com.brimma.bpm.mq;

public class EsignHandlerException extends Throwable {
    public EsignHandlerException(String msg) {
        super(msg);
    }
    public EsignHandlerException(Throwable t){
        super(t);
    }
}
