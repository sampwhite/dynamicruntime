# Definition of Dynamic Type

## Overview

This document defines the data format for the dynamic type, the basic building block of the application.
In a typical schema you define types which are then built up on top of fields. In our application,
we call the type definition **DnType** and the field definition **DnField**. We do this because the words
*type* and *field* are so common in coding and can refer to so many different concepts that using a variant
name will help make it clear that we are talking about types and fields that are the foundational elements
of this application.

### Primitive Types

These types have global definition, have no namespacing, and are reserved keywords and cannot
be reused when naming types. The primitive types are following.

* *String* - This is also the default type if no type information is given.
* *Boolean* - Either *true* or *false*. However, many times this is a tri-state value because
 the value of the field can be missing if it is not a required field.
* *Integer* - The value is an an eight byte integer.
* *Date* - The value is a date.
* *Float* - The value is floating point and also uses eight bytes (a *Double* in Java) to store its value.
* *Any* - Any value that can be a primitive type or can be represented by a JSON construct.
* *Map* - A map of strings to *Any* values.
* *Generic* - Represents a replaceable type whose choice of definition is based on another field in the input data.
 The rule for how this is determined depends on the specifics of the data being provided. A standard
 approach is to have a *name* attribute in the data, and then the *name* is used to find a type
 to replace the *Generic* type. This type can be useful for web form survey implementations where there can
 be a lot of variation in the questions asked.
* *None* - Means schema does not define a Json like data object. For endpoint requests, it means
the requests do not take ordinary parameters. As another example, *None* can represent either file uploads or file
downloads.

Before we go on we call a type *simple* if it has no fields and a recursive chase up the *base* types
leads to a primitive type. A type is *anonymous* if it is not captured into the DnSchemaStore. The data 
structure of the DnType is as follows.

