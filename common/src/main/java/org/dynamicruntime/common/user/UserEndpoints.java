package org.dynamicruntime.common.user;

import org.dynamicruntime.context.UserProfile;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.request.DnRequestCxt;
import org.dynamicruntime.schemadef.DnEndpointFunction;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.dynamicruntime.schemadef.DnEndpointFunction.mkEndpoint;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;
import static org.dynamicruntime.user.UserConstants.*;
import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;

@SuppressWarnings("WeakerAccess")
public class UserEndpoints {
    public static void adminQueryInfo(DnRequestCxt requestCxt) throws DnException {
        var cxt = requestCxt.cxt;
        var data = requestCxt.requestData;
        var userId = getOptLong(data, USER_ID);
        var username = getOptStr(data, AUTH_USERNAME);
        var primaryId = getOptStr(data, AUTH_USER_PRIMARY_ID);

        var userService = Objects.requireNonNull(UserService.get(cxt));
        AuthUserRow row;
        if (userId != null) {
            row = userService.queryUserId(cxt, userId);
        } else if (username != null) {
            row = userService.queryUsername(cxt, username);
        } else if (primaryId != null) {
            row = userService.queryPrimaryId(cxt, primaryId);
        } else {
            throw DnException.mkInput(String.format("Endpoint requires one of *%s*, *%s*, or *%s*.",
                    USER_ID, AUTH_USERNAME, AUTH_USER_PRIMARY_ID));
        }
        if (row == null) {
            return;
        }
        UserProfile up = new UserProfile(row.userId, row.account, row.groupName, row.roles);

        userService.loadProfileRecord(cxt, up, true);
        Map<String,Object> result = cloneMap(row.data);

        // Overlay profile raw row data on top of raw auth row data to create response.
        result.putAll(up.data);
        requestCxt.listResponse = mList(result);
    }

    public static void selfQueryInfo(DnRequestCxt requestCxt) throws DnException {
        var cxt = requestCxt.cxt;
        if (cxt.userProfile == null) {
            throw new DnException("User profile not present when expected.");
        }

        // This call is based on what we got during the request initialization process,
        // which means there is a good chance that no database requests were made,
        // making this is cheap call.
        var profileReport = cxt.userProfile.toMap();
        requestCxt.mapResponse.putAll(profileReport);
    }

    public static void selfSetData(DnRequestCxt requestCxt) throws DnException {
        var cxt = requestCxt.cxt;
        var data = requestCxt.requestData;
        long userId = cxt.userProfile.userId;
        var userService = Objects.requireNonNull(UserService.get(cxt));
        AuthAllUserData allUserData = AuthUserUtil.mkAllUserData(userId, requestCxt.webRequest);
        allUserData.profile = cxt.userProfile;
        userService.updateUserData(cxt, allUserData, data);

        AuthUserUtil.setUpdatedProfileResponse(requestCxt, allUserData);
    }

    public static List<DnEndpointFunction> getFunctions() {
        return mList(
                mkEndpoint(ADMIN_USER_INFO, UserEndpoints::adminQueryInfo),
                mkEndpoint(SELF_USER_INFO, UserEndpoints::selfQueryInfo),
                mkEndpoint(SELF_SET_DATA, UserEndpoints::selfSetData));
    }

}
