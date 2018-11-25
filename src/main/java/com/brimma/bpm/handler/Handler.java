package com.brimma.bpm.handler;

public interface Handler<T, R> {
    default R handle(T in) throws Exception{
        throw new RuntimeException("Not Implemented");
    }

    default R handle() throws Exception{
        throw new RuntimeException("Not Implemented");
    }
}
