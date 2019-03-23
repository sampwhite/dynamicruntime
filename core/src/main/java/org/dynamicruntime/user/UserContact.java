package org.dynamicruntime.user;

import org.dynamicruntime.exception.DnException;

import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;
import static org.dynamicruntime.user.UserConstants.*;

import java.util.Map;

/** A simple class for holding contact info. This is extracted from Map data of a database column that holds JSON
 * string rendered data. */
@SuppressWarnings("WeakerAccess")
public class UserContact {
    public String address;
    public String displayAddr;
    // Groovy does not like fields named *type*.
    public String cType;
    public String usage;
    // Set when we have both profile and auth data and we can determine which contacts are verified.
    @SuppressWarnings("unused")
    public boolean verified;
    public Map<String,Object> data;

    public UserContact(String address, String displayAddr, String cType, String usage, Map<String,Object> data) {
        this.address = address;
        this.displayAddr = displayAddr;
        this.cType = cType;
        this.usage = usage;
        this.data = data;
    }

    public static UserContact extract(Map<String,Object> originalData) throws DnException {
        // Since we are extracting from raw user input, we need to clone and filter.
        Map<String,Object> data = cloneMap(originalData);
        data.remove(FM_FORM_AUTH_TOKEN);

        String contactType = getReqStr(data, CT_CONTACT_TYPE);
        String baseAddress = getReqStr(data, CT_CONTACT_ADDRESS);
        String contactAddress = baseAddress;
        String displayAddress = getOptStr(data, CT_CONTACT_DISPLAY_ADDR);
        if (displayAddress == null) {
            contactAddress = normalizeAddress(contactType, baseAddress);
            displayAddress = baseAddress;
        } else {
            // Call the normalization just for the validation.
            normalizeAddress(contactType, baseAddress);
        }
        String contactUsage = getOptStr(data, CT_CONTACT_USAGE);
        return new UserContact(contactAddress, displayAddress, contactType, contactUsage, data);
    }

    public static String normalizeAddress(String contactType, String address) throws DnException {
        // Trim off part of email that specifies name.
        if (contactType.equals(CTT_EMAIL)) {
            int index = address.indexOf('>');
            if (index >= 0) {
                if (!address.startsWith("<")) {
                    throw DnException.mkInput(String.format(
                            "Email address %s has a > symbol and does not start with a <.",
                            address));
                }
                address = address.substring(index + 1).trim();
            }
        }

        // Keep only meaningful (or legal) characters in the contact type.
        StringBuilder sb = new StringBuilder(address.length());
        for (int i = 0; i < address.length(); i++) {
            char ch = address.charAt(i);
            ch = Character.toLowerCase(ch);
            boolean isLegal;
            if (contactType.equals(CTT_EMAIL)) {
                isLegal = Character.isJavaIdentifierPart(ch) || ch == '.' || ch == '@';
                // Email addresses do not allow fluff formatting characters.
                if (!isLegal) {
                    throw DnException.mkInput(String.format("Email address %s has invalid characters in it.",
                            address));
                }
            } else {
                isLegal = (ch >= '0' && ch <= '9');
            }
            if (isLegal) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    public Map<String,Object> toMap() {
        Map<String,Object> m = (data != null) ? cloneMap(data) : mMap();
        m.putAll(mMap(CT_CONTACT_TYPE, cType, CT_CONTACT_ADDRESS, address, CT_CONTACT_DISPLAY_ADDR, displayAddr,
                CT_CONTACT_USAGE, usage));
        if (verified) {
            m.put(VERIFIED, true);
        }
        return m;
    }
}
