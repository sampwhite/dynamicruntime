# Core Component

## Overview

For those who may have similarly named components in other applications, our definition of *core* is
different from most usages. In particular, we do not segregate code by theme, such as *auth*, *userData*,
etc. In the author's opinion, such segregation has many times been self-defeating not allowing for the
natural intertwining of themes and distracts from a more successful strategy to reduce complexity, which
is *data-driven* code. Now, having said this, at deployment time, only portions of the *core* functionality
may manifest allowing nodes to be deployed for particular purposes, such as authentication, but every node
will have a common understanding of a shared model with every other node.

## Contents

This component contains any code that can be considered at all universal. This includes authentication,
authorization, database access, general user transaction logic, and so on. One of the main points of a 
*data-driven* application is that the code becomes more generic and less specific to a particular case. 
So when developing new functionality, the goal is to have much of that functionality in this component as
makes sense. The *core* component is where code reuse is supposed to happen.

However, there are some things that do not belong in this component. One is configuration data for connecting
to specific resources. The other is any logic or data specific to a particular application manifestation.
This component should also not have the resources to create the web UI (besides self-documentation), though it 
has the engine for building that UI. If you wish to find these application specific resources, go to the
*common* component.