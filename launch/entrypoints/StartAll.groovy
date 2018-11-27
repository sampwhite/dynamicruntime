import org.dynamicruntime.CoreComponent
import org.dynamicruntime.servlet.DnServer

class StartAll {
    static def main(args) {
        def cc = new CoreComponent()
        cc.init()

        DnServer.launch()
    }
}
