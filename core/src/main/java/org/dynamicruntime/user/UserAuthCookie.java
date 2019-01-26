package org.dynamicruntime.user;

import org.dynamicruntime.exception.DnException;
import static org.dynamicruntime.util.ConvertUtil.*;

import java.util.Date;

/** Represents the extracted contents of an authentication cookie. This may also be the extraction of
 * a temporary authentication token as well which was passed in as a parameter.
 * This is a convenience class for encoding and decoding the authentication cookie. This
 * definition is in the core class because when the cookie definition changes, it tends
 * to have an impact system wide. */
@SuppressWarnings("WeakerAccess")
public class UserAuthCookie {
    // Every cookie starts with a single letter code. As the cookie encodes more data over
    // time, different letter codes represent different cookies used for different reasons.
    // Using a different code does not obsolete the usage of other codes.
    public static String STD_AUTH_COOKIE_CODE = "S";

    /** The single letter code identifying the type of cookie. */
    public final String code;
    /** The extracted version of the cookie. This follows the code terminated by a '#'. Versions with lesser
     * numbers are obsoleted and will generally tend not to get renewed. */
    public final int version;
    public final long grantingUserId;
    public final long userId;
    public final int renewalCount;
    public final Date creationDate;
    public final Date modifiedDate;
    public final Date expireDate;

    public UserAuthCookie(String code, int version, long grantingUserId, long userId, int renewalCount,
            Date creationDate, Date modifiedDate, Date expireDate) {
        this.code = code;
        this.version = version;
        this.grantingUserId = grantingUserId;
        this.userId = userId;
        this.renewalCount = renewalCount;
        this.creationDate = creationDate;
        this.modifiedDate = (modifiedDate != null) ? modifiedDate : creationDate;
        this.expireDate = (expireDate != null) ? expireDate : modifiedDate;
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
        return code + version + "," + grantingUserId + "," + userId + "," + renewalCount + "," +
                fmtObject(creationDate) + "," + md + "," + ed;
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
        if (entries.length < 7) {
            throw new DnException("Cookie is not properly formatted.");
        }
        String code = cv.substring(0, 1);
        int version = (int)toReqLong(cv.substring(1));
        long grantingUserId = toReqLong(entries[1]);
        long userId = toReqLong(entries[2]);
        int renewalCount = (int)toReqLong(entries[3]);
        Date creationDate = toReqDate(entries[4]);
        Date modifiedDate = getDateWithDiff(creationDate, entries[5]);
        Date expireDate = getDateWithDiff(modifiedDate, entries[6]);

        // Additional entries can be added as cookie grows in data that is put into it.
        //....

        return new UserAuthCookie(code, version, grantingUserId, userId, renewalCount, creationDate,
                modifiedDate, expireDate);
    }
}
