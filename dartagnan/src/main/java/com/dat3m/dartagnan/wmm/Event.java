package com.dat3m.dartagnan.wmm;
import java.util.*;

/**
 * Node in a computation of a program.
 * As opposed to {@link com.dat3m.dartagnan.program.event.Event},
 * this class defines only those information needed by a memory model.
 */
public abstract class Event {

	/**
	 * List of events this event belongs to.
	 * Conforms to the program order of the computation.
	 */
	public final List<Event> thread;

	/**
	 * Index of this event in {@link #thread}.
	 */
	public final int id;

	/**
	 * Creates a new event.
	 * @param thread
	 * Sequence of events to be
	 */
	private Event(List<Event> thread) {
		this.thread = thread;
		this.id = thread.size();
		thread.add(this);
	}

	public static class Write extends Event {

		public final LinkedList<Write> location;

		public final Set<Read> keyDependency;

		public final Set<Read> valueDependency;

		Write(List<Event> thread, LinkedList<Write> location, Set<Read> dKey, Set<Read> dValue) {
			super(thread);
			this.location = location;
			keyDependency = dKey;
			valueDependency = dValue;
		}
	}

	public static class Read extends Event {

		public final Set<Read> dependency;

		public Write from;

		Read(List<Event> thread, Set<Read> dependency) {
			super(thread);
			this.dependency = dependency;
		}
	}

	public static class Fence extends Event {

		public final String name;

		Fence(List<Event> thread, String name) {
			super(thread);
			this.name = name;
		}
	}

	public static class Branch extends Event {

		public final Set<Read> dependency;

		Branch(List<Event> thread, Set<Read> dependency) {
			super(thread);
			this.dependency = dependency;
		}
	}

	public static class Init extends Write {

		Init(List<Event> thread, LinkedList<Write> location) {
			super(thread, location, Collections.emptySet(), Collections.emptySet());
		}
	}
}
