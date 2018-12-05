package org.dynamicruntime.schemadef;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.startup.ServiceInitializer;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;
import static org.dynamicruntime.schemadef.LogSchema.*;

@SuppressWarnings("WeakerAccess")
public class DnSchemaService implements ServiceInitializer {
    public static final String DN_SCHEMA_SERVICE = DnSchemaService.class.getSimpleName();
    public DnRawSchemaStore rawSchemaStore;
    public AtomicReference<DnSchemaStore> schemaStore = new AtomicReference<>();
    public boolean isInit = false;

    public static DnSchemaService get(DnCxt cxt) {
        var obj = cxt.instanceConfig.get(DN_SCHEMA_SERVICE);
        return (obj instanceof DnSchemaService) ? (DnSchemaService)obj : null;
    }

    @Override
    public String getServiceName() {
        return DN_SCHEMA_SERVICE;
    }

    public void onCreate(DnCxt cxt) throws DnException {
        rawSchemaStore = DnRawSchemaStore.get(cxt);
        if (rawSchemaStore == null) {
            throw new DnException("Raw schema store not create for *DnSchemaService*.");
        }
        rawSchemaStore.builders.put(EP_ENDPOINT, this::buildEndpointType);

        // Add some built-in types.
        var count = DnRawType.mkSubType(DN_COUNT, DN_INTEGER)
                .setOption(DN_MIN, 0);
        var corePckg = DnRawSchemaPackage.mkPackage("DnSchemaServiceCore", DN_CORE_NAMESPACE,
                mList(count));
        rawSchemaStore.addPackage(corePckg);
    }

    @Override
    public void checkInit(DnCxt cxt) throws DnException {
        if (isInit) {
            return;
        }

        // Create the non-raw schema store..
        createSchemaStore(cxt);

        isInit = true;
    }

    public DnSchemaStore getSchemaStore() {
        return schemaStore.get();
    }

    public void createSchemaStore(DnCxt cxt) throws DnException {
        log.debug(cxt, "Turning raw schema into read only Java objects.");
        synchronized (this) {
            // Eventually we will extract DnEndpoints and DnTables as well.
            Map<String,DnType> dnTypes = mMapT();
            Map<String,DnEndpoint> endpoints = mMapT();
            for (DnRawType rawTypeIn : rawSchemaStore.rawTypes.values()) {
                String builder = getOptStr(rawTypeIn.model, DN_BUILDER);
                DnRawType rawType = rawTypeIn;
                if (builder != null) {
                    DnBuilder builderFunction = rawSchemaStore.builders.get(builder);
                    if (builderFunction == null) {
                        throw DnException.mkConv(String.format("Unable to find implementation of builder " +
                                "%s for type %s.", builder, rawTypeIn.name));
                    }
                    rawType = builderFunction.buildType(cxt, rawTypeIn);
                    rawType.finish();
                }
                DnType dnType = DnType.extract(rawType.model, rawSchemaStore.rawTypes);
                dnTypes.put(dnType.name, dnType);
                boolean isEndpoint = getBoolWithDefault(dnType.model, DN_IS_ENDPOINT, false);
                if (isEndpoint) {
                    String functionName = getReqStr(dnType.model, EP_FUNCTION);
                    var endpointFunction = rawSchemaStore.functions.get(functionName);
                    if (endpointFunction == null) {
                        throw DnException.mkConv(String.format("Unable to find function %s " +
                                "for dnType %s.", functionName, rawTypeIn.name));
                    }
                    DnEndpoint endpoint = DnEndpoint.extract(dnType, endpointFunction);
                    endpoints.put(endpoint.path, endpoint);
                }
            }
            schemaStore.set(new DnSchemaStore(dnTypes, endpoints));
        }
    }

    /** Builds an endpoint type from specialized inputs in extra attributes of
     * a raw type. See the *DynamicType.md* for a description of the transformation.
     * Note: This method is likely to grow in complexity over time as more subtleties get added
     * to the general endpoint functionality. */
    public DnRawType buildEndpointType(@SuppressWarnings("unused") DnCxt cxt, DnRawType epInputType)
            throws DnException {
        var inModel = epInputType.model;
        var namespace = epInputType.namespace;
        String inputTypeRef = DnTypeUtils.applyNamespace(namespace, getReqStr(inModel, EP_INPUT_TYPE_REF));
        String outputTypeRef = DnTypeUtils.applyNamespace(namespace, getReqStr(inModel, EP_OUTPUT_TYPE_REF));

        DnRawField inputField = DnRawField.mkField(EP_INPUT_TYPE,
                "Endpoint Input Type", "The definition of the validation and transformation to " +
                        "be applied to input data for the endpoint.");
        DnRawField outputField = DnRawField.mkField(EP_OUTPUT_TYPE, "Endpoint Output Type",
                "The specification of the allowable output for this endpoint.");
        DnRawField requestUri = DnRawField.mkReqField(EP_REQUEST_URI, "Request URI",
                "The request URI that make this request.");
        DnRawField nonce = DnRawField.mkReqField(EP_NONCE, "Security Nonce",
                "A random length string to create randomness in the encrypted version of the output.");
        DnRawField duration = DnRawField.mkReqField(EP_DURATION, "Duration in Milliseconds",
                "The time taken to perform the request in milliseconds.").setTypeRef(DN_FLOAT);

        if (getBoolWithDefault(inModel, EP_IS_LIST_RESPONSE, false)) {
            int defaultLimit = 100;
            // More complex result.
            var inputType = DnRawType.mkSubType(inputTypeRef);
            var limitType = DnRawType.mkSubType(DN_COUNT).setOption(DN_MAX, 20000);
            DnRawField limit = DnRawField.mkField(EP_LIMIT, "Limit On Results",
                    "The maximum number of items that can be returned.")
                    .setTypeDef(limitType)
                    .setOption(DN_DEFAULT_VALUE, defaultLimit);

            inputType.addField(limit);
            inputField.setTypeDef(inputType);

            DnRawField numItems = DnRawField.mkReqField(EP_NUM_ITEMS, "Number of Items",
                    "Number of items returned.").setTypeRef(DN_COUNT);
            DnRawField items = DnRawField.mkReqField(EP_ITEMS, "Items",
                    "Items returned by endpoint.")
                    .setTypeRef(outputTypeRef).setOption(DN_IS_LIST, true);
            var outputType = DnRawType.mkType(mList(numItems, requestUri, nonce, duration, items));
            outputField.setTypeDef(outputType);
        } else {
            // Endpoints always use anonymous types at their core.
            inputField.setTypeDef(DnRawType.mkSubType(inputTypeRef));
            var outputType = DnRawType.mkSubType(outputTypeRef);
            outputType.addFields(mList(requestUri, nonce, duration));
            outputField.setTypeDef(outputType);
        }

        DnRawType endpointType = DnRawType.mkType(epInputType.name, null);
        endpointType.model.putAll(epInputType.model);
        endpointType.addFields(mList(inputField, outputField));
        endpointType.setOption(DN_IS_ENDPOINT, true);
        return endpointType;
    }
}
