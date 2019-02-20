package org.dynamicruntime.schemadef;

import org.dynamicruntime.util.StrUtil;

import java.util.Map;

import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;

/** Construction mechanism for creating endpoints. */
@SuppressWarnings("WeakerAccess")
public class DnRawEndpoint implements DnRawTypeInterface {
    public final String path;
    public final Map<String,Object> epModel;

    public DnRawEndpoint(String path, Map<String,Object> epModel) {
        this.path = path;
        this.epModel = epModel;
    }

    @Override
    public DnRawType getRawType() {
        String name = getOptStr(epModel, DN_NAME);
        if (name == null) {
            // We turn path into name.
            name = StrUtil.turnPathIntoName(path);
            epModel.put(DN_NAME, name);
        }
        if (!epModel.containsKey(EP_HTTP_METHOD)) {
            epModel.put(EP_HTTP_METHOD, EPM_GET);
        }
        epModel.put(DN_IS_ENDPOINT, true);
        epModel.put(DN_BUILDER, EP_ENDPOINT);
        return new DnRawType(name, epModel);
    }

    public static DnRawEndpoint mkEndpoint(String method, String path, String function, String description, String inTypeRef, String outTypeRef) {
        var model = mMap(EP_HTTP_METHOD, method, EP_PATH, path, EP_FUNCTION, function, DN_DESCRIPTION, description,
                EP_INPUT_TYPE_REF, inTypeRef, EP_OUTPUT_TYPE_REF, outTypeRef);
        return new DnRawEndpoint(path, model);
    }

    public DnRawEndpoint setAttribute(String key, Object val) {
        epModel.put(key, val);
        return this;
    }

    public static DnRawEndpoint mkListEndpoint(String path, String function, String description,
            String inTypeRef, String outTypeRef) {
        return mkEndpoint(EPM_GET,path, function, description, inTypeRef, outTypeRef)
                .setAttribute(EP_IS_LIST_RESPONSE, true);
    }

    public static DnRawEndpoint mkSimpleListEndpoint(String path, String function, String description,
            String inTypeRef, String outTypeRef) {
        return mkEndpoint(EPM_GET, path, function, description, inTypeRef, outTypeRef)
                .setAttribute(EP_IS_LIST_RESPONSE, true).setAttribute(EP_NO_LIMIT_PARAMETER, true);
    }

    public DnRawEndpoint setMethod(String method) {
        epModel.put(EP_HTTP_METHOD, method);
        return this;
    }
}
