package org.dynamicruntime.schemadef;

import org.dynamicruntime.exception.DnException;

import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;

import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class DnEndpoint {
    public final String method;
    public final String path;
    public final String description;
    public final DnEndpointFunction endpointFunction;
    public final DnType inType;
    public final DnType outType;
    public final boolean isListResponse;
    public final Map<String,Object> model;

    public DnEndpoint(String method, String path, String description, DnEndpointFunction endpointFunction,
            DnType inType, DnType outType, boolean isListResponse, Map<String,Object> model) {
        this.method = method;
        this.path = path;
        this.description = description;
        this.endpointFunction = endpointFunction;
        this.inType = inType;
        this.outType = outType;
        this.isListResponse = isListResponse;
        this.model = model;
    }

    public static DnEndpoint extract(DnType dnType, DnEndpointFunction endpointFunction) throws DnException {
        String path = getReqStr(dnType.model, EP_PATH);
        String method = getOptStr(dnType.model, EP_HTTP_METHOD);
        if (method == null || !EP_ALLOWABLE_HTTP_METHODS.contains(method)) {
            throw DnException.mkConv(String.format("Method %s is not one of the allowable methods " +
                    "for the endpoint at path %s.", method, path));
        }
        String description = getOptStr(dnType.model, DN_DESCRIPTION);
        DnField inField = dnType.fieldsByName.get(EP_INPUT_TYPE);
        DnField outField = dnType.fieldsByName.get(EP_OUTPUT_TYPE);
        if (inField == null || outField == null || inField.anonType == null || outField.anonType == null) {
             throw DnException.mkConv("Properly defined fields for endpoint " + path + " were not defined.");
        }
        boolean isListResponse = getBoolWithDefault(dnType.model, EP_IS_LIST_RESPONSE, false);
        return new DnEndpoint(method, path, description, endpointFunction, inField.anonType, outField.anonType,
                isListResponse, dnType.model);
    }
}
