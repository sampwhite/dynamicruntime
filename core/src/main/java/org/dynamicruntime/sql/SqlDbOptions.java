package org.dynamicruntime.sql;

/** Options on a database that vary on what type of database you are using. */
@SuppressWarnings("WeakerAccess")
public class SqlDbOptions {
    public boolean identifiersCaseSensitive;
    public boolean useTimezoneWithTz;
    public boolean hasSerialType;
    public boolean storesLowerCaseIdentifiersInSchema;
}
