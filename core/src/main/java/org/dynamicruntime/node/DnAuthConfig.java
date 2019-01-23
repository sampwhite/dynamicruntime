package org.dynamicruntime.node;

import org.dynamicruntime.exception.DnException;
import static org.dynamicruntime.schemadata.CoreConstants.*;
import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnCollectionUtil.mMap;

import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class DnAuthConfig {
    /** If at some point, when we can have a real security model inside the Java VM we can put this key somewhere more
     * secure. */
    private final String nodeEncryptionKey;
    public final Map<String,Object> data;

    public DnAuthConfig(String nodeEncryptionKey, Map<String,Object> data) {
        this.nodeEncryptionKey = nodeEncryptionKey;
        this.data = data;
    }

    public static DnAuthConfig extract(Map<String,Object> data) throws DnException {
        String nodeEncryptionKey = getReqStr(data, ND_ENCRYPTION_KEY);
        return new DnAuthConfig(nodeEncryptionKey, data);
    }

    public String getNodeEncryptionKey() {
        return nodeEncryptionKey;
    }

    public static Map<String,Object> mkAuthData(String encryptionKey) {
        return mMap(ND_ENCRYPTION_KEY, encryptionKey);
    }
}
