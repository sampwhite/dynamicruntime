package org.dynamicruntime.common.user;

import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.request.DnRequestCxt;
import org.dynamicruntime.schemadef.DnEndpointFunction;
import org.dynamicruntime.user.UserContact;

import static org.dynamicruntime.user.UserConstants.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;
import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;


import java.util.List;
import java.util.Objects;

import static org.dynamicruntime.schemadef.DnEndpointFunction.mkEndpoint;
import static org.dynamicruntime.util.DnCollectionUtil.mList;

@SuppressWarnings("WeakerAccess")
public class AuthUserEndpoints {
    /** Authenticates user from browser (setting auth cookie) using a token. */
    public static void authUsingToken(DnRequestCxt requestCxt) throws DnException {
        var cxt = requestCxt.cxt;
        var data = requestCxt.requestData;
        var userService = Objects.requireNonNull(UserService.get(requestCxt.cxt));
        String authId = getReqStr(data, AUTH_ID);
        String tokenData = getReqStr(data, AUTH_TOKEN);
        userService.authUsingAdminToken(cxt, authId, tokenData, requestCxt.webRequest);
        requestCxt.webRequest.setAuthCookieOnResponse(true);
        long userId = Objects.requireNonNull(cxt.userProfile).userId;
        requestCxt.mapResponse.putAll(mMap(AUTH_ID, authId, USER_ID, userId,
                AUTH_USERNAME, cxt.userProfile.publicName, AUTH_LOGGED_IN_USER, true));
    }

    public static void logout(DnRequestCxt requestCxt) {
        var cxt = requestCxt.cxt;
        requestCxt.webRequest.setIsLogout(true);
        var userProfile = cxt.userProfile;
        boolean didLogout = false;
        if (userProfile != null && userProfile.userId > 0) {
            didLogout = true;
            requestCxt.mapResponse.putAll(mMap(AUTH_ID, userProfile.authId, USER_ID, userProfile.userId,
                    AUTH_USERNAME, userProfile.publicName));
        }
        requestCxt.mapResponse.put(AUTH_LOGGED_OUT_USER, didLogout);

    }

    public static void createFormToken(DnRequestCxt requestCxt) throws DnException {
        var cxt = requestCxt.cxt;
        var formHandler = Objects.requireNonNull(UserService.get(requestCxt.cxt)).formHandler;
        var respData = formHandler.generateFormTokenData(cxt, null);
        requestCxt.mapResponse.putAll(respData);
    }

    public static void adminCreateFormToken(DnRequestCxt requestCxt) throws DnException {
        var cxt = requestCxt.cxt;
        var formHandler = Objects.requireNonNull(UserService.get(requestCxt.cxt)).formHandler;
        String username = getReqStr(requestCxt.requestData, AUTH_USERNAME);
        var respData = formHandler.createLoginDataForEmulation(cxt, username);
        requestCxt.mapResponse.putAll(respData);
    }

    public static void sendNewContactVerifyEmail(DnRequestCxt requestCxt) throws DnException {
        var cxt = requestCxt.cxt;
        var data = requestCxt.requestData;

        UserContact contact = UserContact.extract(data);

        String formAuthToken = getReqStr(data, FM_FORM_AUTH_TOKEN);
        String formAuthCode = getReqStr(data, FM_FORM_AUTH_CODE);
        var formHandler = Objects.requireNonNull(UserService.get(cxt)).formHandler;
        formHandler.sendVerifyTokenToContact(cxt, contact, formAuthToken, formAuthCode);
        requestCxt.mapResponse.put(CT_CONTACT_TYPE, contact.cType);
        requestCxt.mapResponse.put(CT_CONTACT_ADDRESS, contact.address);
    }

    @SuppressWarnings("Duplicates")
    public static void sendUserVerifyEmail(DnRequestCxt requestCxt) throws DnException {
        var cxt = requestCxt.cxt;
        var data = requestCxt.requestData;

        String username = getReqStr(data, AUTH_USERNAME);
        String formAuthToken = getReqStr(data, FM_FORM_AUTH_TOKEN);
        String formAuthCode = getReqStr(data, FM_FORM_AUTH_CODE);
        var formHandler = Objects.requireNonNull(UserService.get(cxt)).formHandler;
        formHandler.sendVerifyTokenToUser(cxt, username, formAuthToken, formAuthCode);
        requestCxt.mapResponse.put(AUTH_USERNAME, username);
    }

