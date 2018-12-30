package org.dynamicruntime.common.user;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.sql.SqlCxt;
import org.dynamicruntime.sql.topic.SqlTopicConstants;
import org.dynamicruntime.sql.topic.SqlTopicInfo;
import org.dynamicruntime.sql.topic.SqlTopicService;
import org.dynamicruntime.startup.ServiceInitializer;

@SuppressWarnings("WeakerAccess")
public class UserService implements ServiceInitializer {
    public static final String USER_SERVICE = UserService.class.getSimpleName();
    public SqlTopicService topicService;

    @Override
    public String getServiceName() {
        return USER_SERVICE;
    }

    @Override
    public void onCreate(DnCxt cxt) throws DnException {
        topicService = SqlTopicService.get(cxt);
        if (topicService == null) {
            throw new DnException("UserService requires SqlTopicService.");
        }
        topicService.registerTopicContainer(SqlTopicConstants.AUTH_TOPIC,
                new SqlTopicInfo(UserTableConstants.UT_TB_AUTH_USERS));
        topicService.registerTopicContainer(SqlTopicConstants.USER_PROFILE_TOPIC,
                new SqlTopicInfo(UserTableConstants.UT_TB_USER_PROFILES));

        // Force table creation of auth topic at startup time.
        var authTopic = topicService.getOrCreateTopic(cxt, SqlTopicConstants.AUTH_TOPIC);
        var sqlCxt = new SqlCxt(cxt, authTopic);
        // Force *primary* shard of all auth tables to be created and populated with *sysadmin* user.
        AuthQueryHolder.get(sqlCxt);

        // Force table creation of userprofile topic at startup.
        topicService.getOrCreateTopic(cxt, SqlTopicConstants.USER_PROFILE_TOPIC);
    }

    @Override
    public void checkInit(DnCxt cxt) {

    }
}
