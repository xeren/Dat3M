package com.dat3m.dartagnan.program.utils;

import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.MemEvent;
import com.dat3m.dartagnan.Event;
import com.dat3m.dartagnan.program.event.utils.RegReaderData;
import com.dat3m.dartagnan.program.event.utils.RegWriter;
import com.dat3m.dartagnan.wmm.filter.FilterAbstract;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Stream.of;

public class ThreadCache {

	private Map<FilterAbstract,List<Event>> events = new HashMap<>();
	private Set<Register> registers;
	private Map<Register,List<RegWriter>> regWriterMap;

	public ThreadCache(List<?extends Event> events) {
		this.events.put(FilterBasic.get(EType.ANY), List.copyOf(events));
	}

	public List<Event> getEvents(FilterAbstract filter) {
		return events.computeIfAbsent(filter, k->getEvents(FilterBasic.get(EType.ANY)).stream().filter(filter::filter)
			.collect(Collectors.toUnmodifiableList()));
	}

	public Set<Register> getRegisters() {
		if(registers == null) {
			registers = of(
				getEvents(FilterBasic.get(EType.REG_WRITER)).stream().map(RegWriter.class::cast).map(RegWriter::getResultRegister),
				getEvents(FilterBasic.get(EType.REG_READER)).stream().map(RegReaderData.class::cast).map(RegReaderData::getDataRegs).flatMap(Collection::stream),
				getEvents(FilterBasic.get(EType.MEMORY)).stream().map(MemEvent.class::cast).map(MemEvent::getAddress).map(Register::of).flatMap(Collection::stream))
				.flatMap(x->x)
				.collect(Collectors.toUnmodifiableSet());
		}
		return registers;
	}

	public Map<Register,List<RegWriter>> getRegWriterMap() {
		if(regWriterMap == null) {
			regWriterMap = getEvents(FilterBasic.get(EType.REG_WRITER)).stream().map(RegWriter.class::cast)
				.collect(Collectors.groupingBy(RegWriter::getResultRegister,
					Collectors.collectingAndThen(Collectors.toCollection(TreeSet::new), List::copyOf)));
		}
		return regWriterMap;
	}
}
