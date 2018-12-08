package org.dynamicruntime.schemadef;

import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.request.DnRequestCxt;

@SuppressWarnings("WeakerAccess")
public class DnEndpointFunction {
    public final String name;
    public final DnEndpointFunctionInterface functionInterface;

    public DnEndpointFunction(String name, DnEndpointFunctionInterface functionInterface) {
        this.name = name;
        this.functionInterface = functionInterface;
    }

    public void executeRequest(DnRequestCxt requestCxt) throws DnException {
        functionInterface.executeRequest(requestCxt);
    }

    /** Convenience method designed to be imported statically. */
    public static DnEndpointFunction mkEndpoint(String name, DnEndpointFunctionInterface functionInterface) {
         return new DnEndpointFunction(name, functionInterface);
    }
}
