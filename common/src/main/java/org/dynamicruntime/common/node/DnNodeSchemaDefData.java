package org.dynamicruntime.common.node;

import org.dynamicruntime.schemadef.DnRawField;
import org.dynamicruntime.schemadef.DnRawSchemaPackage;
import org.dynamicruntime.schemadef.DnRawTable;

import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;
import static org.dynamicruntime.schemadata.CoreConstants.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;

import static org.dynamicruntime.schemadef.DnRawField.*;
import static org.dynamicruntime.schemadef.DnRawTable.*;

@SuppressWarnings("WeakerAccess")
public class DnNodeSchemaDefData {

    //
    // InstanceConfig - General configuration about this instance. This is considered to be private data
    // and in full deployments, this data should be in its own database with other private data, so it cannot
    // be casually found when administrators are inspecting user data tables. Generally the contents
    // of this table should be small and entirely cached in memory at node start.
    //

    static public DnRawField instanceName = mkReqField(ND_INSTANCE_NAME, "Instance Name",
            "Unique identifier of instance of application.");
    static public DnRawField configType = mkReqField(ND_CONFIG_TYPE, "Config Type",
            "The type of configuration held in this row.");
    static public DnRawField configName = mkReqField(ND_CONFIG_NAME, "Config Name",
            "The name of the configuration data.");
    static public DnRawField configData = mkReqField(ND_CONFIG_DATA, "Config Data",
            "The configuration data for this entry row.").setTypeRef(DNT_MAP);
    static public DnRawTable instanceConfigTable = mkStdTable(DnNodeTableConstants.NT_INSTANCE_CONFIG,
            "Stores private instance data", mList(instanceName, configType, configName, configData),
            mList(ND_INSTANCE_NAME, ND_CONFIG_NAME));

    static public DnRawSchemaPackage getPackage() {
        return DnRawSchemaPackage.mkPackage("NodeSchema", ND_NAMESPACE, mList(instanceConfigTable));
    }

}
