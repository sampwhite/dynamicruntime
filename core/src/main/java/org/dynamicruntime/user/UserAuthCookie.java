package org.dynamicruntime.user;

import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.util.StrUtil;

import static org.dynamicruntime.util.ConvertUtil.*;

import java.util.Date;
import java.util.List;

/** Represents the extracted contents of an authentication cookie. This may also be the extraction of
 * a temporary authentication token as well, which was passed in as a parameter.
 * This is a convenience class for encoding and decoding the authentication cookie. This
 * definition is in the core class because when the cookie definition changes, it tends
 * to have an impact system wide.
 *
 * The current definition could probably just have the grantingId. userId, and date fields with no other fields,
 * but in prior projects there has been utility in embedding additional information in the cookie.
 * For this reason, we give an example of what that might look like here.*/
@SuppressWarnings("WeakerAccess")
public class UserAuthCookie {
    // Every cookie starts with a single letter code. As the cookie encodes more data over
    // time, different letter codes represent different cookies used for different reasons.
    // Using a different code does not obsolete the usage of other codes.
    public static String STD_AUTH_COOKIE_CODE = "S";

    // Current active version. Incrementing the version does obsolete prior versions (new cookies should
    // be produced with the latest version).
    public static final int CURRENT_VERSION = 1;

    /** The single letter code identifying the type of cookie. */
    public final String code;
    /** The extracted version of the cookie. This follows the single character code and the version part of
     * the string is terminated by a '#'. */
    public final int version;
    public final long grantingUserId;
    public final long userId;
    public final String account;
    public final List<String> roles;
    public final String authId;
    public String publicName;
    public String groupName;
    public String shard;
    // Eventually will be set. This will use some type of custom encoding.
    public String authRulesAsStr;
    public int renewalCount;
    public final Date creationDate;
    public Date modifiedDate;
    public Date expireDate;

    public UserAuthCookie(String code, int version, long grantingUserId, long userId,
            String account, List<String> roles, String authId, Date creationDate) {
        this.code = code;
        this.version = version;
        this.grantingUserId = grantingUserId;
        this.userId = userId;
        this.account = account;
        this.roles = roles;
        this.authId = authId;
        this.creationDate = creationDate;
        this.modifiedDate = creationDate;
        this.expireDate = creationDate;
    }

    public static String getDateDiffAsString(Date base, Date in) {
        if (base == null || in == null) {
            return "";
        }
        long bt = base.getTime();
        long it = in.getTime();
        int mins = (int)((it - bt)/(60*1000));
        if (mins > 4*60) {
            return "" + (mins/60) + "h";
        }
        return "" + mins + "m";
    }

    public static Date getDateWithDiff(Date base, String diff) throws DnException {
        if (diff == null || diff.length() < 2) {
            return base;
        }
        int l = diff.length();
        String dv = diff.substring(0, l - 1);
        String dt = diff.substring(l - 1, l);
        int offset = (int)toReqLong(dv);
        if (dt.equals("h")) {
            offset = offset * 60;
        }
        return new Date(base.getTime() + offset * 60 * 1000L);
    }

    public String toString() {
        String md = getDateDiffAsString(creationDate, modifiedDate);
        String ed = getDateDiffAsString(modifiedDate, expireDate);
        return code + version + "," + grantingUserId + "," + userId + "," + s(account) + "," + l(roles) + "," +
                s(authId) + "," + s(publicName) + "," + s(groupName) + "," + s(shard) + "," +
                s(authRulesAsStr) + "," + renewalCount + "," + fmtObject(creationDate) + "," + md + "," + ed;
    }

    public static String s(String str) {
        return str != null ? str : "";
    }

    public static String l(List<String> lStr) {
        if (lStr == null || lStr.size() == 0) {
            return "";
        }
        return String.join(":", lStr);
    }

    public static UserAuthCookie extract(String str) throws DnException {
        if (str == null || str.length() < 2) {
            throw new DnException("Provided cookie is null or too small.");
        }
        String[] entries = str.split(",");
        String cv = entries[0];
        // For now we only support one type of cookie.
        if (!cv.startsWith(STD_AUTH_COOKIE_CODE) || cv.length() < 2) {
            throw new DnException("Cookie type is not supported by this application.");
        }
        if (entries.length < 14) {
            throw new DnException("Cookie is not properly formatted.");
        }
        String code = cv.substring(0, 1);
        int version = (int)toReqLong(cv.substring(1));
        long grantingUserId = toReqLong(entries[1]);
        long userId = toReqLong(entries[2]);
        String account = entries[3];
        List<String> roles = StrUtil.splitString(entries[4], ":");
        String authId = entries[5];
        String publicName = entries[6];
        String groupName = entries[7];
        String shard = entries[8];
        String authRulesAsStr = entries[9];
        int renewalCount = (int)toReqLong(entries[10]);
        Date creationDate = toReqDate(entries[11]);
        Date modifiedDate = getDateWithDiff(creationDate, entries[12]);
        Date expireDate = getDateWithDiff(modifiedDate, entries[13]);

        // Additional entries can be added as the cookie grows in data that is put into it.
        //....

        var uac = new UserAuthCookie(code, version, grantingUserId, userId, account, roles, authId, creationDate);
        // Mutable values.
        uac.publicName = publicName;
        uac.groupName = groupName;
        uac.shard = shard;
        uac.renewalCount = renewalCount;
        uac.authRulesAsStr = authRulesAsStr;
        uac.modifiedDate = modifiedDate;
        uac.expireDate = expireDate;
        return uac;
    }
}