### DnType
 * *name* - Name of type. Optional for anonymous types.
 * *label* - Optional friendly label for type.
 * *description* - Optional description of type.
 * *dnBuilder* - A post processing builder that builds out the type more fully based on other data in this
   type definition, usually using custom fields that show up in the *<other>* category. A discussion about builders
   can be found later in this document.
 * *baseType* - The base type this type extends. If this DnType adds no fields, then this type is considered to be
  the same as the base type for many purposes. Also, since the *baseType* attribute is a reference string to
  the name of a type, this gives a way of pulling in other types by name.  The *baseType*
  is optional. In many cases, the base type refers to one of the known primitive types. Note that a type is not
  allowed recursively extend itself. If that happens an error will occur during the processing.
 * *dnFields* - A list of DnField objects. If no fields are given then the type is assumed to essentially
  be the base type with extra supplementary data. or if there is no base type, then it is considered to represent
  a simple string value with supplementary data. If the base type also has fields, then these dnFields are
  prepended to the list of fields in the base type. If one of fields in the dnFields has the same name as 
  one of the fields in the base type, then the attributes in the dnField's field are merged into the field
  from the base type with the same name.
 * *noTrimming* - By default string values when validated by a schema are trimmed. Setting this prevents
  strings from being trimmed.
 * *isTable* - Type represents a table.
 * *noCommas* - The value of the field cannot have commas. If this is true and *isList* is true and
  a string is being parsed to populate the list, the list can be broken up by commas. This only applies
  to simple types whose primitive type is a String.
 * *min* - Only applies to simple types whose primitive type is numeric. This is minimum numeric value (inclusive)
 * *max* - Only applies if there are no fields present in this type and a recursive chase up the *baseType* references
 leads to a primitive numeric type. This is the maximum numeric value (exclusive).
 * *&lt;other&gt;* - Other attributes can be put into the schema for a private channel between code, data, and 
  presentation. It can also be used as inputs to builders. This is meant to get sufficiently complex 
  that additional *DnType* definitions will be  needed to defined the structure of this data. Or put it another
  way, the *DnType* has extended metadata that needs its own *DnType* to define its structure. This makes
  the *DnType* both a model definition and an instanced data  object simultaneously. This is a theme which
  this application intends to build on.
 
 When this type is cloned, all the top level attributes are put into a new Map and the top level attributes
 of the *dnFields* are cloned as well. Altering values interior to the values
 that are cloned will likely cause undesired side effects and should be avoided. In particular, the *dnTypeDef*
 attribute of the DnField object is only cloned if it needs to be modified.
 
 ### DnField
 * *name* - Name of field. Required if field is being used to capture data values.
 * *label* - Friendly label for field. Required if field is meant to hold data.
 * *description* - Description of field. Required if field is meant to hold data.
 * *dnTypeRef* - A reference to a DnType by name. This allows the type for the field to have independent
  existence. This field is ignored, if *dnTypeDef* is defined.
 * *dnTypeDef* - A full anonymous *DnType* object that is owned by this field and has no independent
  existence. Useful when interior nested types have meaning only in the context of their parent. When the DnField
  is cloned, this object is recursively cloned, cloning any referenced fields that also use a dnTypeDef to define
  their type. This type definition is considered to be immutable, it must be cloned in order to be modified.
 * *isList* - Whether the field represents a list of values instead of single value.
 * *typeIsChoices* - Whether the DnField's type definition represents choices with each DnField entry
  representing a choice.
 * *dnChoiceTypeRef* - If *typeIsChoices* is enabled, then this is the value type for the values of the choices.
  If this entry is missing then it is computed, first by looking at the type of the first DnField in the
  associated type and if there are no fields, then it is assumed to be a string.
 * *typeIsStrict* - If *typeIsChoices* is set, then it means the value must be one of the possible values. In
  other validations, it can be used to say that the validation should be applied in the strictest way.
 * *choiceValue* - If this field is being used for an entry in a choice list, this is the value to use for
  the choice. If this entry is missing, then the *name* of the field will be used instead.
 * *defaultValue* - If no value is supplied on input for this field, this is the value to supply instead.
  If this attribute is set then, *required* should not be set.
 * *required* - A non-empty value is required for this field.
 * *disabled* - The field is disabled and treated as if it were not present. For example, if one type extends
  another, the extending type can redeclare a field as disabled to remove that field from the type.
 * *sortRank* - A value that specifies which fields should be sorted before other fields in the list of fields.
 This is usually only explicitly set when two field lists are being merged with each other.
 The default sort is to keep the original field order that was used when the type was created with merged in fields
 being appended to existing fields. But sometimes it is useful to control the sort order so that certain fields
 (particularly protocol fields) come either earlier or later in the list. Also, when the schema is used as a choice
 list, it can be used to control how new entries get merged in with the existing entries. The *sortRank* is
 of particular interest to *DnBuilder* functions which want to make sure that their created types have the fields
 in the order they desire. The default sort rank of a field is 100.
 * *&lt;other&gt;* - Other attributes of the field definition, though in general, unlike with DnType, the attributes
  here should be relatively simple.
  
 ### DnBuilder
 
 During the design phase of the schema implementation, I looked at various ways to capture repeated patterns
 in schema definition. As an example, a traditional way to do this is to used parameterized types with
 the schema definition not fully defined until you give it externally supplied type definitions as parameters.
 I also looked at writing a generic build that would let you union the fields from various types together
 to build another type. This mimics the *traits* pattern that can be found in many programming languages.
 
 However, neither of these patterns fits json data models and database table definitions as I have seen them
 over the years. In many cases, the patterns that needed isolating from the main code came with a lot of
 presumptions of about the behavior of those fields. And many times, so many of the additional fields
 and types that I might add using parameterized types and traits were so conditional on certain characteristics
 of the deployment and the specific nature of the usage of the schema. It just made using standard
 programming language constructs for reusing schema definitions clunky and produced arcane artifacts. It
 was also very confusing to explain in a user interface what was going on with a particular type.
 
 In the following we describe the various DnBuilders available in the *core* component of this application.
 
 #### Endpoint Builder
 
 This builder, named *endpoint*, builds a DnType that can be used to define an endpoint.
 
 In addition to using the *name*, *label*, and *description* for documentation, it adds the following
 custom fields to the DnType definition.
 
  * inputTypeRef - The DnType used to validate the input.
  * outputTypeRef - The core part of the output DnType that is expanded upon by this builder.
  * path - The endpoint path, like */health/status* that this endpoint intercepts.
  * function - The name of the registered function that the endpoint calls to do the logic of the endpoint.
  * isListResponse - Whether the *outputTypeRef* is pushed down to become the DnType of a value in an array
   of items.  If this is true, then the builder adds a field called *limit* to the inputTypeRef that allows 
   the caller to limit the number of values returned.
  * hasMorePaging - Whether the response will set *hasMore* to true in the response data it there is more
    data available.
  * hasAvailableSize - Whether the response will indicate the total number of items that could be possibly returned.
  This is usually only done when the potential totalSize value is less than 10,000.
  * supportsOffset - Whether the endpoint supports an *offset* parameter in its call to allow
    the list values returned to start at an offset index. This would be added to the *inputTypeRef*.
    
 As an example of what the builder might do, let us assume the inputTypeRef named *Category* has two fields
 *category* and *fromDate*, the first is a String and the second is a Date. Assume the response
 type named *Content* has two fields *contentId* and *contentDate* with first being a string and the second
 a date.
 
 Suppose you defined the following type, using yaml style syntax.
 
