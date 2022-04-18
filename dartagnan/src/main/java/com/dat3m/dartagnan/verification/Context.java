package com.dat3m.dartagnan.verification;

import com.dat3m.dartagnan.exception.UnsatisfiedRequirementException;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.MutableClassToInstanceMap;

import static com.google.common.base.Preconditions.checkNotNull;

public class Context {
    private final ClassToInstanceMap<Object> metaDataMap;
    private final VerificationTask task;

    private Context(VerificationTask t) {
        metaDataMap = MutableClassToInstanceMap.create();
        task = checkNotNull(t);
    }

    public static Context create(VerificationTask task) {
        return new Context(task);
    }

    public static Context createCopyFrom(VerificationTask task, Context context) {
        Context ctx = new Context(task);
        ctx.metaDataMap.putAll(context.metaDataMap);
        return ctx;
    }

    // =============================================

    /**
     * Collection of program, memory model and specification which the analyses in this instance refer to.
     */
    public VerificationTask getTask() {
        return task;
    }

    // =============================================

    public <T> boolean has(Class<T> c) {
        return metaDataMap.containsKey(c);
    }

    public <T> T get(Class<T> c) {
        return metaDataMap.getInstance(c);
    }

    public <T> boolean invalidate(Class<T> c) {
        return metaDataMap.remove(c) == null;
    }

    public <T> boolean register(Class<T> c, T instance) {
        if (has(c)) {
            return false;
        }
        metaDataMap.putInstance(c, instance);
        return true;
    }

    public <T> T requires(Class<T> c) {
        T instance = get(c);
        if (instance == null) {
            throw new UnsatisfiedRequirementException("Requires " + c.getSimpleName());
        }
        return instance;
    }

}
