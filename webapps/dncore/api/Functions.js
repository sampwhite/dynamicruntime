/** These are global functions that are commonly used and they are deliberately given short names for convenience. */
import Client from './Client'
import moment from 'moment';

/**
 * Before providing a link to the various objects that take relative URL parameters, this function should
 * be called on the link first. If you have URL parameters (the part that goes after the question mark), you
 * can supply them in an object as a second parameter.
 */
export function dnl(relativePath, queryArgs) {
    return Client.createNavUrl(relativePath, queryArgs)
}

/** Returns a presentation string of a date that will report hours, minutes, or seconds ago for a time in the past
 * if the time in the past is recent. Otherwise, it will give the month and day (or year month and day if it is
 * more than 100 days ago). */
export function timeAgo(dt) {
    const curDate = new Date();
    const secondsAgo = Math.round((curDate.getTime() - dt.getTime())/1000);
    if (secondsAgo < 2) {
        return "Just Now";
    }

    if (secondsAgo < 60) {
        return "" + secondsAgo + " Secs Ago";
    }

    if (secondsAgo >= 60 && secondsAgo < 120) {
        return "One Min Ago"
    }

    if (secondsAgo < 3600) {
        return "" + Math.round(secondsAgo/60) + " Mins Ago";
    }

    if (secondsAgo >= 3600 && secondsAgo < 7200) {
        return "One Hr Ago";
    }

    if (secondsAgo < 24 * 3600) {
        return "" + Math.round(secondsAgo/3600) + " Hrs Ago"
    }

    const days = Math.round(secondsAgo/(24 * 3600));
    if (days < 100) {
        return moment(dt).format('MMM Do');
    }
    return moment(dt).format('MMM YYYY')
}