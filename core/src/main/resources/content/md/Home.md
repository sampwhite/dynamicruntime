# Home Page

## Overview

Welcome to Dynamic Runtime, an open source project to create a flexible Java application that can scale in both
complexity and load. Go to the [Github Dynamic Runtime Project](https://github.com/sampwhite/dynamicruntime) for
more. For information about the **DnType** schema, see [Dynamic Types](/content/md/DynamicType.md). For some
thoughts on the code in this project see [Thoughts on Coding](/content/md/ThoughtsOnCode.md).

Some of the results of creating data sourced schema can be seen at 
[/content/html/endpoints.html](/content/html/endpoints.html).

## Intent

This code tries to anticipate and implement solutions to many of the common problems that arise during the creation
of a standard web application. The intent of this project is **not** for usage as a library or
webservice. Instead the code base is meant to be a repository of *ready to copy* example code with an 
assumption that the code will be mutated appropriately to fit the needs of a particular application. The running website
that the code generates exhibits how the code is meant to be used. We do hope that when programmers examine this
code base and look at the approaches to solving some common web application creation problems, they will be inspired to
ask whether they have properly addressed the same problems in their own code.

## Start Up Time

The server currently takes four seconds to start with two of those seconds spent starting the Java VM and 
log4j infrastructure. Re-compiles after changing a few files take less than two seconds and IntelliJ is reasonably
good at hot-loading changed code (after you ask it to recompile a Java file or a resource file). The debugger
is smooth, reliable, and has clean (and informative) thread call stacks.

Our goal for any web application is to have the startup time stay below fifteen seconds. A goal we have achieved
in the past with some fairly complicated code (more than 100,000 lines of code, reading in
megabytes of configuration data). I believe that slow Java server start up times are a self-inflicted unnecessary evil.
Slow start up times are fine for quick simple projects using quick on-ramp frameworks, but for projects that will
have many man years put into them, the savings on start up time can justify a real serious think about the best
approach to implementing your code.

## Current Progress

We now create schemas for endpoints and tables, and tables are automatically created and updated based on
the schema. We use the schema for endpoints to generate HTML forms for convenient execution of the endpoints.
The schema for tables includes the primary key and indexes. Schemas can extend from other schemas 
(*equivalent of subclassing*) and can import fields from other schemas (*equivalent of traits*) into their definitions.

The configuration allows overlays for environment *type* and environment *mode* allowing different configurations
to come into play depending on deployment choices. This is useful for creating staging and simulation environments.

We created code to access an RDS database including a query translator that takes bouncy cap column names to column
names that are lower case with underscores. Also there is query generation code that can look at the table schema
to aid in the creation and design of queries. If the database connection is not configured then the application starts
up using the H2 in-memory database. This means you can clone the git repository and run `./gradlew execute`
(after installing the Java 10 or 11 VM) and have the server running with no additional labors.

The application can send email using a free [mailgun](https://www.mailgun.com) service. We use the email to
do new user registration and support *forgot password* logic.

The application has a login and profile page rendered with React using a live babel compile.

We split the application into independently loadable components and configuration controls which components are to be
loaded on startup. Also, components can extend or add schema. Testing uses this to control exactly what functionality
is loaded for a particular test.

This application is deployed to [dynamicruntime.org](https://dynamicruntime.org). The entry to the website 
uses an AWS load balancer, which talks to multiple nodes running the base application.  At startup the nodes connect
to an AWS Postgres database. The multiple nodes are automatically updated (using AWS CodeDeploy)
when code is contributed to the git repository.

# Near Future

The current focus is on publishing React websites to the application. The idea is for all the javascript, css,
and image assets to be served by AWS Cloudfront. The React websites can use any of the endpoints made available
by the application.