    public static void createInitialUser(DnRequestCxt requestCxt) throws DnException {
        var cxt = requestCxt.cxt;
        var data = requestCxt.requestData;
        var formHandler = Objects.requireNonNull(UserService.get(cxt)).formHandler;

        UserContact contact = UserContact.extract(data);
        String formAuthToken = getReqStr(data, FM_FORM_AUTH_TOKEN);
        String verifyCode = getReqStr(data, FM_VERIFY_CODE);
        AuthUserRow userRow = formHandler.createInitialUser(cxt, contact, formAuthToken, verifyCode);
        // Returning raw row (minus hashed password).
        requestCxt.mapResponse.putAll(userRow.data);
    }

    public static void setAuthLoginData(DnRequestCxt requestCxt) throws DnException {
        var cxt = requestCxt.cxt;
        var data = requestCxt.requestData;
        var formHandler = Objects.requireNonNull(UserService.get(cxt)).formHandler;
        long userId = getReqLong(data, USER_ID);
        String username = getReqStr(data, AUTH_USERNAME);
        String password = getReqStr(data, FM_PASSWORD);
        String formAuthToken = getReqStr(data, FM_FORM_AUTH_TOKEN);
        String verifyCode = getReqStr(data, FM_VERIFY_CODE);

        AuthAllUserData allData = AuthUserUtil.mkAllUserData(userId, requestCxt.webRequest);

        formHandler.setLoginDataAndCreateProfile(cxt, allData, username, password, formAuthToken, verifyCode);
        AuthUserUtil.setLoggedInResponse(requestCxt, allData);
    }


    @SuppressWarnings("Duplicates")
    public static void authLoginByCode(DnRequestCxt requestCxt) throws DnException {
        var cxt = requestCxt.cxt;
        var data = requestCxt.requestData;

        String formAuthToken = getReqStr(data, FM_FORM_AUTH_TOKEN);
        String username = getReqStr(data, AUTH_USERNAME);
        String passwordToChange = getOptStr(data, FM_PASSWORD);
        String verifyCode = getReqStr(data, FM_VERIFY_CODE);

        var formHandler = Objects.requireNonNull(UserService.get(cxt)).formHandler;
        // By setting userId to 0, we force the login code to query for it using the username.
        AuthAllUserData allData = AuthUserUtil.mkAllUserData(0, requestCxt.webRequest);
        formHandler.loginByVerifyCode(cxt, allData, username, passwordToChange, formAuthToken, verifyCode);
        AuthUserUtil.setLoggedInResponse(requestCxt, allData);
    }

    @SuppressWarnings("Duplicates")
    public static void authLoginByPassword(DnRequestCxt requestCxt) throws DnException {
        var cxt = requestCxt.cxt;
        var data = requestCxt.requestData;

        String formAuthToken = getReqStr(data, FM_FORM_AUTH_TOKEN);
        String username = getReqStr(data, AUTH_USERNAME);
        String password = getReqStr(data, FM_PASSWORD);

        var formHandler = Objects.requireNonNull(UserService.get(cxt)).formHandler;
        // By setting userId to 0, we force the login code to query for it using the username.
        AuthAllUserData allData = AuthUserUtil.mkAllUserData(0, requestCxt.webRequest);
        formHandler.loginByPassword(cxt, allData, username, password, formAuthToken);
        AuthUserUtil.setLoggedInResponse(requestCxt, allData);
    }

    public static List<DnEndpointFunction> getFunctions() {
        return mList(
                mkEndpoint(AUTH_EP_TOKEN_LOGIN, AuthUserEndpoints::authUsingToken),
                mkEndpoint(AUTH_EP_LOGOUT, AuthUserEndpoints::logout),
                mkEndpoint(AUTH_CREATE_FORM_TOKEN, AuthUserEndpoints::createFormToken),
                mkEndpoint(ADMIN_CREATE_FORM_TOKEN, AuthUserEndpoints::adminCreateFormToken),
                mkEndpoint(AUTH_SEND_NEW_CONTACT_VERIFY_CODE, AuthUserEndpoints::sendNewContactVerifyEmail),
                mkEndpoint(AUTH_SEND_USER_VERIFY_CODE, AuthUserEndpoints::sendUserVerifyEmail),
                mkEndpoint(AUTH_CREATE_INITIAL_USER, AuthUserEndpoints::createInitialUser),
                mkEndpoint(AUTH_SET_LOGIN_DATA, AuthUserEndpoints::setAuthLoginData),
                mkEndpoint(AUTH_LOGIN_BY_CODE, AuthUserEndpoints::authLoginByCode),
                mkEndpoint(AUTH_LOGIN_BY_PASSWORD, AuthUserEndpoints::authLoginByPassword));
    }
}
