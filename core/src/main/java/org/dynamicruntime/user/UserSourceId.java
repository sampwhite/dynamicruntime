package org.dynamicruntime.user;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.util.DnDateUtil;
import org.dynamicruntime.util.EncodeUtil;
import org.dynamicruntime.util.IpLocationUtil;
import org.dynamicruntime.util.StrUtil;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;
import static org.dynamicruntime.user.UserConstants.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

/** A class that represents a source. It is used in two different places. The first is in
 * the login sources table. The second is as a report in in the  {@link UserConstants#UP_USER_DATA} map
 * of the profile record. In the second, user-agent information is captured but the differentiation
 * by sourceId is removed. This means that are two ways to produce persistent data ({@link #toProfileMap}
 * and {@link #toLoginMap}) and multi-way extraction code for the ip-address data in the method {@link #extract}.
 * Usually the object gets provisionally created based on an extraction from a cookie, then is filled out
 * as needed. */
@SuppressWarnings("WeakerAccess")
public class UserSourceId {
    public static final String[] MACHINE_TERMINATORS = {"OS", "NT", "nux", "ome"};
    public static class UserAgent {
        public final String userAgent;
        public final Date dateCaptured;
        public Date modifiedDate;

        public UserAgent(String userAgent, Date dateCaptured, Date modifiedDate) {
            this.userAgent = userAgent;
            this.dateCaptured = dateCaptured;
            this.modifiedDate = modifiedDate;
        }

        public static UserAgent extract(String str) throws DnException {
            int index = str.indexOf("@");
            if (index < 0) {
                throw new DnException("String " + str + " does not encode user agent information.");
            }
            String dateStrs = str.substring(0, index);
            var dateVals = StrUtil.splitString(dateStrs, "#");
            if (dateVals.size() != 2) {
                throw new DnException("String " + str + " does not have appropriate date values.");
            }
            String agentStr = str.substring(index + 1);
            Date captured = toOptDate(dateVals.get(0));
            Date modified = toOptDate(dateVals.get(1));
            if (captured == null || modified == null) {
                throw new DnException("String " + str + " does not have a date embedded in it.");
            }
            return new UserAgent(agentStr, captured, modified);
        }

        public String toString() {
            return DnDateUtil.formatDate(dateCaptured) + "#" + DnDateUtil.formatDate(modifiedDate)
                    + "@" + userAgent;
        }
    }

    public static class IpAddress {
        public final String ipAddress;
        /** Filled in with GEO location call. */
        public List<String> ipLocation;
        public Date earliestCookieDate;
        public final Map<String,UserAgent> userAgents;

        public IpAddress(String ipAddress, Date cookieDate, Map<String,UserAgent> userAgents) {
            this.ipAddress = ipAddress;
            this.earliestCookieDate = cookieDate;
            this.userAgents = userAgents;
        }

        public static IpAddress extract(Map<String,Object> data) throws DnException {
            String ip = getReqStr(data, LS_IP_ADDRESS);
            Date cd = getReqDate(data, LS_CAPTURE_DATE);
            List<String> userAgentStrs = getOptListOfStrings(data, LS_USER_AGENTS);
            Map<String,UserAgent> userAgents = mMapT();
            if (userAgentStrs != null) {
                for (String uaStr : userAgentStrs) {
                    UserAgent ua = UserAgent.extract(uaStr);
                    String key = computeUserAgentKey(ua.userAgent);
                    userAgents.put(key, ua);
                }
            }
            var retVal = new IpAddress(ip, cd, userAgents);
            retVal.ipLocation = IpLocationUtil.getLocation(ip);
            return retVal;
        }

        public void addUserAgent(DnCxt cxt, String userAgent) {
            String userAgentKey = computeUserAgentKey(userAgent);
            UserAgent ua = userAgents.get(userAgentKey);
            Date now = cxt.now();
            if (ua == null) {
                prepareForAgentAdd();
                UserAgent newUa = new UserAgent(userAgent, now, now);
                userAgents.put(userAgentKey, newUa);
            } else {
                ua.modifiedDate = now;
            }
        }

