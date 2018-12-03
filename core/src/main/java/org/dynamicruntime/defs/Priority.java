package org.dynamicruntime.defs;

/** Place holder class to hold priority order. Note that we do not use Enums (besides a general
 * avoidance in this whole application) because a typical value somebody might supply is
 * *STANDARD - 1* or *LATE + 1*/
public class Priority {
    public static final int VERY_EARLY = 100;
    public static final int EARLY = 200;
    public static final int STANDARD = 300;
    public static final int LATE = 400;
    public static final int VERY_LATE = 400;
}
