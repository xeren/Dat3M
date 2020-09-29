package com.dat3m.dartagnan.wmm;

import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.utils.RegWriter;
import com.dat3m.dartagnan.wmm.filter.FilterAbstract;
import java.util.List;

public class ProgramCache {

	public final com.dat3m.dartagnan.program.Program program;

	public ProgramCache(com.dat3m.dartagnan.program.Program program) {
		this.program = program;
	}

	@FunctionalInterface
	public interface Thread {
		List<Event> cache(FilterAbstract filter);
	}

	public Thread[] thread() {
		return program.getThreads().stream()
			.map(com.dat3m.dartagnan.program.Thread::getCache)
			.map(t->(Thread)(t::getEvents))
			.toArray(Thread[]::new);
	}

	public List<Event> cache(FilterAbstract filter) {
		return program.getCache().getEvents(filter);
	}

	public List<RegWriter> cache(Register register) {
		List<RegWriter> result = program.getCache().getRegWriterMap().get(register);
		return null != result ? result : List.of();
	}

}
