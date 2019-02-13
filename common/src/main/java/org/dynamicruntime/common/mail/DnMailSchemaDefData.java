package org.dynamicruntime.common.mail;

import org.dynamicruntime.schemadef.DnRawEndpoint;
import org.dynamicruntime.schemadef.DnRawField;
import org.dynamicruntime.schemadef.DnRawSchemaPackage;
import org.dynamicruntime.schemadef.DnRawType;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;
import static org.dynamicruntime.common.mail.DnMailConstants.*;

import static org.dynamicruntime.schemadef.DnRawField.*;
import static org.dynamicruntime.schemadef.DnRawType.*;
import static org.dynamicruntime.schemadef.DnRawEndpoint.*;

@SuppressWarnings("WeakerAccess")
public class DnMailSchemaDefData {
    //
    // Admin test endpoint.
    //
    static public DnRawField to = mkReqField(MC_TO, "To",
            "Email address (or addresses) of recipients. Multiple entries separated by commas.");
    static public DnRawField from = mkReqField(MC_FROM, "From",
            "The email address of the person or agent sending the email.");
    static public DnRawField cc = mkField(MC_CC, "CC", "Email addresses to get copy of email.");
    static public DnRawField bcc = mkField(MC_BCC, "BCC",
            "Email addresses that bet a blind copy of the email.");
    static public DnRawField subject = mkReqField(MC_SUBJECT, "Subject",
            "The subject line of the email.");
    static public DnRawField text = mkField(MC_TEXT, "Text", "Simple email text.")
            .setAttribute(DN_IS_LARGE_STRING, true);
    static public DnRawField html = mkField(MC_HTML, "HTML", "Html email message")
            .setAttribute(DN_IS_LARGE_STRING, true);
    static public DnRawType adminMailRequest = mkType("AdminMailRequest",
            mList(to, from, cc, bcc, subject, text, html));

    static public DnRawField id = mkField("id", "ID", "The tracking identifier of the " +
            "mail as sent back by mail server.");
    static public DnRawField message = mkField("message", "Message",
            "The message sent back by the mail server.");
    static public DnRawType adminMailResponse = mkType("AdminMailResponse", mList(id, message));
    static public DnRawEndpoint adminMailTestEndpoint = mkEndpoint(EPH_POST,"/admin/email/test", ADMIN_EMAIL_TEST,
            "Allows testing of mailgun api.",
            adminMailRequest.name, adminMailResponse.name);

    static public DnRawSchemaPackage getPackage() {
        return DnRawSchemaPackage.mkPackage("DnMail", MAIL_NAMESPACE,
                mList(adminMailRequest, adminMailResponse, adminMailTestEndpoint));
    }


}
