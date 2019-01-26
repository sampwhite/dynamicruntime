package script

import config.ConfigLoader
import org.dynamicruntime.CoreComponent
import org.dynamicruntime.common.CommonComponent
import org.dynamicruntime.common.user.UserService
import org.dynamicruntime.startup.InstanceRegistry

class AddToken {
    static void main(String[] args) {
        if (args.length < 3) {
            println("\n*** AddToken ***")
            println("Arguments are: <username> <tokenName> <tokenPassword>")
            return
        }
        def username = args[0]
        def authId = args[1]
        def token = args[2]

        def fileConfig = ConfigLoader.loadGroovyConfig()
        println("FileConfig: ${fileConfig}")
        String instanceName = fileConfig.instanceName ?: "local"
        InstanceRegistry.setDevMode()
        InstanceRegistry.addComponentDefinitions([new CoreComponent(), new CommonComponent()])
        def config = InstanceRegistry.getOrCreateInstanceConfig(instanceName, fileConfig)
        def cxt = InstanceRegistry.createCxt("addToken", config)
        def userService = UserService.get(cxt)
        userService.addToken(cxt, username, authId, token, [:], null)
        println("***\nAdded token ${authId} that allows access to ${username}\n***")
    }
}
