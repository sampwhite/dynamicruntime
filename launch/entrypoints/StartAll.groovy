import org.dynamicruntime.CoreComponent
import org.dynamicruntime.context.DnCxt
import org.dynamicruntime.servlet.DnServer
import org.dynamicruntime.startup.InstanceRegistry
import org.dynamicruntime.startup.LogStartup

/** If at some point this repository gets used along side others, then other entry points can be created
 * in those projects and used as an alternative to this one. */
class StartAll {
    static def main(args) {
        DnCxt cxt = null
        try {
            InstanceRegistry.addComponentDefinitions([new CoreComponent()])
            def config = InstanceRegistry.getOrCreateInstanceConfig("local", [:])
            cxt = InstanceRegistry.createCxt("startServer", config)
            DnServer.launch(cxt)
        } catch (Throwable t) {
            LogStartup.log.error(cxt, t, "Failed to execute full startup")
        }
    }
}
