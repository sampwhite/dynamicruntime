package start

import config.ConfigLoader
import org.dynamicruntime.common.CommonComponent
import org.dynamicruntime.CoreComponent
import org.dynamicruntime.context.DnCxt
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
            // Eventually the *common* project may provide some utility functions for this part of the start up code,
            // once the concept of *deploy* option is implemented.
            InstanceRegistry.setDevMode()
            InstanceRegistry.addComponentDefinitions([new CoreComponent(), new CommonComponent()])
            String instanceName = fileConfig.instanceName ?: "local"
            InstanceRegistry.setDefaultInstance(instanceName)
            String envName = fileConfig.envName
            if (envName) {
                InstanceRegistry.setEnvName(envName)
            }
            if (fileConfig.logHeaders) {
                DnRequestHandler.logHttpHeaders = true
            }
            def config = InstanceRegistry.getOrCreateInstanceConfig(instanceName, fileConfig)
            cxt = InstanceRegistry.createCxt("startServer", config)
            DnServer.launch(cxt)
        } catch (Throwable t) {
            LogStartup.log.error(cxt, t, "Failed to execute full startup")
        }
    }
}
