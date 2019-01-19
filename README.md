# Dynamic Runtime

In standard applications, there is an effort made to have any data models be represented by corresponding declared
code constructs (such as Java classes). This locks down the model and it is difficult to mutate it while the
code is running. This project starts with the assumption that the data models are not necessarily represented
by corresponding Java classes. The model (or schema) is defined purely in terms of data constructions. This allows
the model to evolve either during the startup of the application or from input from a user (usually an administrator).

As an example of the type of solution that this approach can solve is to imagine an application that is designed to
create web based form entry for a random external database table. The administrator imports the design of the table into
the application, chooses which fields to show in the form, defines labels, descriptions, and validation rules
for the fields, entirely using a web UI. When the administrator is finished, a new form entry solution is
created based on a runtime modification to the underlying schema of the the application.

For me, this is not purely an academic issue. Over my three decades of programming the need to dynamically modify
runtime schema (usually at startup) has been a reoccurring theme. I have also found that though such solutions are
initially more difficult to write, they end up being more efficient and adaptable as the years go by.

At this point, the project is just getting started. The developers on this project will contribute when they
have time.

See [Dynamic Schema](core/src/main/resources/content/md/DynamicType.md) for information on how the DnType objects 
are defined.

For a web site that demos running the latest version of the code, see [dynamicruntime.org](https://dynamicruntime.org).
