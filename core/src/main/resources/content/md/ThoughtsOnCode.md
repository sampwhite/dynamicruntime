# Thoughts On Code

## Overview

This is a document for those who have read through some of the source code and have wondered about some of the
choices that were made in the implementation. Before we go into details, I should start by saying that some of what 
was done was done as an experiment and is not necessarily the best approach for a standard Java application.

## Reflection

One of the self-imposed choices was to avoid reflection based methods for solving coding problems. This avoidance of
reflection extends to an avoidance of annotations. Because of this there is a bit more *boiler-plate* code
than might found in a typical project. This is a foundational approach for the dynamic runtime project and
this choice was made for a number of reasons. Here are some of them.

* The types of problems that reflection is used to solve are usually the easiest part of the work for a project
and not the part that adds significantly to the overall time of project development for large projects. For
short-term projects, reflection tricks can be big time savers but the Dynamic Runtime application is focused
on large hundreds of man years type projects where such savings are irrelevant.

* Reflection imposes rigidity on an application. This is particularly true in loading Java objects from databases
or other data sources. The Java object is locked to the current design of its data source allowing neither to
float independently from each other. In particular, it has difficulty simultaneously supporting multiple
different versions of a schema as it changes over the years. But the issue of rigidity also applies when wiring together
objects where more complex choices might need to be made. This can become a big problem when your deployment 
options for testing and simulations become more exotic.

* Reflection solutions are hard to audit. If you wish to add logging or metrics to the code, reflection can
many times get in the way. For example, you might want to know when a field gets a particular value and report
it as an event to an event monitor.

* Reflection solutions can be hard to break up into independently moving parts. It can be harder to create 
implementations with code modules focusing on different areas of concern.

* In my personal experience, reflection solutions have been buggy (and slow) and particularly prone to multi-thread
race conditions. And when reflection solutions are buggy they compound the problem by being hard to debug.

* During active development when you hot reload parts of your application, reflection based solutions for 
wiring components together usually deal poorly with parts of your application re-initializing. Again
this is a bigger issue for large projects where you are only working on a small piece of a puzzle and
not the entire puzzle.

* Reflection can obscure Java stack dumps. For example, if you have hand written code to read in data to an object
and an exception is thrown, the class name for that object will appear in the stack dump. This is usually
not true for reflection based loading of a Java object. Similar issues can occur when objects are wired
together using reflection.

## IntelliJ

Many of the coding choices made in this project are with an awareness of the capabilities and limitations of 
IntelliJ. In particular, the code tries to take advantage of the fact that IntelliJ can answer questions like 
"who uses this variable or calls this method?" or "who implements this interface?" quickly and without ambiguity. 
It is one of the reasons there are so many string constants in the code. Using "where used" on them can be a great
way to navigate around the code.

We give a particular example from the code. The functional interface *DnEndpointFunctionalInterface* is used
to register a callback function to be called for an endpoint.  We could have created a generic *DnConsumer* 
functional interface instead that would have been shared with other code that had similar needs. But we did not
because we can now use IntelliJ to find all the endpoint functions in the system just by asking IntelliJ
what code implements *DnEndpointFunctionalInterface*. If at some point the number of endpoints becomes
quite large, then we will create sub-interfaces of *DnEndpointFunctionInterface* just to add a different
interface signature into the interface name so that IntelliJ can classify the endpoints that it finds by
which sub-interface it implements.

## Exceptions

For this project, we have created a universal exception class called *DnException*. It is based on a general
fact that we have seen over the years that the logic for error handling tends to have a lot of similarities
and if you start creating different exception classes, you lose your ability to leverage that commonality. And for
Java with enforced exception signatures on methods, using different exception classes forces the creation of
code layers that rethrow one type of exception as another type of exception and many times losing useful
information in the process. One of the advantages of having a universal exception class is that a lot
of the code can ignore the issue of exception handling all together. Again this speaks to the issue of breaking
apart areas of concern into different code packages. This issue is particularly relevant to *functional* 
programming where the executors of lambda functions can have difficulty dealing with exceptions
being thrown by the lambda function.

For a particular win of this approach, look at the method *DnException#canRetry* and
ask if any other application can answer whether a retry should be done as authoritatively.

## Execution Context

In other applications, user session data and active database sessions are all stored in thread locals,
making them essentially invisible elements when executing the code during debug. Thread local storage is 
also used to control dynamic binding between various elements of an application. In this application, we 
put all those attributes into a single class named DnCxt with an intent to make all this data explicit
and immediately available during a debug session. It also eliminates complications when delegating
code to worker threads.

## Enums

This is a particular pain point. If Java had implemented enums as a restriction on possible values of a type but
did not change the underlying type, then using enums in Java would be quite useful. But using an Enumeration
forces the creation of a completely different type signature and it is difficult to write simple code that can
access the underlying *real* type of the value. It is particularly painful to use Java enumerations when 
you want to read values from a database or a file. Because of these problems we do not use enumerations in
our code, but it comes with a fairly heavy price. To alleviate this we do try to do tricks such as using common
prefixes for the names of static string constants to give some help to the coder. To put it another way,
many of our string and numeric constants are "wanna-be" enumeration values.

## Import Static

For the very commonly used static methods we import them using a static import to reduce the amount of typing
to use them. However, this is also a documentation feature. If you see a short variable or method name it means
one of two things. The definition of the variable or method is defined in the same class (or same code block) and
is used no where else or it is so commonly used that it is assumed that the programmer has memorized a working
knowledge of the implementation. It is also assumed that the variable or code has a fairly straightforward
implementation. 

On the other hand if the variable name or method name is done with a long naming construct, then it implies 
that the code is not that commonly used or it may require some real investigation to understand the logic
surrounding the variable or method. Using this type of naming reference convention can help a programmer when 
reading the code to know what parts of the code require mental effort and what parts do not, greatly 
speeding the comprehension of code for a skilled reader of code.

On one other note, with IntelliJ (or similar IDEs) you can quickly find the definition of
a method call even if the source is obscured.




