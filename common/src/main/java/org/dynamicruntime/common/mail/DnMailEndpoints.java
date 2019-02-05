package org.dynamicruntime.common.mail;

import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.request.DnRequestCxt;
import org.dynamicruntime.schemadef.DnEndpointFunction;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.common.mail.DnMailConstants.*;
import static org.dynamicruntime.schemadef.DnEndpointFunction.mkEndpoint;

import java.util.List;
import java.util.Objects;

@SuppressWarnings("WeakerAccess")
public class DnMailEndpoints {
    /** Tests sending email. */
    public static void adminTestEmail(DnRequestCxt requestCxt) throws DnException {
        var cxt = requestCxt.cxt;
        var data = requestCxt.requestData;
        var mailService = DnMailService.get(cxt);
        DnMailResponse resp = Objects.requireNonNull(mailService).sendEmail(cxt, data);
        requestCxt.mapResponse.putAll(resp.toMap());
    }

    public static List<DnEndpointFunction> getFunctions() {
        return mList(mkEndpoint(ADMIN_EMAIL_TEST, DnMailEndpoints::adminTestEmail));
    }
}