```
name: CategoryRequest
label: Requests Content by Category
description: Allows requests by category starting after a particular date.
dnBuilder: endpoint
httpMethod: GET
path: /content/byCategory
function: getContent
inputTypeRef: Category
outputTypeRef: Content
isListResponse: true
hasMorePaging: true
ruleToGetContent: byCategory       
```

Note the extra custom parameter *ruleToGetContent*. This is visible
to the *getContent* function which allows the function to alter its behavior
based on the schema definition of the endpoint. One function can potentially
serve many endpoints.

Then the builder would produce the following type.

```
name: CategoryRequest
label: Requests Content by Category
description: Allows requests by category starting after a particular date.
dnBuilder: endpoint
httpMethod: GET
path: /content/byCategory
function: getContent
inputTypeRef: Category
outputTypeRef: Content
isListResponse: true
hasMorePaging: true
ruleToGetContent: byCategory
isEndpoint: true
dnFields: 
   - name: endpointInputType
     dnTypeDef:
         baseType: Category
         dnFields:
           - name: limit
             label: Limit on Results
             description: Maximum number of items that can be returned.
             defaultValue: 100
             sortRank: 200
             dnTypeDef:
                baseType: Count
                max: 20000
   - name: endpointOutputType
     dynTypeDef:
         dnFields:
           - name: numItems
             label: Number of Items
             description: Number of items returned.
             required: true
             dnTypeRef: Count
           - name: hasMore
             label: Has More Items
             description: The results returned are not all the results available if this value is true.
             dnTypeRef: Boolean
           - name: requestUri
             label: Request URI
             description: The request URI that made this request.
             required: true
           - name: duration
             label: Duration
             description: The time taken to perform request in milliseconds.
             required: true
             dnTypeRef: Float
           - name: hasMore
             label Has More
             description: Set to true if list of items is incomplete
             required: true
             dnTypeRef: Boolean
           - name: items
             label: Items
             description: Items returned by endpoint.
             required: true
             isList: true
             dnTypeRef: Content
```

The reference to the *dnType* Count is a reference to a built in type that extends
Integer and has a *min* value of zero.

If you look at the above, there are some things to call out. The first is that the
DnType *Category* is extended to have an additional *limit* field that can be used to
limit the results. The second is that the DnType *Content* has been subsumed into a containing type
so that the *items* field of the containing type has *Content* has the of one of its objects. The values 
*duration*, *requestUri*, and *none* are all handled by generic protocol handlers and
are invisible to the function *getContent*.

Another DnBuilder is the *table* builder, which we will expand on at a later point. But it can
take input parameters like *primaryKey* and *indexes*. The builder would then add standard protocol
fields like *creationDate* and *updatedDate*. Then code that interacts with the table could look
at the table design and automatically create based parameterized queries to query, insert, and update rows.

 ### Namespacing
 
 One of the reasons when cloning is needed is so that a DnType can be put into a particular namespace, 
 which alters the name of its type. Also, any fields referring to a base type also may have that reference 
 adjusted as well. Essentially, if the name of a DnType does not have a dot ('.') in it, then the name space is 
 added a prefix separated by a dot. This allows the definition of the raw type to be done without reference
 to a namespace and the namespacing applied later.
 