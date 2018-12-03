package org.dynamicruntime.schemadef;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;

@FunctionalInterface
public interface DnBuilder {
    DnRawType buildType(DnCxt cxt, DnRawType input) throws DnException;
}
