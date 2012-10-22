package com.gryzorz.thread.richthread.v1;

/**
 * 
 * @author Benoit Fernandez
 */
public interface RichThreadListener {
    public void threadRunning();
    public void threadRunningWaitingToPause();
    public void threadRunningWaitingToStop();
    public void threadStopping();
    public void threadStoppedAfterStopRequest();
    public void threadStoppedNormally();
    public void threadPaused();
    public void threadCrashed(Throwable t);
    public void threadRestarted();
}
