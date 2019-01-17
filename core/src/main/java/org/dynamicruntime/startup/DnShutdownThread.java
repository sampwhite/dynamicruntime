package org.dynamicruntime.startup;

@SuppressWarnings("WeakerAccess")
public class DnShutdownThread extends Thread {
    public DnShutdownThread() {
        super("DnShutdownThread");
    }

    @Override
    public void run() {
        LogStartup.log.info(null, "Shutting down DnServer application.");
    }
}
