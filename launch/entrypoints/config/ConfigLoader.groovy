package config

import org.dynamicruntime.startup.LogStartup
import org.dynamicruntime.config.ConfigLoadUtil

class ConfigLoader {
    static Map<String,Object> loadGroovyConfig() {
        File configFile = ConfigLoadUtil.findConfigFile("dnConfig.groovy")
        if (configFile == null) {
            LogStartup.log.info(null, "Cannot find *dnConfig.groovy* file, using defaults.")
            return [:]
        } else {
            LogStartup.log.debug(null, "Found *dnConfig.groovy* file at ${configFile.getAbsolutePath()}.")
        }
        def configSlurper = new ConfigSlurper()
        return configSlurper.parse(configFile.text)
     }
}
