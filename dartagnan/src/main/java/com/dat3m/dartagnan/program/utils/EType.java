package com.dat3m.dartagnan.program.utils;

public class EType {

    public static final String ANY          = "_";
    public static final String INIT         = "IW";
    public static final String READ         = "R";
    public static final String WRITE        = "W";
    public static final String MEMORY       = "M";
    public static final String FENCE        = "F";
    public static final String RMW          = "RMW";
    public static final String LOCAL        = "T";
    public static final String VISIBLE      = "V";
    public static final String ASSERTION    = "ASS";
    public static final String EXCLUSIVE    = "arm.exclusive";
    public static final String RELAXED      = "relaxed";
    public static final String CONSUME      = "consume";
    public static final String RELEASE      = "release";
    public static final String ACQUIRE      = "acquire";
    public static final String ACQ_REL      = "release-acquire";
    public static final String SC           = "sequential";
    public static final String ONCE         = "linux.once";
    public static final String MB           = "linux.barrier";
    public static final String RCU_SYNC     = "Sync-rcu";
    public static final String RCU_LOCK     = "Rcu-lock";
    public static final String RCU_UNLOCK   = "Rcu-unlock";
}
