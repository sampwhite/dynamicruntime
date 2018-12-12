package org.dynamicruntime.sql;

import org.dynamicruntime.exception.DnException;

public interface SqlFunction {
    void execute() throws DnException;
}
