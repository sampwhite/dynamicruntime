package org.dynamicruntime.sql.topic;

/** Has the common topic names and related constants. New topics can be added to this file even if they
 * are used by an optional component or are speculative. */
@SuppressWarnings("unused")
public class SqlTopicConstants {
    //
    // Node topics. These topics focus on general system issues and endpoints for this topic generally
    // focus on node health and configuration.
    //
    /** Data store used by nodes to store information about their state and how they can be found. Used by
     * nodes to find each other. It also contains top-level private keys specific to a particular instance.
     * Node topics are *not* sharded.  */
    public static final String NODE_TOPIC = "node";
    /** Holds any configuration data that configures this particular instance of the application in variance
     * from the standard configuration. Standard configuration should be in source code (as data files). This topic
     * tends not to be sharded. */
    public static final String CONFIG_TOPIC = "config";

    //
    // User interaction topics. Endpoints focus on the current acting user. There is very little background
    // processing being done.
    //
    /** Storage of user records needed to do authentication. Also provides determination of *userGroup*
     * and *shard*. The auth topic cannot be sharded using the standard sharding solution in this application, but
     * custom sharding can be done based on regional hardware configurations, but that is a separate effort
     * with its own set of functionality. Auth topic data is queried at the time security is evaluated
     * against the context-root of the endpoint. It is then cached in a browser cookie (or mobile
     * equivalent). After determining auth information, the current node may decide
     * to forward the request to another node (but augmented with the auth information that was loaded).
     * When such forwarding is performed, it should be done in a *sticky* way so that requests to the
     * same context-root and same user go to the same node (as long as that node stays responsive).
     * Note, not all auth information is loaded. Some is used only when actually doing authentication (such
     * as contact information). When some auth information is edited it may have a cascading edit of
     * the user profile topic as well (contact information in particular). */
    public static final String AUTH_TOPIC = "auth";
    /** Storage of user profile information, augments *auth* information. Tends to not need to be sharded
     * (though caching solutions are common). User profile information is loaded before an endpoint
     * implementation is called for context-roots that call for it. When data is edited in this topic,
     * other nodes that query this topic should be notified so caches can be obsoleted. */
    public static final String USER_PROFILE_TOPIC = "userProfile";
    /** Storage of user facts that are captured from user interactions. This topic is likely to use
     * sharding when the system scales up. When data in top level portal pages is edited in this topic,
     * other nodes handling the same shard of the data should be notified so that caches can be obsoleted. */
    public static final String USER_DATA_TOPIC = "userData";

    //
    // Data processing topics. Endpoints process across many consumers and there tends to be active
    // background processing.
    //
    /** Storage of data used for doing batch processing. The idea is that data is copied from other topics
     * into this topic and then processed. It then provides summary tables for access by the user front-end.
     * The batch topic is the most likely to take advantage of sharding. Batch processing also has
     * the code that migrates users between shards (and userGroups) in all topics that do sharding and
     * userGroup tracking. */
    public static final String BATCH_TOPIC = "batch";
    /** Storage dedicated to the data needed to create reports. This layout of the tables follow a more
     * traditional (inner joins for queries, reference data populating side tables) relational model so
     * that tool chains designed to use SQL databases will find the tables friendly. The reporting topic
     * takes inputs from all the other topics with some *batch* topic processing dedicated to producing
     * *report* topic data. */
    public static final String REPORTING_TOPIC = "reporting";
}
