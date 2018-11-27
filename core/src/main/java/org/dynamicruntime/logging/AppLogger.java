package org.dynamicruntime.logging;

import org.dynamicruntime.context.DnCxt;

import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;

import org.apache.logging.log4j.Logger;

public class AppLogger {
    public final Class topic;
    public final Logger logger;

    public AppLogger(Class topic) {
        this.topic = topic;
        this.logger = LogManager.getLogger(topic);
    }

    public void debug(DnCxt cxt, String message) {
        reportMessage(cxt, Level.DEBUG, null, message, null);
    }

    public void info(DnCxt cxt, String message) {
        reportMessage(cxt, Level.INFO, null, message, null);
    }

    public void error(DnCxt cxt, Throwable t, String message) {
        reportMessage(cxt, Level.ERROR, t, message, null);
    }

    public void reportMessage(DnCxt cxt, Level level, Throwable t, String message, Map<String,Object> data) {
        String msg = cxt != null ? "[" + cxt.loggingId + "] " + message : message;
        logger.log(level, msg, t);
    }
}
