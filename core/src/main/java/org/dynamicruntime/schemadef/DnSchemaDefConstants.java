package org.dynamicruntime.schemadef;

import java.util.List;
import java.util.Set;

/** Holds the static strings for schema. */
@SuppressWarnings({"WeakerAccess", "unused"})
public class DnSchemaDefConstants {
    public static final String DN_NAMESPACE = "namespace";

    //
    // Primitive types, do not get namespaced or get a type definition.
    //

    public static final String DNT_STRING = "String";
    public static final String DNT_BOOLEAN = "Boolean";
    public static final String DNT_INTEGER = "Integer";
    public static final String DNT_DATE = "Date";
    public static final String DNT_FLOAT = "Float";
    public static final String DNT_ANY = "Any";
    public static final String DNT_MAP = "Map";
    public static final String DNT_BINARY = "Binary";
    public static final String DNT_GENERIC = "Generic";
    /** Represent a type that is not represented by normal JSON style data. It can represent a
     * request that takes no parameters or represent simple string data or binary data in a response.
     * Using a value of DNT_NONE essentially says that the normal JSON oriented DnSchema logic does not
     * interact with the entity that has this type name. */
    public static final String DNT_NONE = "None";

    public static final Set<String> PRIMITIVE_TYPES = Set.of(DNT_STRING, DNT_BOOLEAN, DNT_INTEGER,
            DNT_DATE, DNT_FLOAT, DNT_ANY, DNT_MAP, DNT_BINARY, DNT_GENERIC, DNT_NONE);

    public static boolean isPrimitive(String dnTypeName) {
        return PRIMITIVE_TYPES.contains(dnTypeName);
    }


    //
    // DnType
    //

    /** Name of a DnType or DnField. */
    public static final String DN_NAME = "name";
    /** Label of DnType or DnField. */
    public static final String DN_LABEL = "label";
    /** Description of DnType or DnField */
    public static final String DN_DESCRIPTION = "description";
    /** The name of a post processor builder to expand and refactor type for a specific purpose. */
    public static final String DN_BUILDER = "dnBuilder";
    /** Reference to a base DnType by a DnType. Used for one type to extend another. */
    public static final String DN_BASE_TYPE = "baseType";
    /** Reference to the core type underlying all the base types. */
    public static final String DN_CORE_TYPE = "coreType";
    /** Attribute that holds the fields of the DnType. */
    public static final String DN_FIELDS = "dnFields";
    /** Whether trimming should not be done on strings. */
    public static final String DN_NO_TRIMMING = "noTrimming";
    /** Whether type represents an endpoint. */
    public static final String DN_IS_ENDPOINT = "isEndpoint";
    /** Whether value is large and requires multiple lines of input and likely to have more than 255
     * characters. */
    public static final String DN_IS_LARGE_STRING = "isLargeString";
    /** Whether type represents the definition of a table. */
    public static final String DN_IS_TABLE = "isTable";
    /** Whether values for type do not allow a comma. If true, then values can be put into a
     * comma separated list when there are more than one of them and if a string value has commas in
     * it, it can be broken up into a list of values (assuming *isList* is set). */
    public static final String DN_NO_COMMAS = "noCommas";
    /** The maximum possible value (value is strictly less than max). */
    public static final String DN_MAX = "max";
    /** The smallest possible value (value is greater or equal to min). */
    public static final String DN_MIN = "min";
    /** Whether the field should do auto-incrementing (used in table design). */
    public static final String DN_IS_AUTO_INCREMENTING = "isAutoIncrementing";

    //
    // DnField
    //

