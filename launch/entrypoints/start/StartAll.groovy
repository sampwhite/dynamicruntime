package start

import config.ConfigLoader
import org.dynamicruntime.common.startup.StartupCommon
import org.dynamicruntime.context.DnCxt
import org.dynamicruntime.context.DnCxtConstants
import org.dynamicruntime.servlet.DnRequestHandler
import org.dynamicruntime.servlet.DnServer
import org.dynamicruntime.startup.InstanceRegistry
import org.dynamicruntime.startup.LogStartup

/** If at some point this repository gets used along side others, then other entry points can be created
 * in those projects and used as an alternative to this one. For those who wish to suppress the Groovy
 * warning about illegal access, add the following VM options to the launch of Java.
 *
 * --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.annotation=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.lang.module=ALL-UNNAMED --add-opens=java.base/java.lang.ref=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.math=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.net.spi=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.nio.channels=ALL-UNNAMED --add-opens=java.base/java.nio.channels.spi=ALL-UNNAMED --add-opens=java.base/java.nio.charset=ALL-UNNAMED --add-opens=java.base/java.nio.charset.spi=ALL-UNNAMED --add-opens=java.base/java.nio.file=ALL-UNNAMED --add-opens=java.base/java.nio.file.attribute=ALL-UNNAMED --add-opens=java.base/java.nio.file.spi=ALL-UNNAMED --add-opens=java.base/java.security=ALL-UNNAMED --add-opens=java.base/java.security.acl=ALL-UNNAMED --add-opens=java.base/java.security.cert=ALL-UNNAMED --add-opens=java.base/java.security.interfaces=ALL-UNNAMED --add-opens=java.base/java.security.spec=ALL-UNNAMED --add-opens=java.base/java.text=ALL-UNNAMED --add-opens=java.base/java.text.spi=ALL-UNNAMED --add-opens=java.base/java.time=ALL-UNNAMED --add-opens=java.base/java.time.chrono=ALL-UNNAMED --add-opens=java.base/java.time.format=ALL-UNNAMED --add-opens=java.base/java.time.temporal=ALL-UNNAMED --add-opens=java.base/java.time.zone=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED
 */
class StartAll {
    static def main(args) {
        DnCxt cxt = null
        try {
            def fileConfig = ConfigLoader.loadGroovyConfig()
            // Deployment configuration loading is driven by three key boot choices,
            // environment type (general, stage, prod, etc.), environment name (dev, integration, unit, deployed),
            // and instance name (defaults to local which is for non-permanent instances).
            String envType = fileConfig.envType ?: DnCxtConstants.GENERAL_TYPE
            String envName = fileConfig.envName ?: DnCxtConstants.DEV
            String instanceName = fileConfig.instanceName ?: "local"
            InstanceRegistry.setEnvType(envType)
            InstanceRegistry.setEnvName(envName)
            InstanceRegistry.setDefaultInstance(instanceName)

            if (fileConfig.logHeaders) {
                DnRequestHandler.logHttpHeaders = true
            }

            // Use a convenience method to load the two components we have defined so far.
            cxt = StartupCommon.mkBootCxt("startServer", instanceName, fileConfig)
            DnServer.launch(cxt)
        } catch (Throwable t) {
            LogStartup.log.error(cxt, t, "Failed to execute full startup")
        }
    }
}