        public void prepareForAgentAdd() {
            int count = 0;
            while (userAgents.size() >= 5) {
                // We do this brute force since the size of map is so small.
                // Find earliest date.
                Date ed = null;
                String key = null;
                for (String k : userAgents.keySet()) {
                    UserAgent ua = userAgents.get(k);
                    if (ed == null || ua.dateCaptured.before(ed)) {
                        ed = ua.dateCaptured;
                        key = k;
                    }
                }
                if (count++ > 10) {
                    throw new RuntimeException("Aborting out of unexpected infinite loop.");
                }
                userAgents.remove(key);
            }
        }

        public Map<String,Object> toMap() {
            // See if we can add geo location info as well.
            try {
                if (ipLocation == null) {
                    ipLocation = IpLocationUtil.getLocation(ipAddress);
                }
            } catch (DnException ignore) { }
            List<String> encodedUserAgents = nMapSimple(userAgents.values(), UserAgent::toString);
            return mMap(LS_IP_ADDRESS, ipAddress, LS_GEO_LOCATION, ipLocation, LS_CAPTURE_DATE, earliestCookieDate,
                    LS_USER_AGENTS, encodedUserAgents);
        }
    }
    // Indicates whether this object was initially created from a cookie.
    public final boolean fromCookie;
    // First two parts are from cookie.
    public final String sourceCode;

    // This is not part of the serialized data into the database. It is a placeholder for cookie
    // management. The cookie creation date is purely for reporting. If two different IP addresses
    // share a cookieCreateDate value on their user agents, then it is assumed that they come from
    // the same machine or device and the reports for the two IP addresses can be collapsed.
    public final Date cookieCreateDate;

    // Date to use when creating cookie. The idea is to carry over dates from prior generation
    // of the cookie or a date associated with the current IP address, whichever is earlier.
    public Date newCookieCreateDate;

    // Whether to force a regeneration of cookie.
    public boolean forceRegenerateCookie;

    // Whether this sourceId does not yet exist in the database.
    public boolean isNew;

    // Whether we made a change significant enough for us to save back to login source records.
    // Some of the changes can get captured in profile or special tracking storage, but
    // they will focus on more lightweight concerns not directly relevant to authentication.
    public boolean isModified;

    // These later attributes are from querying row.
    public long userId;

    // We keep at most 10 of these.
    public List<IpAddress> ipAddresses;

    public Map<String,Object> sourceData;
    // Row data.
    public Map<String,Object> data;

    public UserSourceId(boolean fromCookie, String sourceCode, Date cookieCreateDate) {
        this.fromCookie = fromCookie;
        this.sourceCode = sourceCode;
        this.cookieCreateDate = cookieCreateDate;
        this.newCookieCreateDate = cookieCreateDate;
        if (!fromCookie) {
            forceRegenerateCookie = true;
        }
    }

    public static UserSourceId createNew() {
        String sourceCode = EncodeUtil.mkRndString(16);
        return new UserSourceId(false, sourceCode, null);
    }

    public static UserSourceId createFromCookie(String cookie) {
        try {
            var vals = StrUtil.splitString(cookie, "@", 2);
            Date d = DnDateUtil.parseDate(vals.get(0));
            return new UserSourceId(true, vals.get(1), d);
        } catch (Throwable ignore) {
            // We do not want this code to cause failures.
            return null;
        }
    }

    public static UserSourceId extract(Map<String,Object> data) throws DnException {
        String sourceCode = getReqStr(data, LS_SOURCE_GUID);
        UserSourceId source = new UserSourceId(false, sourceCode, null);
        source.fillOutData(data);
        return source;
    }

    public void fillOutData(Map<String,Object> data) throws DnException {
        userId = getReqLong(data, USER_ID);
        Map<String,Object> lsSourceData = getMapDefaultEmpty(data, LS_SOURCE_DATA);
        fillFromSourceData(lsSourceData);
        this.data = data;
    }

    @SuppressWarnings("unchecked")
    public void fillFromSourceData(Map<String,Object> lsSourceData) throws DnException {
        Object obj = lsSourceData.get(LS_CAPTURED_IPS);
        ipAddresses = mList();
        if (obj instanceof List) {
            List<?> l = (List<?>)obj;
            for (Object elt : l) {
                if (elt instanceof Map) {
                    Map<String,Object> m = (Map<String,Object>)elt;
                    ipAddresses.add(IpAddress.extract(m));
                } else if (elt instanceof String) {
                    // Date is irrelevant, just needs to not be null.
                    Date d = newCookieCreateDate != null ? newCookieCreateDate : new Date();
                    ipAddresses.add(new IpAddress(elt.toString(), d, mMapT()));
                }
            }
        }

    }

