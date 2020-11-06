package com.dat3m.dartagnan.wmm;

import java.util.*;

/**
 * Describes one computation of a program.
 * Each thread executes a certain sequence of events in order.
 * Each read event is assigned a write event.
 * All memory events are assigned the collection of memory events to the same address.
 * @author r.maseli@tu.bs.de
 */
public class Computation {

	private final Map<Integer,Integer> readFrom;

	private final HashMap<Integer,Event.Read> readByCid = new HashMap<>();

	private final HashMap<Integer,Event.Write> writeByCid = new HashMap<>();

	private final LinkedList<Thread> thread = new LinkedList<>();

	private final HashMap<Object,LinkedList<Event.Write>> location = new HashMap<>();

	/**
	 * Initializes an empty computation.
	 * @param readFrom
	 * Maps compile identifiers of read events to the compile identifier of the write event being read from.
	 */
	public Computation(Map<Integer,Integer> readFrom) {
		this.readFrom = readFrom;
	}

	/**
	 * Used to insert events that originate from a common thread into the computation.
	 * Preserves the perceived program order.
	 * Tracks data, address and control dependencies.
	 */
	public class Thread extends ArrayList<Event> {

		private final HashMap<Object,HashSet<Event.Read>> dependency = new HashMap<>();

		/**
		 * Starts building a new thread.
		 */
		public Thread() {
			thread.add(this);
		}

		/**
		 * Inserts a thread-local evaluation at the end of this thread's computation.
		 * Carries dependencies from its parameters to its result.
		 * Events of this type are invisible to the memory model.
		 * @param destination
		 * Register being modified.
		 * @param source
		 * Registers being used.
		 */
		public void local(Object destination, Set<?> source) {
			HashSet<Event.Read> result = new HashSet<>();
			for(Object register : source) {
				HashSet<Event.Read> s = dependency.get(register);
				if(null != s)
					result.addAll(s);
			}
			dependency.put(destination, result);
		}

		/**
		 * Inserts a store event at the end of this thread's computation.
		 * @param cId
		 * Compile identifier of the store, connecting to the read-from relation.
		 * @param key
		 * Address of the location being modified.
		 * @param keyRegister
		 * Registers being used for the address.
		 * @param valueRegister
		 * Registers being used for the value.
		 */
		public void write(int cId, Object key, Set<?> keyRegister, Set<?> valueRegister) {
			LinkedList<Event.Write> l = location.computeIfAbsent(key, k->new LinkedList<>());
			Event.Write write = new Event.Write(this, l, dependency(keyRegister), dependency(valueRegister));
			l.add(write);
			writeByCid.put(cId, write);
			for(Map.Entry<Integer,Integer> e : readFrom.entrySet()) {
				if(e.getValue() == cId) {
					Event.Read read = readByCid.get(e.getKey());
					if(null != read)
						read.from = write;
				}
			}
		}

		/**
		 * Inserts a load event at the end of this thread's computation.
		 * @param cId
		 * Compile identifier of the load.
		 * @param destination
		 * Register being modified.
		 * @param register
		 * Registers being used for the address.
		 */
		public void read(int cId, Object destination, Set<?> register) {
			Event.Read read = new Event.Read(this, dependency(register));
			readByCid.put(cId, read);
			Event.Write write = writeByCid.get(readFrom.get(cId));
			if(null != write)
				read.from = write;
			HashSet<Event.Read> d = new HashSet<>();
			d.add(read);
			dependency.put(destination, d);
		}

		/**
		 * Inserts a memory barrier event at the end of this thread's computation.
		 * @param name
		 * Identifies the type of barrier.
		 */
		public void fence(String name) {
			new Event.Fence(this, name);
		}

		/**
		 * Inserts a branching event at the end of this thread's computation.
		 * @param register
		 * Registers being used for determining the direction of control flow.
		 */
		public void branch(Set<?> register) {
			new Event.Branch(this, dependency(register));
		}

		/**
		 * Inserts an initial event at the end of this thread's computation.
		 * @param cId
		 * Compile identifier of the event.
		 * @param key
		 * Identifies the location being modified.
		 */
		public void init(int cId, Object key) {
			LinkedList<Event.Write> l = location.computeIfAbsent(key, k->new LinkedList<>());
			Event.Init init = new Event.Init(this, l);
			l.add(init);
			writeByCid.put(cId, init);
			for(Map.Entry<Integer,Integer> e : readFrom.entrySet()) {
				if(e.getValue() == cId) {
					Event.Read read = readByCid.get(e.getKey());
					if(null != read)
						read.from = init;
				}
			}
		}

		private Set<Event.Read> dependency(Set<?> registers) {
			HashSet<Event.Read> result = new HashSet<>();
			for(Object register : registers) {
				HashSet<Event.Read> s = dependency.get(register);
				if(null != s)
					result.addAll(s);
			}
			return result;
		}
	}
}
