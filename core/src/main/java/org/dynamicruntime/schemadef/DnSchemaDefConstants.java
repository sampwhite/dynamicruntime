package org.dynamicruntime.schemadef;

import java.util.List;
import java.util.Set;

/** Holds the static strings for schema. */
@SuppressWarnings({"WeakerAccess", "unused"})
public class DnSchemaDefConstants {
    public static final String DN_NAMESPACE = "namespace";

    //
    // Primitive types.
    //

    public static final String DN_STRING = "String";
    public static final String DN_BOOLEAN = "Boolean";
    public static final String DN_INTEGER = "Integer";
    public static final String DN_DATE = "Date";
    public static final String DN_FLOAT = "Float";
    public static final String DN_ANY = "Any";
    public static final String DN_MAP = "Map";
    public static final String DN_GENERIC = "Generic";
    /** Represent a type that is not represented by normal JSON style data. It can represent a
     * request that takes no parameters or represent simple string data or binary data in a response.
     * Using a value of DN_NONE essentially says that the normal JSON oriented DnSchema logic does not
     * interact with the entity that has this type name. */
    public static final String DN_NONE = "None";

    public static final Set<String> PRIMITIVE_TYPES = Set.of(DN_STRING, DN_BOOLEAN, DN_INTEGER,
            DN_DATE, DN_FLOAT, DN_ANY, DN_MAP, DN_GENERIC, DN_NONE);

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

    //
    // DnField
    //

    /** Attribute of DnField a reference to a DnType for the field. */
    public static final String DN_TYPE_REF = "dnTypeRef";
    /** Attribute holding an anonymous implementation of a DnType for the field. */
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
     * applied the namespace so that it can be easily referenced as type for a field. */
    public static final String DN_COUNT = "core.Count";

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
    /** The maximum number of items to return. */
    public static final String EP_LIMIT = "limit";
    /** The number of items returned. */
    public static final String EP_NUM_ITEMS = "numItems";
    /** Whether the endpoint definition supports indicating wether more items can be returned. */
    public static final String EP_HAS_MORE_PAGING = "hasMorePaging";
    /** Whether there are more items beyond the ones that were returned. */
    public static final String EP_HAS_MORE = "hasMore";
    /** Enables returning the number of available items. */
    public static final String EP_HAS_NUM_AVAILABLE = "hasNumAvailable";
    /** The total number of available items.  If the *limit* parameter reduced the number of items
     * being returned, then this is the total count of what would have been available if the the number
     * had not been reduced.*/
    public static final String EP_NUM_AVAILABLE = "numAvailable";
    /** The original Request URI of the request. */
    public static final String EP_REQUEST_URI = "requestUri";
    /** The duration of the request (as computed internally). */
    public static final String EP_DURATION = "duration";
    /** The items to be returned. */
    public static final String EP_ITEMS = "items";
    /** The name of the field that defines the input type for the endpoint. */
    public static final String EP_INPUT_TYPE = "endpointInputType";
    /** The name of the field that defines the output type for the endpoint. */
    public static final String EP_OUTPUT_TYPE = "endpointOutputType";

    /** The allowable HTTP methods. */
    public static final String EP_GET = "GET";
    public static final String EP_POST = "POST";
    public static final String EP_PUT = "PUT";

    public static final List<String> EP_ALLOWABLE_HTTP_METHODS = List.of(EP_GET, EP_POST, EP_PUT);


}
