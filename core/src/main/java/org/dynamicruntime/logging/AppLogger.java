package org.dynamicruntime.logging;

import org.dynamicruntime.context.DnCxt;

import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;

import org.apache.logging.log4j.Logger;

@SuppressWarnings("WeakerAccess")
public class AppLogger {
    final Class topic;
    final Logger logger;

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

    public void reportMessage(DnCxt cxt, Level level, Throwable t, String message,
            @SuppressWarnings("unused") Map<String,Object> data) {
        // Eventually the logging implementation will forward logging to a Fluentd style solution which will include
        // the data.
        String msg = cxt != null ? "[" + cxt.instanceConfig.instanceName + ":"  + cxt.getLogInfo() + "] " + message :
                message;
        logger.log(level, msg, t);
    }
}