    public void addSource(DnCxt cxt, String ipAddress, String userAgent) {
        if (ipAddresses == null) {
            ipAddresses = mList();
        }
        // Look for a match.
        int index = -1;
        for (int i = 0; i < ipAddresses.size(); i++) {
            var ip = ipAddresses.get(i);
            if (ip.ipAddress.equals(ipAddress)) {
                index = i;
                break;
            }
        }
        IpAddress curIpAddr = (index >= 0) ? ipAddresses.remove(index) : null;
        if (curIpAddr == null) {
            if (newCookieCreateDate == null) {
                newCookieCreateDate = cxt.now();
            }
            while (ipAddresses.size() >= 10) {
                ipAddresses.remove(0);
            }
            curIpAddr = new IpAddress(ipAddress, newCookieCreateDate, mMapT());
            isModified = true;
        } else {
            if (newCookieCreateDate != null && newCookieCreateDate.before(curIpAddr.earliestCookieDate)) {
                curIpAddr.earliestCookieDate = newCookieCreateDate;
                isModified = true;
            } else {
                newCookieCreateDate = curIpAddr.earliestCookieDate;
            }
        }
        // Note that we have either moved an existing ip address to the end of the list
        // or we have shrunk the list so that it can have a new last member.
        ipAddresses.add(curIpAddr);
        curIpAddr.addUserAgent(cxt, userAgent);
    }

    /** Used for profile store. */
    public Map<String,Object> toProfileMap() {
        // Get all the user agent info, but we only care about the *sourceData*.
        var encodedIps = (ipAddresses != null) ? nMapSimple(ipAddresses, IpAddress::toMap) : mList();
        Map<String,Object> retSourceData = (sourceData != null) ? cloneMap(sourceData) : mMap();
        retSourceData.put(LS_CAPTURED_IPS, encodedIps);
        return retSourceData;
    }

    /** Used for login source table. */
    public Map<String,Object> toLoginMap() {
        Map<String,Object> retData = (data != null) ? cloneMap(data) : mMap();
        Map<String,Object> retSourceData = (sourceData != null) ? cloneMap(sourceData) : mMap();
        // Get the list of IP addresses only.
        var encodedIps = (ipAddresses != null) ? nMapSimple(ipAddresses, ip -> ip.ipAddress) : mList();
        retSourceData.put(LS_CAPTURED_IPS, encodedIps);
        // Currently we do not care about verification, so everything is verified since we do not let in
        // a login unless its verified. In the future, we may have partially verified user logins which
        // get additional verification when they do things with large implications (such as changing
        // the password).
        retData.putAll(mMap(LS_SOURCE_GUID, sourceCode, LS_SOURCE_DATA, retSourceData, VERIFIED, true));
        return retData;
    }

    public String computeCookieString(DnCxt cxt) {
        if (newCookieCreateDate == null) {
            newCookieCreateDate = cxt.now();
        }
        return DnDateUtil.formatDate(newCookieCreateDate) + "@" + sourceCode;
    }

    public static String computeUserAgentKey(String userAgent) {
        String browserType = "unknown";
        if (userAgent.contains("Edge")) {
            browserType = "Edge";
        } else if (userAgent.contains("Chrome")) {
            browserType = "Chrome";
        } else if (userAgent.contains("Firefox")) {
            browserType = "Firefox";
        } else if (userAgent.contains("Safari")) {
            browserType = "Safari";
        }
        String machineType = "unknown";
        int index = userAgent.indexOf("(");
        if (index > 0) {
            int index2 = userAgent.indexOf(")", index);
            if (index2 > 0) {
                String machineDes = userAgent.substring(index + 1, index2);
                // Look for terminators in the string.
                for (String term : MACHINE_TERMINATORS) {
                    int index3 = machineDes.indexOf(term);
                    if (index3 > 0) {
                        machineDes = machineDes.substring(0, index3 + term.length());
                        break;
                    }
                }
                machineType = machineDes;
            }
        }
        return browserType + ":" + machineType;
    }
}
