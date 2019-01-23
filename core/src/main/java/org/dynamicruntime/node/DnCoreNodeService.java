package org.dynamicruntime.node;

import org.dynamicruntime.context.DnConfigUtil;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.startup.ServiceInitializer;
import org.dynamicruntime.util.EncodeUtil;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.schemadata.CoreConstants.*;

import java.util.Date;
import java.util.Map;
import java.util.Objects;

/**
 * The node functionality is built up in two layers. The first is provided by the *core* component
 * and provides common shared functionality and defines the Java classes needed to hold node data. That
 * is what you will find in this Java package.
 *
 * See DnNodeService in the *common* component for additional functionality and for code that initializes
 * parts of this class (see {@link #nodeData}). That class and the Java classes defined in its package
 * provide the non-core parts of the implementation.
 *
 * The idea is that the core component defines the model and APIs and the parts of the functionality that
 * are natural extensions of those pieces. The common component provides the implementation, especially the
 * details of where node data is stored and how it is accessed.
 */
@SuppressWarnings("WeakerAccess")
public class DnCoreNodeService implements ServiceInitializer {
    public static final Date VM_STARTTIME = new Date();

    /** The key that identifies the name of the encryption key used to encrypt the data. These are deliberately
     * a bit obscure because they show up in the encrypted data in plain text. */
    public static final String DEFAULT_AUTH_CONFIG_LOOKUP_KEY = "ak";

    public static final String DN_NODE_HEALTH_SERVICE = DnCoreNodeService.class.getSimpleName();

    private DnNodeId nodeId;
    private String nodeIdLabel;
    public volatile boolean isInCluster = true;
    public boolean loggingHealthChecks = false;
    /** Lookup key to find current active encryption key. */
    public String instanceAuthConfigKey;
    /** Loaded externally by code from code outside *core* source directory during code startup. */
    public final DnNodeData nodeData = new DnNodeData();

    public static DnCoreNodeService get(DnCxt cxt) {
        Object obj = cxt.instanceConfig.get(DN_NODE_HEALTH_SERVICE);
        return (obj instanceof DnCoreNodeService) ? (DnCoreNodeService)obj : null;
    }

    @Override
    public String getServiceName() {
        return DN_NODE_HEALTH_SERVICE;
    }

    @Override
    public void onCreate(DnCxt cxt) {
        nodeId = DnNodeUtil.extractNodeId(cxt);
        nodeIdLabel = nodeId.nodeIpAddress + ":" + nodeId.port;
        loggingHealthChecks = toBoolWithDefault(cxt.instanceConfig.get("loggingHealthChecks"), false);
        instanceAuthConfigKey = DnConfigUtil.getConfigString(cxt, "node.instance.authConfigKey",
                DEFAULT_AUTH_CONFIG_LOOKUP_KEY,
                "Lookup key in instance configuration for encryption and auth logic configuration. " +
                        "Changing this will cause all new encrypted data to use a new key.");
    }

    @Override
    public void checkInit(DnCxt cxt) {

    }

    public DnNodeId getNodeId() {
        return nodeId;
    }

    public String getNodeLabel() {
        return nodeIdLabel;
    }

    public Map<String,Object> getHealth() {
        long vt = VM_STARTTIME.getTime();
        long curTime = System.currentTimeMillis();
        double durInDays = ((double)(curTime - vt))/(1000*24*3600);
        String durRpt = fmtDouble(durInDays) + " days";
        return mMap(ND_START_TIME, VM_STARTTIME, ND_UPTIME, durRpt, ND_NODE_ID, nodeIdLabel,
                ND_IS_CLUSTER_MEMBER, isInCluster, ND_VERSION, "0.2");
    }

    public String encryptString(String plainText) throws DnException {
        DnAuthConfig authConfig = nodeData.getAuthConfig(instanceAuthConfigKey);
        String ec = Objects.requireNonNull(authConfig).getNodeEncryptionKey();
        String encodedData = EncodeUtil.encrypt(ec, plainText);
        return instanceAuthConfigKey + "|" + encodedData;
    }

    public String decryptString(String encryptedText) throws DnException {
        int index = encryptedText.indexOf('|');
        if (index < 0) {
            throw DnException.mkConv("Encrypted text was not in correct format.");
        }
        String configKey = encryptedText.substring(0, index);
        String data = encryptedText.substring(index + 1);
        DnAuthConfig authConfig = nodeData.getAuthConfig(configKey);
        if (authConfig == null) {
            throw new DnException("System does not support encryption key named " + configKey + ".");
        }
        String ec = authConfig.getNodeEncryptionKey();
        return EncodeUtil.decrypt(ec, data);
    }
}
