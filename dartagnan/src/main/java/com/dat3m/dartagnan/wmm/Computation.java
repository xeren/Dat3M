package com.dat3m.dartagnan.wmm;

import java.util.*;

public class Computation {

	private final Map<Integer,Integer> readFrom;

	private final HashMap<Integer,Event.Read> readByCid = new HashMap<>();

	private final HashMap<Integer,Event.Write> writeByCid = new HashMap<>();

	public Computation(Map<Integer,Integer> readFrom) {
		this.readFrom = readFrom;
	}

	@Override
	public String toString() {
		return super.toString();
	}

	public class Thread {

		private final Object id;

		private final ArrayList<Event> event = new ArrayList<>();

		private final HashMap<Object,HashSet<Integer>> dependency = new HashMap<>();

		public Thread(Object id) {
			this.id = id;
		}

		public ArrayList<Event> getVisible() {
			return event;
		}

		/**
		 * Inserts a thread-local computation.
		 * @param destination
		 * Register being modified.
		 * @param source
		 * Registers being used.
		 */
		public void local(Object destination, Set<?> source) {
			HashSet<Integer> result = new HashSet<>();
			for(Object register : source) {
				HashSet<Integer> s = dependency.get(register);
				if(null != s)
					result.addAll(s);
			}
			dependency.put(destination, result);
		}

		/**
		 * Inserts a store event.
		 * @param cId
		 * Compile identifier of the store, connecting to the read-from relation.
		 * @param key
		 * Address of the location being modified.
		 * @param value
		 * Value being written.
		 * @param keyRegister
		 * Registers being used for the address.
		 * @param valueRegister
		 * Registers being used for the value.
		 */
		public void write(int cId, long key, long value, Set<?> keyRegister, Set<?> valueRegister) {
			Event.Write write = new Event.Write(id, event.size(), key, value, dependency(keyRegister), dependency(valueRegister));
			event.add(write);
			writeByCid.put(cId, write);
			for(Map.Entry<Integer,Integer> e : readFrom.entrySet()) {
				if(e.getValue() == cId) {
					Event.Read read = readByCid.get(e.getKey());
					if(null != read)
						read.from = write;
				}
			}
		}

		public void read(int cId, Object destination, Set<?> register) {
			int index = event.size();
			Event.Read read = new Event.Read(id, index, dependency(register));
			event.add(read);
			readByCid.put(cId, read);
			Event.Write write = writeByCid.get(readFrom.get(cId));
			if(null != write)
				read.from = write;
			HashSet<Integer> d = new HashSet<>();
			d.add(index);
			dependency.put(destination, d);
		}

		public void fence(String name) {
			event.add(new Event.Fence(id, event.size(), name));
		}

		public void branch(Set<?> register) {
			event.add(new Event.Branch(id, event.size(), dependency(register)));
		}

		public void init(int cId, long key, long value) {
			Event.Init init = new Event.Init(id, event.size(), key, value);
			event.add(init);
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
				HashSet<Integer> s = dependency.get(register);
				if(null == s)
					continue;
				for(Integer i : s)
					result.add((Event.Read)event.get(i));
			}
			return result;
		}
	}
}
