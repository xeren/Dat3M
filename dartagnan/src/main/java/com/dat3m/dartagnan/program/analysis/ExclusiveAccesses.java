package com.dat3m.dartagnan.program.analysis;

import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.core.Event;
import com.dat3m.dartagnan.program.event.core.MemEvent;
import com.dat3m.dartagnan.program.filter.FilterBasic;
import com.dat3m.dartagnan.verification.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import static com.dat3m.dartagnan.program.event.Tag.EXCL;
import static com.dat3m.dartagnan.program.event.Tag.READ;
import static com.dat3m.dartagnan.program.event.Tag.WRITE;
import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;

/**
 * Detects all reserve-load/exclusive-store pairs in a program.
 */
@Options
public final class ExclusiveAccesses {

    private static final Logger logger = LogManager.getLogger(Dependency.class);

    private final HashMap<MemEvent,List<LoadInfo>> result = new HashMap<>();

    private ExclusiveAccesses() {}

    /**
     * Performs a dependency analysis on a program.
     * @param program
     * Instruction lists to be analyzed.
     * @param analysisContext
     * Collection of other analyses previously performed on {@code program}.
     * Should include {@link ExecutionAnalysis}.
     * @param config
     * Mapping from keywords to values,
     * further specifying the behavior of this analysis.
     * See this class's list of options for details.
     * @throws InvalidConfigurationException
     * Some option was provided with an unsupported type.
     */
    public static ExclusiveAccesses fromConfig(Program program, Context analysisContext, Configuration config) throws InvalidConfigurationException {
        logger.info("Analyze exclusive accesses");
        ExecutionAnalysis exec = analysisContext.requires(ExecutionAnalysis.class);
        ExclusiveAccesses result = new ExclusiveAccesses();
        config.inject(result);
        for(Thread t: program.getThreads()) {
            result.process(t, exec);
        }
        logger.info("Found {} possible exclusive operations",result.result.size());
        return result;
    }

    /**
     * The results of this analysis are grouped by the store operation.
     * @return
     * Complete collection of exclusive store events.
     * Maps possible pairs to the set of intermediates that could disable the relationship.
     * @see #getLoads(MemEvent)
     */
    public Collection<MemEvent> getStores() {
        return result.keySet();
    }

    /**
     * @param store
     * Exclusive store event of the analyzed program.
     * @return
     * Complete collection of reserving loads directly program-ordered before {@code store}.
     * Each load also comes equipped with a complete list of possible intermediates.
     */
    public List<LoadInfo> getLoads(MemEvent store) {
        checkArgument(store.is(EXCL) && store.is(WRITE),"expected ");
        return result.get(store);
    }

    /**
     * Instances of this class are indirectly linked to a store operation in the analyzed program.
     * They are decorated with the collection of program-ordered-between events which would also form such pairs.
     * Since reserve-load/exclusive-store pairings should be
     */
    public static final class LoadInfo {
        public final MemEvent load;
        public final List<MemEvent> intermediates;
        private LoadInfo(MemEvent l, List<MemEvent> i) {
            load = l;
            intermediates = i;
        }
    }

    private void process(Thread thread, ExecutionAnalysis exec) {
        List<Event> events = thread.getCache().getEvents(FilterBasic.get(EXCL));
        int end = events.size();
        for(int i = 0; i < end; i++) {
            Event store = events.get(i);
            if(!store.is(WRITE)) {
                continue;
            }
            int iFinal = i;
            int first = range(1,i+1)
            .map(j->iFinal-j)
            .filter(j -> exec.isImplied(store,events.get(j)))
            .findFirst()
            .orElse(0);
            ArrayList<LoadInfo> info = new ArrayList<>(i - first);
            for(int j = first; j < i; j++) {
                Event load = events.get(j);
                if(load.is(READ) && events.subList(j+1,i).stream().noneMatch(e -> exec.isImplied(load,e))) {
                    info.add(new LoadInfo((MemEvent)load,events.subList(j+1,i).stream()
                        .filter(e -> !exec.areMutuallyExclusive(load,e) && !exec.areMutuallyExclusive(e,store))
                        .map(MemEvent.class::cast)
                        .collect(toList())));
                }
            }
            if(info.size() != 1) {
                logger.info("{} has {} possible reserve-loads",store,info.size());
            }
            result.put((MemEvent)store,info);
        }
    }
}
