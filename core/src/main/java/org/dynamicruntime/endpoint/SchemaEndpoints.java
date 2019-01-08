package org.dynamicruntime.endpoint;

import org.dynamicruntime.request.DnRequestCxt;
import org.dynamicruntime.schemadef.DnEndpoint;
import org.dynamicruntime.schemadef.DnEndpointFunction;
import org.dynamicruntime.schemadef.DnTable;
import org.dynamicruntime.schemadef.DnType;

import java.util.Comparator;
import java.util.List;

import static org.dynamicruntime.schemadata.CoreConstants.*;
import static org.dynamicruntime.schemadef.DnEndpointFunction.mkEndpoint;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.EP_ENDPOINT;
import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.util.ConvertUtil.*;

@SuppressWarnings("WeakerAccess")
public class SchemaEndpoints {
    static void getSchemaTypes(DnRequestCxt requestCxt) {
        var reqData = requestCxt.requestData;
        String namespace = getOptStr(reqData, SS_NAMESPACE);
        if (namespace != null) {
            namespace = namespace + ".";
        }
        String dnTypeName = getOptStr(reqData, SS_DN_TYPE_NAME);
        var types = requestCxt.cxt.getSchema().types;
        List<DnType> retVal = mList();
        if (dnTypeName != null && dnTypeName.indexOf('.') > 0) {
            var dnType = types.get(dnTypeName);
            if (dnType != null) {
                retVal.add(dnType);
            }
        } else {
            if (dnTypeName != null) {
                dnTypeName = "." + dnTypeName;
            }
            for (var dt : types.values()) {
                if (namespace != null) {
                    if (!dt.name.startsWith(namespace)) {
                        continue;
                    }
                }
                if (dnTypeName != null) {
                    if (!dt.name.endsWith(dnTypeName)) {
                        continue;
                    }
                }
                retVal.add(dt);
            }
        }
        retVal.sort(Comparator.comparing(dt -> dt.name));
        requestCxt.listResponse = nMapSimple(retVal, DnType::toMap);
    }

    public static void getEndpointDefinitions(DnRequestCxt requestCxt) {
        var reqData = requestCxt.requestData;
        String endpointPath = getOptStr(reqData, EP_ENDPOINT);
        List<DnEndpoint> retVal = mList();
        if (endpointPath != null) {
            var endpoint = requestCxt.cxt.getSchema().endpoints.get(endpointPath);
            if (endpoint != null) {
                retVal.add(endpoint);
            }
        } else {
            String prefix = getOptStr(reqData, SS_ENDPOINT_PATH_PREFIX);
            var endpoints = requestCxt.cxt.getSchema().endpoints.values();
            for (var ep : endpoints) {
                if (prefix != null && !ep.path.startsWith(prefix)) {
                    continue;
                }
                retVal.add(ep);
            }
            retVal.sort(Comparator.comparing(ep -> ep.path));
        }
        requestCxt.listResponse = nMapSimple(retVal, DnEndpoint::toMap);
    }

    public static void getTableDefinitions(DnRequestCxt requestCxt) {
        var reqData = requestCxt.requestData;
        String prefix = getOptStr(reqData, SS_TABLE_NAME_PREFIX);
        var tables = requestCxt.cxt.getSchema().tables.values();
        List<DnTable> retVal = mList();
        for (var tb : tables) {
            if (prefix != null && !tb.tableName.startsWith(prefix)) {
                continue;
            }
            retVal.add(tb);
        }
        retVal.sort(Comparator.comparing(tb -> tb.tableName));
        requestCxt.listResponse = nMapSimple(retVal, DnTable::toMap);
    }

    public static List<DnEndpointFunction> getFunctions() {
        return mList(
                mkEndpoint(SS_GET_TYPE_DEFINITIONS, SchemaEndpoints::getSchemaTypes),
                mkEndpoint(SS_GET_ENDPOINT_DEFINITIONS, SchemaEndpoints::getEndpointDefinitions),
                mkEndpoint(SS_GET_TABLE_DEFINITIONS, SchemaEndpoints::getTableDefinitions));
    }
}