    /** Attribute of DnField a reference to a DnType for the field. */
    public static final String DN_TYPE_REF = "dnTypeRef";
    /** Attribute holding an inline implementation of a DnType for the field. */
    public static final String DN_TYPE_DEF = "dnTypeDef";
    /** Whether DnType is a list (or collection of the DnType) instead of a single instance of the DnType. */
    public static final String DN_IS_LIST = "isList";
    /** Whether the fields in the DnType are treated as a choice list. */
    public static final String DN_TYPE_IS_CHOICES = "typeIsChoices";
    /** Whether the validation is strict allowing no variation from expected values. */
    public static final String DN_IS_STRICT = "isStrict";
    /** The DnType of the values of the choices if *typeIsChoices is enabled. */
    public static final String DN_CHOICE_TYPE_REF = "dnChoiceTypeRef";
    /** The value to use if the field is being treated as a choice list value. If value is not present
     * the field name is used instead. */
    public static final String DN_CHOICE_VALUE = "choiceValue";
    /** The default value to use if none is supplied for field when field is used to validate or transform
     * input data. */
    public static final String DN_DEFAULT_VALUE = "defaultValue";
    /** Sort rank of the field. Fields with lower sort rank will be put earlier into the list of fields. Fields
     * that share sort ranks will keep their original order. */
    public static final String DN_SORT_RANK = "sortRank";
    /** Whether the DnField represents a field value that is required. */
    public static final String DN_REQUIRED = "required";
    /** Whether the DnField is implicitly deleted. */
    public static final String DN_DISABLED = "disabled";

    /** Default sort rank. */
    public static final int DN_DEFAULT_SORT_RANK = 100;

    //
    // Core types.
    //
    /** Namespace for core types. */
    public static final String DN_CORE_NAMESPACE = "core";
    /** Type for a counting integer. An integer that starts at zero. Note that we have already
     * applied the namespace so that it can be easily referenced as type for a field. The count type
     * is provided for free at startup. */
    public static final String DNT_COUNT = "core.Count";

    //
    // Endpoint fields.
    //

    /** Name of endpoint builder. */
    public static final String EP_ENDPOINT = "endpoint";
    /** The http method (GET, POST, or PUT) for the endpoint. */
    public static final String EP_HTTP_METHOD = "httpMethod";
    /** Endpoint path. */
    public static final String EP_PATH = "path";
    /** Name of function to execute. */
    public static final String EP_FUNCTION = "function";
    /** Reference to schema to apply to input data. */
    public static final String EP_INPUT_TYPE_REF = "inputTypeRef";
    /** Reference to schema that defines output data. */
    public static final String EP_OUTPUT_TYPE_REF = "outputTypeRef";
    /** Indicates whether response data is put into a list of items. */
    public static final String EP_IS_LIST_RESPONSE = "isListResponse";
    /** Indicates whether request with list response should *not* allow a limit parameter. */
    public static final String EP_NO_LIMIT_PARAMETER = "noLimitParameter";
    /** Whether field holds password information or other secure data. */
    public static final String EP_IS_PASSWORD = "isPassword";
    /** The maximum number of items to return. */
    public static final String EPF_LIMIT = "limit";
    /** The *from* date for a date range query. Returns everything after or equal to this date. */
    public static final String EPF_FROM = "from";
    /** The *until* date for a date range query. Returns everything before the date, but not including the date. */
    public static final String EPF_UNTIL = "until";
    /** The number of items returned. */
    public static final String EPR_NUM_ITEMS = "numItems";
    /** Whether the endpoint definition supports indicating whether more items can be returned. */
    public static final String EP_HAS_MORE_PAGING = "hasMorePaging";
    /** Whether there are more items beyond the ones that were returned. */
    public static final String EPR_HAS_MORE = "hasMore";
    /** Enables returning the number of available items. */
    public static final String EP_HAS_NUM_AVAILABLE = "hasNumAvailable";
    /** The total number of available items.  If the *limit* parameter reduced the number of items
     * being returned, then this is the total count of what would have been available if the the number
     * had not been reduced.*/
    public static final String EPR_NUM_AVAILABLE = "numAvailable";
    /** The original Request URI of the request. */
    public static final String EPR_REQUEST_URI = "requestUri";
    /** The duration of the request (as computed internally). */
    public static final String EPR_DURATION = "duration";
    /** The items to be returned. */
    public static final String EPR_ITEMS = "items";
    /** The name of the field that defines the input type for the endpoint. */
    public static final String EPF_INPUT_TYPE = "endpointInputType";
    /** The name of the field that defines the output type for the endpoint. */
    public static final String EPF_OUTPUT_TYPE = "endpointOutputType";

