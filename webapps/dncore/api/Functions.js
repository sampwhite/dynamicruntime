/** These are global functions that are commonly used and they are deliberately given short names for convenience. */
import Client from './Client'

/**
 * Before providing a link to the various objects that take relative URL parameters, this function should
 * be called on the link first. If you have URL parameters (the part that goes after the question mark), you
 * can supply them in an object as a second parameter.
 */
export function dnl(relativePath, queryArgs) {
    return Client.createNavUrl(relativePath, queryArgs)
}