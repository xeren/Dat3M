package com.dat3m.dartagnan.wmm;
import java.util.*;

public abstract class Event {

	public final Object thread;

	public final int id;

	public Event(Object thread, int id) {
		this.thread = thread;
		this.id = id;
	}

	public static class Write extends Event {

		public final long key;

		public final long value;

		public final Set<Read> keyDependency;

		public final Set<Read> valueDependency;

		public Write(Object thread, int id, long key, long value, Set<Read> dKey, Set<Read> dValue) {
			super(thread, id);
			this.key = key;
			this.value = value;
			keyDependency = dKey;
			valueDependency = dValue;
		}
	}

	public static class Read extends Event {

		public final Set<Read> dependency;

		public Write from;

		public Read(Object thread, int id, Set<Read> dependency) {
			super(thread, id);
			this.dependency = dependency;
		}
	}

	public static class Fence extends Event {

		public final String name;

		public Fence(Object thread, int id, String name) {
			super(thread, id);
			this.name = name;
		}
	}

	public static class Branch extends Event {

		public final Set<Read> dependency;

		public Branch(Object thread, int id, Set<Read> dependency) {
			super(thread, id);
			this.dependency = dependency;
		}
	}

	public static class Init extends Write {

		public Init(Object thread, int id, long address, long value) {
			super(thread, id, address, value, Collections.emptySet(), Collections.emptySet());
		}
	}
}
