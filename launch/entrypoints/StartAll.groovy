import org.dynamicruntime.CoreComponent
import org.dynamicruntime.servlet.DnServer
import org.dynamicruntime.startup.InstanceRegistry

/** If at some point this repository gets used along side others, then other entry points can be created
 * in those projects and used as an alternative to this one. */
class StartAll {
    static def main(args) {
        InstanceRegistry.addComponentDefinitions([new CoreComponent()])
        def config = InstanceRegistry.getOrCreateInstanceConfig("local", [:])
        def cxt = InstanceRegistry.createCxt("startServer", config)

        DnServer.launch(cxt)
    }
}
