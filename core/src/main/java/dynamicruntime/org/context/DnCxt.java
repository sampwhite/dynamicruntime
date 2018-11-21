package dynamicruntime.org.context;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static dynamicruntime.org.util.DnCollectionUtil.*;
import static dynamicruntime.org.context.DnCxtConstants.*;

public class DnCxt {
    public String cxtName;
    public final InstanceConfig instanceConfig;
    public final List<String> parentLoggingIds;
    public final String loggingId;
    public final Map<String,Object> locals = mMap();
    public final Date startDate = new Date();

    public DnCxt(String cxtName, InstanceConfig instanceConfig, DnCxt parentCxt) {
        var ic = instanceConfig;
        if (ic == null) {
            ic = new InstanceConfig("codeTest", UNIT, TEST_TYPE);
        }
        this.cxtName = cxtName;
        this.instanceConfig = instanceConfig;
        var l = cloneList(parentCxt.parentLoggingIds);
        l.add(parentCxt.loggingId);
        parentLoggingIds = l;

        this.loggingId = cxtName + instanceConfig.getNextLoggingId();
    }
}
