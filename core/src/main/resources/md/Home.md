# Home Page

## Overview

Welcome to Dynamic Runtime, an open source project to create a flexible Java application that can scale in both
complexity and load. Go to the [Github Dynamic Runtime Project](https://github.com/sampwhite/dynamicruntime) for
more.

The first batch of endpoints have been created.

* Health Info - [/health/info](/health/info)
* List of schema DnTypes - [/schema/dnType/list](/schema/dnType/list) Try out parameters *limit*, *dnTypeName*,
and *namespace*. Most (currently all) of the types are present in support of the endpoints.
* List of endpoints - [/schema/endpoint/list](/schema/endpoint/list) Try out parameters *limit* and *pathPrefix*. Note
that if you read the output of this endpoint carefully you can determine the legal parameters to any of the
endpoints.