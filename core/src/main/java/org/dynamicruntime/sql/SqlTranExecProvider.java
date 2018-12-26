package org.dynamicruntime.sql;

import org.dynamicruntime.exception.DnException;

public interface SqlTranExecProvider {
    public void insert() throws DnException;
    public boolean lock() throws DnException;
    public void execute() throws DnException;
}
