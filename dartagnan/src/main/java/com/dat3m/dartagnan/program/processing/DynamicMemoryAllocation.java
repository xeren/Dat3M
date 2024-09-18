package com.dat3m.dartagnan.program.processing;

import com.dat3m.dartagnan.configuration.OptionNames;
import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.ExpressionFactory;
import com.dat3m.dartagnan.expression.type.TypeFactory;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.event.core.Alloc;
import com.dat3m.dartagnan.program.memory.MemoryObject;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;

/*
    This pass
        (1) collects all Alloc events in the program and generates a corresponding MemoryObject
        (2) for all MemoryObjects, it generates corresponding Init events if they are required
 */
@Options
public class DynamicMemoryAllocation implements ProgramProcessor {

    // =========================== Configurables ===========================

    @Option(name = OptionNames.INIT_DYNAMIC_ALLOCATIONS,
            description = "Creates init events for all dynamic allocations. Those init events zero out the memory.",
            secure = true)
    private boolean createInitsForDynamicAllocations = false;

    // ======================================================================


    private DynamicMemoryAllocation() {
    }

    public static DynamicMemoryAllocation newInstance() {
        return new DynamicMemoryAllocation();
    }

    public static DynamicMemoryAllocation fromConfig(Configuration config) throws InvalidConfigurationException {
        final DynamicMemoryAllocation memAlloc = new DynamicMemoryAllocation();
        config.inject(memAlloc);
        return memAlloc;
    }

    @Override
    public void run(Program program) {
        processAllocations(program);
    }

    private void processAllocations(Program program) {
        final ExpressionFactory expressions = ExpressionFactory.getInstance();
        // FIXME: We should probably initialize depending on the allocation type of the alloc
        final Expression zero = expressions.makeZero(TypeFactory.getInstance().getByteType());
        for (Alloc alloc : program.getThreadEvents(Alloc.class)) {
            final MemoryObject allocatedObject = program.getMemory().allocate(alloc);
            alloc.setAllocatedObject(allocatedObject);

            if (alloc.doesZeroOutMemory() || createInitsForDynamicAllocations) {
                for (int i = 0; i < allocatedObject.size(); i++) {
                    allocatedObject.setInitialValue(i, zero);
                }
            }
        }
    }
}
