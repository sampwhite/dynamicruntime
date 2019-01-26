package org.dynamicruntime.util;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;

import static org.dynamicruntime.util.DnCollectionUtil.*;

import java.util.List;
import java.util.Map;

public class HttpUtil {
    public static String encodeHttpArgs(Map<String,Object> args) {
        List<NameValuePair> pairs = mList();
        for (String key : args.keySet()) {
            Object v = args.get(key);
            String e = ParsingUtil.toJsonString(v, true);
            pairs.add(new BasicNameValuePair(key, e));
        }
        return URLEncodedUtils.format(pairs, "utf-8");
    }
}
