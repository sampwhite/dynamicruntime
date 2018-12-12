package org.dynamicruntime.sql;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;

public interface SqlTopicInterface {
    SqlDatabase getDatabase();
    void init(DnCxt cxt) throws DnException;
}
