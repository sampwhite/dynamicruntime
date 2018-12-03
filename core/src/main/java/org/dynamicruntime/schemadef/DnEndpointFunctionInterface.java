package org.dynamicruntime.schemadef;

import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.request.DnRequestCxt;

public interface DnEndpointFunctionInterface {
    void executeRequest(DnRequestCxt requestCxt) throws DnException;
}
