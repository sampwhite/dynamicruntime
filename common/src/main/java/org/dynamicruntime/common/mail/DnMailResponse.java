package org.dynamicruntime.common.mail;

import java.util.Map;

import static org.dynamicruntime.util.ConvertUtil.*;

public class DnMailResponse {
    public final String id;
    public final String message;
    public final Map<String,Object> data;

    public Map<String,Object> mailData;
    public boolean sentToMailServer;

    public DnMailResponse(String id, String message, Map<String,Object> data) {
        this.id = id;
        this.message = message;
        this.data = data;
    }

    public static DnMailResponse extract(Map<String,Object> data) {
        // This is mailgun specific. At some point, may add support for other mail servers.
        String id = getOptStr(data, "id");
        String message = getOptStr(data, "message");
        return new DnMailResponse(id, message, data);
    }
}
