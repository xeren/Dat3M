package com.dat3m.dartagnan.parsers.program.utils;

import com.dat3m.dartagnan.asserts.AbstractAssert;
import com.dat3m.dartagnan.expression.BExpr;
import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IConst;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.*;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.memory.Address;
import com.dat3m.dartagnan.program.memory.Location;
import com.dat3m.dartagnan.program.memory.Memory;
import com.google.common.collect.ImmutableSet;

import java.util.*;
import java.util.stream.Collectors;

public class ProgramBuilder {

	private final Map<Integer,T> threads = new HashMap<>();

	private final Map<String,Location> locations = new HashMap<>();
	private final Map<String,Address> pointers = new HashMap<>();

	private final Map<Address,IConst> iValueMap = new HashMap<>();
	private final Memory memory = new Memory();

	private AbstractAssert ass;
	private AbstractAssert assFilter;

	public Program build() {
		for(T t : threads.values()) {
			if(!t.labelPending.isEmpty()) {
				throw new ParsingException("Unassigned labels " + t.labelPending.keySet());
			}
		}
		List<Thread> threadList = new ArrayList<>();
		int o = 0;
		for(T t : threads.values()){
			if(t.event.isEmpty())
				continue;
			t.event.get(0).setOId(o++);
			Thread thread = new Thread(t.name, t.id, t.event.toArray(new Event[0]));
			int end = t.event.size() - 1;
			for(int i = 0; i < end; i++) {
				Event next = t.event.get(i + 1);
				next.setOId(o++);
			}
			threadList.add(thread);
		}
		List<Init> initList = iValueMap.entrySet().stream().map(e->new Init(e.getKey(), e.getValue())).collect(Collectors.toList());
		return new Program(memory, ImmutableSet.copyOf(locations.values()), threadList, initList, ass, assFilter);
	}

	public T thread(String name, int id) {
		return threads.computeIfAbsent(id, k->new T(id, name));
	}

	public T thread(int id) {
		return thread(null, id);
	}

	public T threadOrError(int id) {
		T t = threads.get(id);
		if(null == t) {
			throw new RuntimeException("Thread " + id + " is not initialised");
		}
		return t;
	}

	public void setAssert(AbstractAssert ass) {
		this.ass = ass;
	}

