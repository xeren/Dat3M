package com.dat3m.dartagnan.program.utils;

import com.dat3m.dartagnan.program.event.*;
import com.dat3m.dartagnan.wmm.filter.FilterAbstract;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.google.common.collect.ImmutableList;
import java.util.*;
import static java.util.stream.Collectors.toList;//TODO toUnmodifiableList

/**
Represents a thread of a compiled program.
*/
public class ThreadCache {

	private final ImmutableList<Event> all;
	private final Map<FilterAbstract,List<Event>> tag = new HashMap<>();
	private final HashMap<Class<?>,List<?>> subclass = new HashMap<>();

	/**
	Tracks information about a compiled thread.
	@param events
	List of all events reachable in this thread.
	First element must be the thread's entry point.
	All jumps must be directed forwards.
	*/
	public ThreadCache(List<Event> events) {
		all = ImmutableList.copyOf(events);
		tag.put(FilterBasic.get(EType.ANY), all);
		subclass.put(Event.class, all);
	}

	/**
	Subsequence of tagged events.
	@param filter
	Proposition of tags.
	@return
	Immutable list of events satisfying the filter in order of appearance.
	*/
	public List<Event> getEvents(FilterAbstract filter) {
		return tag.computeIfAbsent(filter, k->all.stream().filter(filter::filter).collect(toList()));
	}

	/**
	Subsequence of instances of a certain interface or class.
	@param cls
	Class object characterizing the targeted elements.
	@return
	Immutable list of events in order of appearance.
	*/
	@SuppressWarnings("unchecked")
	public <T> List<T> getEvents(Class<T> cls) {
		return (List<T>) subclass.computeIfAbsent(cls, k->all.stream().filter(cls::isInstance).collect(toList()));
	}
}
