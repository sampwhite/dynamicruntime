package org.dynamicruntime.hook;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;

/**
 * This is an interface that implements a general hook following patterns that we have found to be successful over the
 * last few decades. Since the pattern of using hooks is critical in creating flexible architectures, we
 * give some additional thoughts on the subject here. First let us look at the generic types U and V.
 *
 * ### The Hook Parameters
 *
 * U -- The *parent* object. This object should supply API calls that the hook functions can use to
 * interact with their surrounding context. In some cases, instead of making an API call directly
 * using a known internal function, the parent should supply an equivalent API call. This allows
 * the parent to create variations in implementations in internal calls. As a typical example of such
 * a call, the hook may want to query a database for information.
 *
 * V -- The *workData* object. In a typical scenario for how a hook gets created, some complex code, which
 * did not have a hook, adds a hook call to augment the behavior of the code. For example, a financial
 * transaction may have hooks to talk to 3rd party validators of extended financial services. At the time
 * the hook gets created, all the local variables that are in the code are put into a new Java object
 * that holds a field for each local variable. The code then interacts with this object instead of the
 * local variables it had before. It then supplies the *local variables object* as the *workData* object
 * to the hook call. When the original code is modified to have more *local* variables, these variables are added
 * to the work object.
 *
 * When implementing hooks it is a good idea to reuse U, V patterns (use the same set of classes or interfaces).
 * This approach may create burdens in packaging and un-packaging inputs, but it is worth
 * the additional labors. In the past we have discovered that certain types of repeated code patterns build
 * up around hook implementations. Reusing U, V patterns (or at least reusing shared interfaces) allows
 * us to reuse code instead of writing the same code repeatedly.
 *
 * ### When and How To Use Hooks
 *
 * When it comes to adding hooks or varying the parameters that are passed to them, there are two general
 * strategies. The first is to try to anticipate the hooks that are needed and then create a well-defined
 * and published set of hooks whose implementation details are described as unvarying contracts with outside
 * code. We have found this approach to generally be a failure.
 *
 * The second approach is for a writer of external code to look at the base code set and say, "I would like
 * to vary the behavior of this code at the particular location." Normally what might happen at this point, is
 * that the writer of the new code may then edit the base code to do what they need, hard-wiring implementation
 * details that are particular to that writer's needs. But a better approach is for the writer to add a "hook"
 * to the code and create a *workData* object as described above to capture all the "working locals". Adding
 * the hook is minimally invasive and allows other writers to come along and add their own custom behaviors. We
 * call this approach a "hook on demand" approach and it has been quite successful. Also, changes to add hooks
 * can be quickly reviewed and approved because the hook by itself (normally, there are exceptions for
 * larger changes) does not change functionality or behavior in the base code (in particular it does not
 * need new tests, existing tests can be used to verify that nothing was broken by adding the hook).
 *
 * Once you start using hooks, they become a good place to add metric reporting and *timing* (how long did
 * a hook take to execute) code. Over time, for this project, we expect to add such instrumentation. Hooks are
 * also good places to have debug flags (that can be turned on) to turn on dumping of the current work data to
 * the log. And as a last point, but not minor, hooks are a great place to put debug breakpoints.
 *
 * There is one other advantage of hooks. If code is misbehaving, many times it is possible to turn off hooks
 * at various places in the application and see if the code continues misbehavior or can be isolated to a particular
 * hook call (or a set of hook calls). It can help answer the question, is the bug a *core* behavior or one
 * introduced by a complex component added to the application.
 *
 * ### Notes
 *
 * The V parameter should have good *toString()* implementations so that it can be dumped to the log
 * output if needed and viewed quickly during debug.
 *
 * Each implementation of this interface should have singleton instances created for it. When defining the class,
 * declare it as a real class (not anonymous). This way in a Java stack dump, the hook call shows up as an easily
 * visible line item, making it available to full text search engines that search aggregations of log files.
 *
 * The *DnHookTypeInterface* supplies the method *registerHookFunction* for registering hooks functions.
 */
public interface DnHookBase<U,V> extends DnHookTypeInterface<DnHookFunction<U,V>> {
    // The normal thing to do is implement this calling *callHookImpl*. This
    // injects the method into the call stack attached to the object that implements the interface.
    boolean callHook(DnCxt cxt, U parent, V workData) throws DnException;

    default boolean callHookImpl(DnCxt cxt, U parent, V workData) throws DnException {
        var hook = cxt.instanceConfig.getHook(this);
        // First do executions and then notifications.

        // Only execute until one entry says that it took care of the task.
        boolean retVal = false;
        for (var entry : hook.entries) {
            if (entry.function.execute(cxt, parent, workData)) {
                // Execution finished.
                retVal = true;
                break;
            }
        }

        // Unconditional execution.
        for (var entry: hook.entries) {
            entry.function.notify(cxt, parent, workData);
        }
        return retVal;
    }
}
