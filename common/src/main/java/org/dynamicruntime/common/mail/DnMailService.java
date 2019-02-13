package org.dynamicruntime.common.mail;

import org.dynamicruntime.context.DnConfigUtil;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.httpclient.DnHttpClient;
import org.dynamicruntime.httpclient.DnHttpRequest;
import org.dynamicruntime.startup.ServiceInitializer;
import org.dynamicruntime.util.CacheMap;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;
import static org.dynamicruntime.common.mail.DnMailConstants.*;

@SuppressWarnings("WeakerAccess")
public class DnMailService implements ServiceInitializer {
    public static final String MAIL_SERVICE = DnMailService.class.getSimpleName();
    // The configuration for private key should be in the private file.
    public static final String API_KEY = "mail.mg.apiKey";
    public static final String MAILGUN_URI_KEY = "mail.mg.uri";
    public static final String MAIL_APP_FROM_ADDRESS_KEY = "mail.appFromAddress";

    // For simulated email sending.
    public final AtomicInteger mailId = new AtomicInteger(1);

    public String fromAddressForApp;

    // Target of email.
    public String mailgunUri;
    private String apiKey;
    public DnHttpClient httpClient;

    // Caching of recent emails for testing and trouble shooting.
    protected final CacheMap<String,DnMailResponse> sentEmails = mBoundedMap(200);

    public static DnMailService get(DnCxt cxt) {
        var obj = cxt.instanceConfig.get(MAIL_SERVICE);
        return (obj instanceof DnMailService) ? (DnMailService)obj : null;
    }

    public void onCreate(DnCxt cxt) {
        if (httpClient == null) {
            mailgunUri = DnConfigUtil.getConfigString(cxt, MAILGUN_URI_KEY,
                    "https://api.mailgun.net/v3/mg.dynamicruntime.org/messages",
                    "Mailgun URI path");
            fromAddressForApp = DnConfigUtil.getConfigString(cxt, MAIL_APP_FROM_ADDRESS_KEY,
                    "support@mg.dynamicruntime.org", "From address for application " +
                            "generated emails.");
            apiKey = toOptStr(cxt.instanceConfig.get(API_KEY));
            httpClient = new DnHttpClient(cxt, "Mailgun", false);
        }
    }

    @Override
    public String getServiceName() {
        return MAIL_SERVICE;
    }

    @Override
    public void checkInit(DnCxt cxt) {

    }

    public Map<String,Object> createMailData(String to, String from, String subject, String text,
            String html) {
        return mMap(MC_TO, to, MC_FROM, from, MC_SUBJECT, subject, MC_TEXT, text, MC_HTML, html);
    }

    @SuppressWarnings("unused")
    public DnMailService addBcc(Map<String,Object> data, String bcc) {
        data.put(MC_BCC, bcc);
        return this;
    }

    public DnMailResponse sendEmail(DnCxt cxt, Map<String,Object> mailData) throws DnException {
        DnMailResponse resp;
        if (apiKey != null) {
            var request = new DnHttpRequest(cxt, EPH_POST, mailgunUri);
            request.auth("api", apiKey).useFormEncoded(true).values(mailData);
            httpClient.execute(request);
            int code = request.respCode;
            if (code != 200) {
                int exceptionCode = (code == 400 || code == 404 || code == 500) ? code :
                        DnException.INTERNAL_ERROR;
                String msg = (code == 401) ? "Mail request could not authenticate" :
                        String.format("Could not send email %s.", rpt(mailData));
                throw new DnException(msg, null, exceptionCode, DnException.NETWORK, DnException.IO);
            }
            Map<String,Object> respData = request.responseData;
            if (respData == null) {
                throw new DnException(String.format("No response from mailgun for email %s", rpt(mailData)));
            }
            resp = DnMailResponse.extract(respData);
            if (resp.id == null) {
                String msg = resp.message != null ?
                        String.format("Mail gun reports the following on email %s. %s", rpt(mailData), resp.message) :
                        String.format("No mailgun id generated for email %s.", rpt(mailData));
                throw new DnException(msg);
            }
            LogMail.log.debug(cxt, String.format("Sent email %s and was identified as %s.",
                    rpt(mailData), resp.id));
            resp.sentToMailServer = true;
        } else {
            resp = new DnMailResponse("" + mailId.getAndIncrement(), "Simulated email", mMap());
            LogMail.log.debug(cxt, "Sent simulated email " + rpt(mailData) + ".");
        }
        resp.mailData = mailData;
        synchronized (sentEmails) {
            // Caching by id, because we may eventually do follow up calls to check on status of emails so
            // we can report on emails that mailgun was not able to deliver immediately.
            sentEmails.put(resp.id, resp);
        }
        return resp;
    }

    public List<DnMailResponse> getRecentSentEmails() {
        synchronized (sentEmails) {
            return cloneList(sentEmails.values());
        }
    }

    public static String rpt(Map<String,Object> mailData) {
        return "" + mailData.get("from") + "->" + mailData.get("to");
    }
}
