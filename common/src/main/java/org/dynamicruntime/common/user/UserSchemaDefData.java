package org.dynamicruntime.common.user;

import org.dynamicruntime.schemadef.DnRawField;
import org.dynamicruntime.schemadef.DnRawSchemaPackage;
import org.dynamicruntime.schemadef.DnRawTable;

import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.user.UserConstants.*;
import static org.dynamicruntime.schemadef.DnRawField.*;
import static org.dynamicruntime.schemadef.DnRawTable.*;
import static org.dynamicruntime.common.user.UserTableConstants.*;

@SuppressWarnings("WeakerAccess")
public class UserSchemaDefData {
    //
    // AuthUser - Holds facts necessary to do authentication and assign userGroup and shard.
    //

    static public DnRawField userId = mkReqField(USER_ID, "User Id",
            "The global unique counter value assigned to this user in this application instance.")
                .setTypeRef(DNT_COUNT);
    static public DnRawField userGroup = mkReqField(USER_GROUP, "User Group",
            "The logical group to which the user belongs and which determines the user's shard. " +
                    "The usage of this entity varies by application.");
    static public DnRawField userShard = mkField(USER_SHARD, "User Shard",
            "The software shard for data storage to which the user is assigned. Requests can " +
                    "potentially be forwarded or redirected to a node that is dedicated to the shard.");
    static public DnRawField userAccount = mkReqField(USER_ACCOUNT, "User Account",
            "The account to which the consumer belongs to. The account can " +
                    "dictate login approach used and the enabling or disabling of various options.");
    static public DnRawField userPrimaryId = mkReqField(AUTH_USER_PRIMARY_ID, "Primary User Identification Data",
            "The data about the user that can be used to uniquely find the user. In the default " +
                    "implementation this is the primary email address.");
    static public DnRawField username = mkReqField(AUTH_USERNAME, "Username",
            "The user's preferred name when showing the name to others. It must be unique in the " +
                    "database table. It can be used for doing simplified logins once the user has been provisioned.");
    static public DnRawField authUserData = mkReqField(AUTH_USER_DATA, "Auth User Data",
            "Auth user data including contacts, passwords, and role grants encoded as a string.")
            .setTypeRef(DNT_MAP);

    static public DnRawTable authUserTable = mkStdTable(UT_TB_AUTH_USERS,
            "Main table for authenticating consumers.",
            mList(userId, userGroup, userAccount, userShard, userPrimaryId, username, authUserData), null)
            .setCounterField(USER_ID)
            .setTopLevel()
            .setComplexIndexes(mList(
                    mkComplexIndex(null, mList(AUTH_USER_PRIMARY_ID), mMap(TBI_UNIQUE_INDEX, true)),
                    mkComplexIndex(null, mList(AUTH_USERNAME), mMap(TBI_UNIQUE_INDEX, true)),
                    mkComplexIndex(null, mList(USER_ACCOUNT, MODIFIED_DATE), null),
                    mkComplexIndex(null, mList(USER_GROUP, MODIFIED_DATE), null)
            ));

    //
    // AuthContacts - Holds contacts used during the authentication process.
    //
    static public DnRawField contactId = mkReqField(CT_CONTACT_ID, "Contact Id",
            "An incrementing counter uniquely identifying a contact when combined with the userId.")
                .setTypeRef(DNT_COUNT).setAttribute(DN_IS_AUTO_INCREMENTING, true);
    static public DnRawField contactType = mkReqField(CT_CONTACT_TYPE, "Contact Type",
            "Auth contact type, either *mobile* or *email*.");
    static public DnRawField contactAddress = mkReqField(CT_CONTACT_ADDRESS, "Contact Address",
            "The normalized address for the contact. Either a phone number if mobile, or an email address if " +
                    "an email.");
    static public DnRawField contactAuthCode = mkField(AUTH_CODE, "Contact Auth Code",
            "An auth code to be played back by the user.");
    static public DnRawField contactAuthExpiration = mkDateField(AUTH_ACTION_EXPIRATION,
            "Contact Verification Expiration Date", "The date by which a user must get back with " +
                    "an auth code.");
    static public DnRawField contactVerified = mkReqBoolField(VERIFIED, "Contact Verified",
            "Whether a communication has been sent to the contact address and the sent information was " +
                    "used to verify that the address is a working address.");
    static public DnRawTable authContactTable = mkStdUserTable(UT_TB_AUTH_CONTACTS,
            "Contacts used to do authentication.",
            mList(contactId, contactType, contactAddress, contactAuthCode, contactAuthExpiration, contactVerified),
            mList(USER_ID, CT_CONTACT_ADDRESS)/* Primary Key, note contactId is *not* the primary key. */)
            .setSimpleIndexes(mList(mList(CT_CONTACT_ID),mList(USER_ID, AUTH_ACTION_EXPIRATION, AUTH_CODE)));

    //
    // AuthLoginSources - Location from where authentication originated.
    //
    static public DnRawField sourceGuid = mkReqField(LS_SOURCE_GUID, "Login Source GUID",
            "A generated unique identifier attached to the requesting agent. For browsers, the GUID is " +
                    "a cookie set on the browser.");
    static public DnRawField sourceData = mkReqField(LS_SOURCE_DATA, "Login Source Data",
            "Any captured information about the login source.").setTypeRef(DNT_MAP);
    static public DnRawField sourceAuthCode = mkField(AUTH_CODE, "Source Auth Code",
            "Auth code assigned to the source which is then sent to the user by a verified contact " +
                    "address that is trusted.");
    static public DnRawField sourceAuthExpiration = mkDateField(AUTH_ACTION_EXPIRATION,
            "Login Source Code Expiration", "The expiration date on the auth code sent to " +
                    "the user.");
    static public DnRawField sourceVerified = mkReqBoolField(VERIFIED, "Login Source Verified",
            "Whether the login source has been verified as trusted.");
    static public DnRawField verifyExpiration = mkDateField(LS_VERIFY_EXPIRATION, "Verify Expiration",
            "Date when verification for login source needs to be verified.");
    static public DnRawTable authLoginSourcesTable = mkStdUserTable(UT_TB_AUTH_LOGIN_SOURCES,
            "Sources or devices from which logins originate.",
            mList(sourceGuid, sourceData, sourceAuthCode, sourceAuthExpiration, sourceVerified, verifyExpiration),
            mList(LS_SOURCE_GUID));

    //
    // UserProfiles - User profile information.
    //
    static public DnRawField userLocale = mkField(UP_USER_LOCALE, "User Locale",
            "The user's preferred locale for language and presentation of UI.");
    static public DnRawField userTimezone = mkField(UP_USER_TIMEZONE, "User Timezone",
            "The user's preferred timezone for presentation and entering of date information.");
    static public DnRawField userData = mkField(UP_USER_DATA, "User Data",
            "General user profile data including preference and contact information.").setTypeRef(DNT_MAP);
    static public DnRawTable userProfileTable = mkStdUserTable(UT_TB_USER_PROFILES,
            "User profile information for each user.",
            mList(userLocale, userTimezone, userData),
            mList(USER_ID))
            .setTopLevel();

    static public DnRawSchemaPackage getPackage() {
        return DnRawSchemaPackage.mkPackage("UserSchema", USER_NAMESPACE,
                mList(authUserTable, authContactTable, authLoginSourcesTable, userProfileTable));
    }
}
