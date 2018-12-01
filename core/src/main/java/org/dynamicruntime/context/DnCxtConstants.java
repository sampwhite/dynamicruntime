package org.dynamicruntime.context;

@SuppressWarnings({"WeakerAccess", "unused"})
public class DnCxtConstants {
    /* Note the lack of usage of enums. In dynamic runtime, an enum list is a compiler time enforced mechanism on
     * choice values, not consistent with the goals of dynamic modification of model at runtime. */

    /* Environment names */
    /** Running for development purposes. */
    public static final String DEV = "dev";
    /** Running integration tests. */
    public static final String INTEGRATION = "integration";
    /** Running unit tests. */
    public static final String UNIT = "unit";
    /** Running in deployed state. */
    public static final String DEPLOYED = "deployed";

    /* Environment types. The type of system that is running the instance. */
    /** System is for general activities, such as scripts or in start up phase. */
    public static final String GENERAL_TYPE = "generalType";
    /** System is for performing QA. */
    public static final String TEST_TYPE = "testType";
    /** System is for staging to prod. */
    public static final String STAGE_TYPE = "stageType";
    /** System is run for preview purposes. */
    public static final String PREVIEW_TYPE = "previewType";
    /** System is running in prod. */
    public static final String PROD_TYPE = "prodType";

    /** The default shard name. Only if actual sharding is being
     * done is the value anything else. Generally, there has to be a some top level data storage
     * that is not sharded that knows about all the possible shards, except for unit tests. */
    public static final String PRIMARY = "primary";

}
