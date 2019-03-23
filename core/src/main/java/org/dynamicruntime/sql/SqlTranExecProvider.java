package org.dynamicruntime.sql;

import org.dynamicruntime.exception.DnException;

public interface SqlTranExecProvider {
    void insert() throws DnException;
    boolean lock() throws DnException;
    void execute() throws DnException;
}