	public void setAssertFilter(AbstractAssert ass) {
		this.assFilter = ass;
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Declarators

	public void initLocEqLocPtr(String leftName, String rightName, int precision) {
		Location location = getOrCreateLocation(leftName, precision);
		iValueMap.put(location.getAddress(), getOrCreateLocation(rightName, precision).getAddress());
	}

	public void initLocEqLocVal(String leftName, String rightName, int precision) {
		Location left = getOrCreateLocation(leftName, precision);
		Location right = getOrCreateLocation(rightName, precision);
		iValueMap.put(left.getAddress(), iValueMap.get(right.getAddress()));
	}

	public void initLocEqConst(String locName, IConst iValue) {
		Location location = getOrCreateLocation(locName, iValue.getPrecision());
		iValueMap.put(location.getAddress(), iValue);
	}

	public void initRegEqLocPtr(int regThread, String regName, String locName, int precision) {
		Location loc = getOrCreateLocation(locName, precision);
		T t = thread(regThread);
		t.local(t.register(regName, precision), loc.getAddress());
	}

	public void initRegEqLocVal(int regThread, String regName, String locName, int precision) {
		Location loc = getOrCreateLocation(locName, precision);
		T t = thread(regThread);
		t.local(t.register(regName, precision), iValueMap.get(loc.getAddress()));
	}

	public void initRegEqConst(int regThread, String regName, IConst iValue) {
		T t = thread(regThread);
		t.local(t.register(regName, iValue.getPrecision()), iValue);
	}

	public void addDeclarationArray(String name, List<IConst> values, int precision) {
		int size = values.size();
		List<Address> addresses = memory.malloc(name, size, precision);
		for(int i = 0; i < size; i++) {
			String varName = name + "[" + i + "]";
			Address address = addresses.get(i);
			locations.put(varName, new Location(varName, address));
			iValueMap.put(address, values.get(i));
		}
		pointers.put(name, addresses.get(0));
	}

	public void addDeclarationArray(String name, List<IConst> values) {
		addDeclarationArray(name, values, -1);
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Utility

	public Location getLocation(String name){
		return locations.get(name);
	}

	public Location getOrCreateLocation(String name, int precision) {
		if(!locations.containsKey(name)) {
			Location location = memory.getOrCreateLocation(name, precision);
			locations.put(name, location);
			iValueMap.put(location.getAddress(), new IConst(Location.DEFAULT_INIT_VALUE, location.getAddress().getPrecision()));
		}
		return locations.get(name);
	}

	public Location getOrErrorLocation(String name) {
		if(locations.containsKey(name)) {
			return locations.get(name);
		}
		throw new ParsingException("Location " + name + " has not been initialised");
	}

	public IConst getInitValue(Address address) {
		return iValueMap.getOrDefault(address, new IConst(Location.DEFAULT_INIT_VALUE, address.getPrecision()));
	}

	public Address getPointer(String name) {
		return pointers.get(name);
	}

	/**
	 * Builder for threads.
	 */
	public static final class T {

		private final int id;

		private final String name;

		private final ArrayList<Event> event = new ArrayList<>();

		private final HashMap<Object,Register> register = new HashMap<>();

		private final HashMap<String,Label> label = new HashMap<>();

		private final HashMap<String,Label> labelPending = new HashMap<>();

		private T(int id, String name) {
			this.id = id;
			this.name = name;
		}

		/**
		 * Appends an event to the current end of this thread's sequence.
		 * @param event
		 * Element to be inserted.
		 */
		public void add(Event event) {
			this.event.add(event);
		}

		public Local local(Register register, ExprInterface value) {
			Local result = new Local(register, value);
			event.add(result);
			return result;
		}

		public Load load(Register register, IExpr address, String... tag) {
			Load result = new Load(register, address, tag);
			event.add(result);
			return result;
		}

		public Store store(IExpr address, ExprInterface value, String... tag) {
			Store result = new Store(address, value, tag);
			event.add(result);
			return result;
		}

		public Fence fence(String... tag) {
			Fence result = new Fence(tag);
			event.add(result);
			return result;
		}

		public CondJump jump(Label label, BExpr condition) {
			CondJump result = new CondJump(condition, label);
			event.add(result);
			return result;
		}

		public RMWStore store(Load load, ExprInterface value, String... tag) {
			RMWStore result = new RMWStore(load, load.getAddress(), value, tag);
			event.add(result);
			return result;
		}

		/**
		 * If a label has already been inserted.
		 * @param name
		 * Identifies the label.
		 */
		public boolean hasLabel(String name) {
			return label.containsKey(name);
		}

		/**
		 * Labels the next event appended to this thread.
		 * @param name
		 * Identifies the label.
		 * @throws IllegalArgumentException
		 * A label with that name already exists in the current scope.
		 */
		public void addLabel(String name) {
			//TODO implement scopes
			if(label.containsKey(name)) {
				throw new IllegalArgumentException();
			}
			Label l = labelPending.remove(name);
			if(null == l) {
				l = new Label(name);
			}
			label.put(name, l);
			event.add(l);
		}

		/**
		 * Looks up a label by name.
		 * Creates a new instance marked as pending if not already exists.
		 * @param name
		 * Identifier for the label.
		 */
		public Label label(String name) {
			if(null == name)
				return new Label(null);
			Label l = label.get(name);
			return null != l ? l : labelPending.computeIfAbsent(name, Label::new);
		}

		/**
		 * Looks up a register by name.
		 * @param name
		 * Identifies the register in this thread.
		 * @return
		 * Named register or {@code null} if no such exists.
		 */
		public Register register(String name) {
			return register.get(name);
		}

		/**
		 * Looks up a register by name.
		 * Creates a new one if not already exists.
		 * @param name
		 * Identifies the register in this thread.
		 * @param precision
		 * Count of bits for storing values.
		 * Negative for unbounded domain.
		 * @return
		 * Named register.
		 */
		public Register register(String name, int precision) {
			return register.computeIfAbsent(name, k->new Register(name, id, precision));
		}

		/**
		 * Looks up a register by name.
		 * @param name
		 * Identifies the register in this thread.
		 * @return
		 * Named register.
		 * @throws ParsingException
		 * Currently no register with that name exists.
		 */
		public Register registerOrError(String name) {
			Register r = register.get(name);
			if(null == r) {
				throw new ParsingException("Register " + id + ":" + name + " is not initialised");
			}
			return r;
		}
	}
}
