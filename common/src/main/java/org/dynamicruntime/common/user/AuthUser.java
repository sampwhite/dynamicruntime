package org.dynamicruntime.common.user;

import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.user.UserAuthData;

import java.util.List;
import java.util.Map;

import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;

import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;
import static org.dynamicruntime.user.UserConstants.*;

/** Convenience wrapper for accessing auth database tables. Notice the emphasis on convenience, not on *contract*.
 * If the data schema for auth users changes over time, it is the responsibility of the class to be able to
 * support both old and new schemas simultaneously by mapping or providing defaults as appropriate. */
@SuppressWarnings({"WeakerAccess", "unused"})
public class AuthUser {
    public final long userId;
    public final String account;
    public final String primaryId;
    // Can be mutated, but only if it stays unique.
    public String username;
    // Can be mutated.
    public String groupName;
    // This can be mutated, especially during initial user provisioning. It can also be null.
    public String shard;
    public List<String> roles;
    public String encodedPassword;
    public String passwordEncodingRule;
    // Data from the userData column.
    public Map<String,Object> authUserData;
    /* Original source data from AuthUser table and data that will get put back. */
    public Map<String,Object> data;

    // Additional data from various authentication methods.
    public String authId;
    public Map<String,Object> authRules;

    public AuthUser(long userId, String account, String primaryId) {
        this.userId = userId;
        this.account = account;
        this.primaryId = primaryId;
    }

    public static AuthUser extract(Map<String,Object> data) throws DnException {
        // Manually extract fields. We deliberately avoid reflection magic since it can impose large restrictions,
        // add layers of obfuscation, and make it difficult to chase down data problems just to save a few minutes
        // of extra coding. Note how we promote fields out of the *authUserData* map object into top level fields.
        // The design of our Java class is not dictated by the design of our data storage.
        long userId = getReqLong(data, USER_ID);
        String account = getReqStr(data, USER_ACCOUNT);
        String primaryId = getReqStr(data, AUTH_USER_PRIMARY_ID);
        AuthUser au = new AuthUser(userId, account, primaryId);
        au.username = getReqStr(data, AUTH_USERNAME);
        au.groupName = getReqStr(data, USER_GROUP);
        au.shard = getOptStr(data, USER_SHARD);
        var userData = getMapDefaultEmpty(data, AUTH_USER_DATA);
        au.roles = getOptListOfStrings(userData, AUTH_ROLES);
        if (au.roles == null) {
            au.roles = mList(ROLE_USER);
        }
        au.encodedPassword = getOptStr(userData, AUTH_ENCODED_PASSWORD);
        au.passwordEncodingRule = getOptStr(userData, AUTH_PASSWORD_ENCODING_RULE);
        au.authUserData = userData;
        au.data = data;

        return au;
    }

    /** Used to create the initially provisioned row in the database. */
    public static Map<String,Object> mkInitialUser(String primaryId, String account, String groupName,
            String role) {
        var authUserData = mMap(AUTH_ROLES, mList(role));
        var placeholderUsername = AUTH_USERNAME_TMP_PREFIX + primaryId;
        return mMap(AUTH_USER_PRIMARY_ID, primaryId, AUTH_USERNAME, placeholderUsername,
                USER_ACCOUNT, account, USER_GROUP, groupName, AUTH_USER_DATA, authUserData);
    }

    public boolean needsRealUsername() {
        return username.startsWith("@");
    }

    public Map<String,Object> toMap() {
        // Take advantage of the fact that *mMap()* removes nulls.
        var newAuthUserData = mMap(AUTH_ROLES, roles, AUTH_ENCODED_PASSWORD, encodedPassword,
                AUTH_PASSWORD_ENCODING_RULE, passwordEncodingRule);
        Map<String,Object> curAuthUserData;
        if (authUserData == null) {
            curAuthUserData = newAuthUserData;
        } else {
            curAuthUserData = cloneMap(authUserData);
            curAuthUserData.putAll(newAuthUserData);
        }

        var newData = mMap(AUTH_USERNAME, username, USER_GROUP, groupName, USER_SHARD, shard,
                AUTH_USER_DATA, curAuthUserData);
        var retData = cloneMap(data);
        retData.putAll(newData);
        return retData;
    }

    public void populateAuthData(UserAuthData authData) {
        authData.userId = userId;
        authData.account = account;
        authData.userGroup = groupName;
        authData.authId = authId;
        authData.roles = roles;
        authData.authRules = authRules;
    }
}