    /** The allowable HTTP methods. */
    public static final String EPH_GET = "GET";
    public static final String EPH_POST = "POST";
    public static final String EPH_PUT = "PUT";

    public static final List<String> EPH_ALLOWABLE_HTTP_METHODS = List.of(EPH_GET, EPH_POST, EPH_PUT);

    //
    // Table definition fields
    //
    /** Name of table builder. */
    public static final String TB_TABLE = "table";
    /** Root name of table, can be varied by version and context information. */
    public static final String TB_NAME = "tableName";
    /** The primary key. */
    public static final String TB_PRIMARY_KEY = "primaryKey";
    /** The secondary indexes. */
    public static final String TB_INDEXES = "indexes";
    /** The name of the auto-counter column. */
    public static final String TB_COUNTER_FIELD = "counterField";
    /** Whether *createdDate* and *modifiedDate* should be added to table. */
    public static final String TB_HAS_ROW_DATES = "hasRowDates";
    /** Whether the *sourceDate*, the date assigned to the data that the external systems recognize as the date
     * of the data. */
    public static final String TB_HAS_SOURCE_DATE = "hasSourceDate";
    /** Whether a acting user ID tracker should be added to the table. */
    public static final String TB_HAS_MODIFY_USER = "hasModifyUser";
    /** Whether the *enabled* field should *not* be added to the table. */
    public static final String TB_NO_ENABLED = "noEnabled";
    /** Whether this is a table for storing user data and user fields should be added. */
    public static final String TB_IS_USER_DATA = "isUserData";
    /** Whether the table is a top level transaction locking table. */
    public static final String TB_IS_TOP_LEVEL = "isTopLevel";
    /** Name of fields entry in index. */
    public static final String TBI_INDEX_FIELDS = "fields";
    /** Whether index is unique. */
    public static final String TBI_UNIQUE_INDEX = "unique";
    /** Name of properties entry in index. */
    public static final String TBI_INDEX_PROPS = "props";
    /** Whether a column is a big string. Needed for non-postgres databases including H2. */
    public static final String TBC_IS_BIG_STRING = "isBigString";

    //
    // Protocol fields, no prefix because they are so common.
    //
    /** Unique identifier for consumer. */
    public static final String USER_ID = "userId";
    /** The group consumer belongs to, also group may be used in other contexts as well. */
    public static final String USER_GROUP = "userGroup";
    /** The account to which the user belongs. */
    public static final String USER_ACCOUNT = "userAccount";
    /** The shard to which the user belongs. */
    public static final String USER_SHARD = "shard";
    /** User doing modifications. */
    public static final String MODIFY_USER = "modifyUser";
    /** Whether something is enabled. */
    public static final String ENABLED = "enabled";
    /** The date something was created. */
    public static final String CREATED_DATE = "createdDate";
    /** The date something was last modified. */
    public static final String MODIFIED_DATE = "modifiedDate";
    /** The external date for data. */
    public static final String SOURCE_DATE = "sourceDate";
    /** The expiration date for the data. The row is treated as disabled if the current date is greater
     * than this date. */
    public static final String EXPIRE_DATE = "expireDate";
    /** Whether the data in the data or row has been verified. */
    public static final String VERIFIED = "verified";
    /** An authentication code sent to the user that is either played back by the user or attached to a later
     * request. */
    public static final String AUTH_CODE = "authCode";
    /** An expiration time for when an authentication action must take place. */
    public static final String AUTH_ACTION_EXPIRATION = "authActionExpiration";
    /** The identifier of the transaction. It must be unique in the scope of a particular userId, unless
     * the userId is not part of the primary key of the top level table, in which case it has to be globally
     * unique (to the application instance). It is the *tranId* which makes edit requests idempotent, because
     * there should be a history table that records each transaction and if the same transaction is attempted,
     * it should do a no-op and report success. */
    public static final String TRAN_ID = "tranId";
    /** The identifier of the last transaction that updated this table. */
    public static final String LAST_TRAN_ID = "lastTranId";
    /** Last touched date, used for initiating transactions. */
    public static final String TOUCHED_DATE = "touchedDate";
}
