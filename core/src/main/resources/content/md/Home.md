# Home Page

## Overview

Welcome to Dynamic Runtime, an open source project to create a flexible Java application that can scale in both
complexity and load. Go to the [Github Dynamic Runtime Project](https://github.com/sampwhite/dynamicruntime) for
more. For information about the **DnType** schema, see [Dynamic Types](/content/md/DynamicType.md). For some
thoughts on the code in this project see [Thoughts on Coding](/content/md/ThoughtsOnCode.md).

Some of the results of creating data sourced schema can be seen at 
[/content/html/endpoints.html](/content/html/endpoints.html).

## Start Up Time

The server currently takes four seconds to start with two of those seconds spent starting the Java VM and 
log4j infrastructure. Re-compiles after changing a few files takes less than two seconds and IntelliJ is reasonably
good at hot-loading changed code (after you ask it to recompile a Java file or a resource file). The debugger
is smooth, reliable, and has clean (and informative) thread call stacks.

Our goal is to not let the startup time get beyond fifteen seconds, a goal we have achieved in the past with some
fairly complicated code (more than 100,000 lines of code, reading in megabytes of configuration data). I believe that
slow Java server start up times are a self-inflicted unnecessary evil. Slow start up times are fine for
quick simple projects using quick on-ramp frameworks, but for projects that will have many man years put 
into them, the savings on start up time can justify a real serious think about the best approach to implementing
your code.

## Current Progress

We now create schemas for endpoints and tables and tables will be automatically created and updated based on
the schema. We use the schema for endpoints to generate HTML forms for convenient execution of the endpoints.
The schema for tables includes the primary key and indexes.

Configuration allows overlays for environment *type* and environment *mode* allowing different configurations
to come into play depending on deployment choices. This is useful for creating staging and simulation environments.

We created code to access an RDS database including a query translator that takes bouncy cap column names to column
names that are lower case with underscores. Also there is query generation code that can look at the table schema
to aid the creation of the queries making the code use design information when defining the query.
If the database connection is not configured then the application will startup using the H2 in-memory database. 
This means you can clone the git repository and run `./gradlew execute` (after installing the Java 10 or 11 VM) to
get the server running with no additional labors.

We broke the application up into independently loadable components. The startup code can load one more
more components depending on configuration and allows control over which components are loading. Components
can extend or add schema. Testing uses this to control exactly what functionality is loaded for a particular test.

We have reached our first major milestone in our AWS integration. The entry to the website uses a load 
balancer which talks to multiple nodes running the base application.  At startup the nodes connect
to an AWS postgres flavored Aurora database. The multiple nodes are automatically 
updated (using AWS CodeDeploy) when code is contributed to the git repository. 
The internet entry point is at [dynamicruntime.org](https://dynamicruntime.org).


# Near Future

The current focus is on allowing users to self-register on the website and access and update their profile. We
may get distracted by trying to create a self-contained Docker image of our current work.