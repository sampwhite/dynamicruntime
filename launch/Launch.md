## Launch

This directory contains code to launch applications using the code in other directories. The current lauch implementation
uses Groovy to do the launching and reads local configuration from a *dnConfig.groovy* file which it hunts for
by going up the parent paths of the current working directory. The idea is that others may write their own launch 
and configuration solutions pulling in configuration and deployment options from other sources. Besides actual 
application launches, the launches could run scripts using the Dynamic Runtime directories purely as a library of 
useful code. 

Also, in order to connect to databases you will need to create a directory *private* somewhere in a directory
above the *dynamicruntime* directory. Inside that directory should be the file *dnConfig.yaml*. It should
have contents that look like the following.

```$xslt
# This assumes we have postgres database named *data1* with a user named *java_user* given privileges to 
# create tables and do queries. See *dnCommonConfig.yaml* for the configuration that refers to the entries
# in this file.
dbpasswords:
    primary: "<password-for-java_user>"
```