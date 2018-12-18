package org.dynamicruntime.sql.topic;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.sql.SqlDatabase;

public interface SqlTopicInterface {
    SqlDatabase getDatabase();
    void init(DnCxt cxt) throws DnException;
}
