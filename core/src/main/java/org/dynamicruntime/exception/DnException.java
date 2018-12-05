package org.dynamicruntime.exception;

import static org.dynamicruntime.util.DnCollectionUtil.*;

@SuppressWarnings({"WeakerAccess", "unused"})
public class DnException extends Exception {
    /** HTML codes. Generally if you receive a 500 or a 504, you can retry
     * the request against a cluster. */
    public static final int OK = 200;
    public static int OK_CREATED = 201;
    public static int BAD_INPUT = 400;
    public static int AUTH_NEEDED = 401;
    public static int NOT_AUTHORIZED = 403;
    public static int NOT_FOUND = 404;
    public static int INTERNAL_ERROR = 500;
    // We treat 501 as a version of *400* but it is not the caller's fault.
    public static int NOT_SUPPORTED = 501;
    public static int NOT_RESPONDING = 502;
    public static int NOT_AVAILABLE =  504;

    /** Source codes. What type of resource caused the issue. */
    public static final String NETWORK = "network";
    public static final String FILE = "file";
    public static final String DATABASE = "database";
    public static final String SYSTEM = "system";

    /** Activity codes. What type of activity caused the issue. */
    // No activity reported, the most common case.
    public static final String UNSPECIFIED = "unspecified";
    // Thread interruption.
    public static final String INTERRUPTED = "interrupted";
    // Indicates that parsing or conversion was taking place.
    public static final String CONVERSION = "conversion";
    // Indicates that code threw a deliberate exception based on internal logic and
    // wants the exception to be handled as significant.
    public static final String CODE = "code";
    // Includes any type of failure due to a network misbehaving, not
    // just at time of connection.
    public static final String CONNECTION = "connection";
    // Performing a file system or database operation.
    public static final String IO = "io";

    public final int code;
    public final String source;
    public final String activity;

    public DnException(String msg) {
        this(msg, null, INTERNAL_ERROR, SYSTEM, UNSPECIFIED);
    }

    public DnException(String msg, Throwable t) {
        this(msg, t, INTERNAL_ERROR, SYSTEM, UNSPECIFIED);
    }


    public DnException(String msg, int code) {
        this(msg, null, code, SYSTEM, UNSPECIFIED);
    }

    public DnException(String msg, Throwable cause, int code, String source, String activity) {
        super(msg, cause);
        this.code = code;
        this.source = source;
        this.activity = activity;
    }

    /** Creates an exception that the caller has provided bad input. Typically generated by
     * schema validation failures. */
    public static DnException mkInput(String msg) {
        return new DnException(msg, null, BAD_INPUT, SYSTEM, CODE);
    }

    /** Convert another exception into a BAD_INPUT exception so we can return it as a 400. */
    public static DnException mkInput(String msg, Throwable t) {
        var source = SYSTEM;
        var activity = CODE;
        if (t instanceof DnException) {
            DnException dn = (DnException)t;
            source = dn.source;
            activity = dn.activity;
        }
        return new DnException(msg, null, BAD_INPUT, source, activity);
    }


    /** Creates exception on conversion or parsing activities. Many times conversion or parsing
     * exceptions can be turned into bad input exceptions if there is a requesting agent who might
     * have provided bad data. */
    public static DnException mkConv(String msg) {
        return mkConv(msg, null);
    }

    public static DnException mkConv(String msg, Throwable t) {
        return new DnException(msg, t, INTERNAL_ERROR, SYSTEM, CONVERSION);
    }

    /** Used for logging and reporting. */
    public String getFullMessage() {
        Throwable t = getCause();
        if (t == null || t == this) {
            return getMessage();
        }
        DnException de = (t instanceof DnException) ? (DnException)t : null;
        if (de == null) {
            return getMessage();
        }
        var list = mList(this);

        int count = 0;
        while (count++ < 3) {
            list.add(de);
            Throwable nt = de.getCause();
            de = (nt instanceof DnException) ? (DnException)nt : null;
            if (de == null || list.contains(de)) {
                break;
            }
        }
        return String.join(" ", nMapSimple(list, DnException::getMessage));
    }

}
