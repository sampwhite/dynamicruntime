### Overview

This is a document describing the Dynamic Runtime project's approach to delivering react applications. Note,
the build process below is based on NPM and React from 2019. It is most likely obsolete now.

### Main Idea

The idea is to support deploying multiple web applications that share a common base of javascript source code. This
common base is located in the *webapps/dncore* sub-directory of the dynamicruntime project root. It is pulled into 
the main application *webapps/dnapp* as a source code dependency using tricks from
[https://medium.com/capriza-engineering/sharing-source-code-and-libraries-in-react-bd30926df312](https://medium.com/capriza-engineering/sharing-source-code-and-libraries-in-react-bd30926df312).

### Getting Started

If you have just checked out this repository go to *dncore* directory underneath the *webapps* directory and run
*npm install*. This will create the appropriate *node_modules* directory with all the dependencies. You may
need to run *npm install* again if the *package.json* file changes or there is a large refactoring in the
javascript code base. Then in the webapp *dnapp* execute the following.
```$xslt
npm install "../dncore"
npm install
```

### Creating New Webapps

New web applications can be created and deployed and run side by side in the dynamicruntime deployment layer.
Each web application is given a **siteId** which is of the form *&lt;appName&gt;/&lt;branch&gt;* with the branch
name *current* being used for the live released branch. Other branch names can be used for test or beta
deploys of a web application. The only webapp currently under development is the *dnapp* so the default *siteId*
is *dnapp/current*. 

The web application can be reached at the endpoint **/portal**. If you wish to view a particular webapp, then
you can provide a *siteId* parameter. For example, if you wished to see the *beta* version of the *dnapp* on
the *dynamicruntime.org* website, you could use the url *https://dynamicruntime.org/portal?siteId=dnapp/beta*.

#### Development Configuration

To have the webapp, when running in development mode, successfully call the *dynamicruntime* process running
in your development environment, add the following entry to the **package.json** file 
(after the *private* configuration entry is a good location).
```$xslt
  "proxy": "http://localhost:7070",
```

#### Including *dncore* Source

To include the *dncore* as co-compile source code for your webapp, perform the following steps.

First you create a node module dependency on *dncore* by executing the following inside the directory that houses
your webapp (do not forget the quotes around the *../dncore*)

```$xslt
npm install "../dncore"
```

The above assumes you are executing from another directory in the **webapps** directory. If your webapp is 
not located in this directory, then you can put in an appropriate relative path from your webapp to the dncore 
replacing the **../dncore** path. At this point, you should have in your *node_modules* directory
a symlink from *dncore* to the actual location of the *dncore* directory.

Now run inside your web application the following.
```$xslt
npm install react-app-rewired customize-cra --save-dev
```

This adds support for customizing webpack behavior. Then in your *package.json* for the new webapp,
replace **react-app** with **react-app-rewired** except for the **eject** script. This is what supports
doing configuration overrides. To do the overrides, in your webapp directory create the
file **config-overrides.js** file and put in the following content.

```$xslt
var path = require ('path');
var fs = require ('fs');
const {
    override,
    addDecoratorsLegacy,
    babelInclude,
    disableEsLint,
} = require("customize-cra");

module.exports = function (config, env) {
    return Object.assign(config, override(
        disableEsLint(),
        addDecoratorsLegacy(),

        babelInclude([
            path.resolve('src'), // Your application's source
            fs.realpathSync('node_modules/dncore') // The *dncore* source
        ])
        )(config, env)
    )
}
```

This config override file is what causes babel to co-compile both your application's Javascript and
the Javascript from *dncore*.

Now, in the *index.js* file of your webapp add the following import.
```$xslt
import Client from './api/Client';
```
Then before the *ReactDom.render* call, add the following call.
```$xslt
Client.init(window.location.pathname, window.location.search);
```
This will integrate your webapp with the dynamicruntime mechanisms for creating URLs and handling
JSON calls to the server.

At this point you should be able to start your webapp in the normal way (using *npm start*) and if you
change code in the *webapps/dncore* or your own application's directories it will be picked up by the live
webpack recompile. In the Javascript code, to import files and classes from the *dncore* source directory, use the 
import path *dncore/path-to-your-resource*. For example,
to import the function *dnl* for fixing up URL links, use the following construction.
```$xslt
import {dnl} from 'dncore/api/Functions'
```

### Web Application Deployment

There are three ways to view your web application. They are as follows.

* *Live Development* - In your webapp directory execute ```npm start```. And if you have correctly configured
the proxy entry in your *package.json* file (as described above), then you should see the web application
show up in a browser tab with the ability to access your local running dynamicruntime instance. If you 
edit web application code, the changes will be reflected in the browser view in a couple of seconds.

* *Local Preview* - If in your *dnConfig.groovy* file, you have put in the entry ```portal.useAws=false```,
then when you go to the *localhost:7070/portal* path of your local running instance of dynamicruntime you will
see content retrieved from the build output of ```npm run build```. In order to have the correct URLs be injected
into the React build, run the shell script *dnBuildLocal*. You will find an example copy of it in the
*dnapp* application directory. You can copy it into any new web application and mutate the script appropriately.
If you do development work outside the *webapps* directory of the dynamicruntime repository, you will need
to set the config variable *portal.resourceLocation* to the parent directory of your webapp directory. The default
for this entry is set to the path to the *webapps* directory in the dynamicruntime repository.

* *AWS Deploy* - In your *dnConfig.groovy* file you will need to set ```portal.useAws=true``` in order
to consume AWS S3 content for the portal page. This also assumes that you have the AWS client installed with
the credentials necessary to deploy the web application to the correct S3 location. In order to consume
the S3 content, the build directory needs to be built and then uploaded to S3. It is then consumed
through a cloudfront caching web path. You should examine the contents of the file *dnBuildAws* in the *webapps* 
directory for an example of how this is done. This script builds the web application with the appropriate URL 
paths and then writes a script called *awsUpload.sh* in the build directory. This script can then be run to upload
the files to AWS S3.
