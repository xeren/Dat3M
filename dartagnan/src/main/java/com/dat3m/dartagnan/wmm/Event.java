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
	public final int threadIndex;

	public final int id;

	/**
	 * Creates a new event.
	 * @param thread
	 * Sequence of events to be
	 */
	private Event(int id, List<Event> thread) {
		this.thread = thread;
		this.threadIndex = thread.size();
		this.id = id;
		thread.add(this);
	}

	public static class Write extends Event {

		public final LinkedList<Write> location;

		public final Set<Read> keyDependency;

		public final Set<Read> valueDependency;

		Write(int id, List<Event> thread, LinkedList<Write> location, Set<Read> dKey, Set<Read> dValue) {
			super(id, thread);
			this.location = location;
			keyDependency = dKey;
			valueDependency = dValue;
		}
	}

	public static class Read extends Event {

		public final Set<Read> dependency;

		public Write from;

		Read(int id, List<Event> thread, Set<Read> dependency) {
			super(id, thread);
			this.dependency = dependency;
		}
	}

	public static class Fence extends Event {

		public final String name;

		Fence(int id, List<Event> thread, String name) {
			super(id, thread);
			this.name = name;
		}
	}

	public static class Branch extends Event {

		public final Set<Read> dependency;

		Branch(int id, List<Event> thread, Set<Read> dependency) {
			super(id, thread);
			this.dependency = dependency;
		}
	}

	public static class Init extends Write {

		Init(int id, List<Event> thread, LinkedList<Write> location) {
			super(id, thread, location, Collections.emptySet(), Collections.emptySet());
		}
	}
}
