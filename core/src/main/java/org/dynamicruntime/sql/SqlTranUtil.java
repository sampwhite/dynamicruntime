package org.dynamicruntime.sql;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;

/**
 * Does a database transaction with retries. This logic is based on experience with actual behaviors
 * of various databases. One of the advantages of not using third party libraries to take care of
 * behaviors like this is that you can handle certain logic cases more delicately and capture common
 * retry patterns in a single block of code.
 */
public class SqlTranUtil {
    public static void doTran(SqlCxt sqlCxt, String tranName, SqlTranExecProvider provider) throws DnException {
        DnCxt cxt = sqlCxt.cxt;
        SqlDatabase sqlDb = sqlCxt.sqlDb;
        boolean[] didIt = {false};
        boolean doInsert = false;
        boolean didInsert = false;
        sqlCxt.didInsert = false;

        DnException lastException = null;
        for (int i = 0; i < 3 && !didIt[0]; i++) {
            if (doInsert) {
                try {
                    // Insert the row on which we wish to take a lock.
                    provider.insert();

                    doInsert = false;
                    didInsert = true;
                    sqlCxt.didInsert = true; // Advertise what we did to code that implements *execute*.
                } catch (DnException e) {
                    lastException = e;
                    if (!e.source.equals(DnException.DATABASE)) {
                        throw e;
                    }
                    continue;
                }
            }
            try {
                // Attempt to do transaction.
                sqlDb.withTran(cxt, () -> {
                    boolean lock = provider.lock();
                    if (lock) {
                        provider.execute();
                        didIt[0] = true;
                    }
                });
                // If no error but did not do work, then we need to insert a row to lock on.
                if (!didIt[0]) {
                    doInsert = true;
                }
            } catch (DnException e) {
                if (!e.canRetry()) {
                    throw e;
                }
                lastException = e;
            }
            if (doInsert && didInsert) {
                // Should not happen.
                throw new DnException(String.format("Could not take transaction lock after insert row for " +
                        "transaction %s.", tranName), lastException);
            }
        }
        if (!didIt[0]) {
            throw new DnException(
                    String.format("Unable to execute transaction %s after three attempts.", tranName),
                    lastException, DnException.INTERNAL_ERROR, DnException.DATABASE, DnException.CODE);
        }
    }
}
