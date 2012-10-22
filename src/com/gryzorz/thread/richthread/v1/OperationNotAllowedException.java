package com.gryzorz.thread.richthread.v1;

/**
 * 
 * @author Benoit Fernandez
 */
public class OperationNotAllowedException extends Exception {
    @Deprecated //use OperationNotAllowedException(String message)
    public OperationNotAllowedException() {
        
    }
    public OperationNotAllowedException(String message) {
        super(message);
    }
}
