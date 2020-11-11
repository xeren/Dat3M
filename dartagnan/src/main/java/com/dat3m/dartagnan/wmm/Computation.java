package com.dat3m.dartagnan.wmm;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

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

	public final HashMap<Object,Relation> relation = new HashMap<>();

	/**
	 * Initializes an empty computation.
	 * @param readFrom
	 * Maps compile identifiers of read events to the compile identifier of the write event being read from.
	 */
	public Computation(Map<Integer,Integer> readFrom) {
		this.readFrom = readFrom;
	}

	public void forEach(Consumer<Event> action) {
		thread.forEach(t->t.forEach(action));
	}

	public void forEachRead(Consumer<Event.Read> action) {
		readByCid.values().forEach(action);
	}

	public void forEachWrite(Consumer<Event.Write> action) {
		writeByCid.values().forEach(action);
	}

	public void forEachBranch(Consumer<Event.Branch> action) {
		forEach(x->{if(x instanceof Event.Branch)action.accept((Event.Branch)x);});
	}

	public void forEachLocation(BiConsumer<Event.Write,Event.Write> action) {
		location.values().forEach(l->{
			for(int i = 0; i < l.size(); ++i)
				for(int j = 0; j < i; ++j)
					action.accept(l.get(j), l.get(i));});
	}

	public void forEachThread(Consumer<List<Event>> action) {
		thread.forEach(action);
	}

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		for(int i = 0; i < thread.size(); ++i) {
			Thread t = thread.get(i);
			if(t.size() == 1 && t.get(0) instanceof Event.Init)
				continue;
			for(Event e : t) {
				s.append(i).append('.').append(e.threadIndex).append(':');
				if(e instanceof Event.Branch) {
					s.append('B');
				} else if(e instanceof Event.Init) {
					s.append('I').append(' ');
					//ASSUME there is only one match
					location.forEach((k,v)->{if(((Event.Init) e).location==v)s.append(k);});
				} else if(e instanceof Event.Fence) {
					s.append(((Event.Fence) e).name);
				} else if(e instanceof Event.Read) {
					s.append('R');
					Event.Write w = ((Event.Read) e).from;
					if(!(w instanceof Event.Init)) {
						s.append(' ');
						//ASSUME there is only one match
						for(int j = 0; j < thread.size(); ++j)
							if(w.thread==thread.get(j))
								s.append(j);
						s.append('.').append(w.threadIndex);
					}
				} else if(e instanceof Event.Write) {
					s.append('W').append(' ');
					//ASSUME there is only one match
					location.forEach((k,v)->{if(((Event.Write) e).location==v)s.append(k);});
				}
				s.append('\n');
			}
			s.append('\n');
		}
		return s.toString();
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
		 * @param id
		 * Compile identifier of the store, connecting to the read-from relation.
		 * @param key
		 * Address of the location being modified.
		 * @param keyRegister
		 * Registers being used for the address.
		 * @param valueRegister
		 * Registers being used for the value.
		 */
		public void write(int id, Object key, Set<?> keyRegister, Set<?> valueRegister) {
			LinkedList<Event.Write> l = location.computeIfAbsent(key, k->new LinkedList<>());
			Event.Write write = new Event.Write(id, this, l, dependency(keyRegister), dependency(valueRegister));
			l.add(write);
			writeByCid.put(id, write);
			for(Map.Entry<Integer,Integer> e : readFrom.entrySet()) {
				if(id == e.getValue()) {
					Event.Read read = readByCid.get(e.getKey());
					if(null != read)
						read.from = write;
				}
			}
		}

		/**
		 * Inserts a load event at the end of this thread's computation.
		 * @param id
		 * Compile identifier of the load.
		 * @param destination
		 * Register being modified.
		 * @param register
		 * Registers being used for the address.
		 */
		public void read(int id, Object destination, Set<?> register) {
			Event.Read read = new Event.Read(id, this, dependency(register));
			readByCid.put(id, read);
			Event.Write write = writeByCid.get(readFrom.get(id));
			if(null != write)
				read.from = write;
			HashSet<Event.Read> d = new HashSet<>();
			d.add(read);
			dependency.put(destination, d);
		}

		/**
		 * Inserts a memory barrier event at the end of this thread's computation.
		 * @param id
		 * Compile identifier of the barrier.
		 * @param name
		 * Identifies the type of barrier.
		 */
		public void fence(int id, String name) {
			new Event.Fence(id, this, name);
		}

		/**
		 * Inserts a branching event at the end of this thread's computation.
		 * @param id
		 * Compile identifier of the branch.
		 * @param register
		 * Registers being used for determining the direction of control flow.
		 */
		public void branch(int id, Set<?> register) {
			new Event.Branch(id, this, dependency(register));
		}

		/**
		 * Inserts an initial event at the end of this thread's computation.
		 * @param id
		 * Compile identifier of the event.
		 * @param key
		 * Identifies the location being modified.
		 */
		public void init(int id, Object key) {
			LinkedList<Event.Write> l = location.computeIfAbsent(key, k->new LinkedList<>());
			Event.Init init = new Event.Init(id, this, l);
			l.add(init);
			writeByCid.put(id, init);
			for(Map.Entry<Integer,Integer> e : readFrom.entrySet()) {
				if(id == e.getValue()) {
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

	public static class Relation {

		private final HashMap<Event,HashSet<Event>> max = new HashMap<>();

		private final HashMap<Event,HashSet<Event>> encode = new HashMap<>();

		private final LinkedList<BiConsumer<Event,Event>> parent = new LinkedList<>();

		public void addParent(BiConsumer<Event,Event> p) {
			parent.add(p);
			max.forEach((x,y)->y.forEach(z->p.accept(x, z)));
		}

		public void addMax(Event first, Event second) {
			if(max.computeIfAbsent(first, k->new HashSet<>()).add(second))
				parent.forEach(p->p.accept(first, second));
		}

		public boolean hasMax(Event first, Event second) {
			return max.containsKey(first) && max.get(first).contains(second);
		}

		public Stream<Event> maxByFirst(Event first) {
			return max.getOrDefault(first, new HashSet<>()).stream();
		}

		public Stream<Event> maxBySecond(Event second) {
			return max.entrySet().stream().filter(e->e.getValue().contains(second)).map(Map.Entry::getKey);
		}

		public boolean encode(Event first, Event second) {
			assert max.containsKey(first) && max.get(first).contains(second);
			return encode.computeIfAbsent(first, k->new HashSet<>()).add(second);
		}
	}
}
