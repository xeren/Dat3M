package com.dat3m.dartagnan.wmm;

import com.dat3m.dartagnan.program.event.*;

import java.util.HashMap;

public interface Filter {

	boolean filter(Event e);

	static Filter of(String id) {
		switch(id) {
			case "_": return Atom.any;
			case "T": return Atom.local;
			case "R": return Atom.read;
			case "W": return Atom.write;
			case "B": return Atom.branch;
			case "M": return Atom.memory;
			case "F": return Atom.fence;
			case "IW": return Atom.init;
			default:
		}
		return Tag.map.computeIfAbsent(id, Tag::new);
	}

	enum Atom implements Filter {
		any("_", Event.class),
		local("T", Local.class),
		read("R", Load.class),
		write("W", Store.class) {
			@Override
			public boolean filter(Event e) {
				return e instanceof Store || e instanceof Init;
			}
		},
		branch("B", CondJump.class),
		memory("M", MemEvent.class),
		fence("F", Fence.class),
		init("IW", Init.class);

		public final String id;
		public final Class<?> cls;

		Atom(String id, Class<?> cls) {
			this.id = id;
			this.cls = cls;
		}

		@Override
		public boolean filter(Event e) {
			return cls.isInstance(e);
		}

		@Override
		public String toString() {
			return id;
		}
	}

	final class Tag implements Filter {

		public final String tag;
		private static final HashMap<String,Tag> map = new HashMap<>();

		private Tag(String tag) {
			this.tag = tag;
		}

		@Override
		public boolean filter(Event e) {
			return e.is(tag);
		}

		@Override
		public String toString() {
			return "'" + tag;
		}
	}

	final class And implements Filter {

		public final Filter first;
		public final Filter second;
		private static final HashMap<Filter,HashMap<Filter,And>> map = new HashMap<>();

		public static And of(Filter first, Filter second) {
			return map.computeIfAbsent(first, k->new HashMap<>()).computeIfAbsent(second, k->new And(first,second));
		}

		private And(Filter first, Filter second) {
			this.first = first;
			this.second = second;
		}

		@Override
		public boolean filter(Event e) {
			return first.filter(e) && second.filter(e);
		}

		@Override
		public String toString() {
			return "(and " + first + " " + second + ")";
		}
	}

	final class Or implements Filter {

		public final Filter first;
		public final Filter second;
		private static final HashMap<Filter,HashMap<Filter,Or>> map = new HashMap<>();

		public static Or of(Filter first, Filter second) {
			return map.computeIfAbsent(first, k->new HashMap<>()).computeIfAbsent(second, k->new Or(first,second));
		}

		private Or(Filter first, Filter second) {
			this.first = first;
			this.second = second;
		}

		@Override
		public boolean filter(Event e) {
			return first.filter(e) || second.filter(e);
		}

		@Override
		public String toString() {
			return "(or " + first + " " + second + ")";
		}
	}

	final class Except implements Filter {

		public final Filter first;
		public final Filter second;
		private static final HashMap<Filter,HashMap<Filter,Except>> map = new HashMap<>();

		public static Except of(Filter first, Filter second) {
			return map.computeIfAbsent(first, k->new HashMap<>()).computeIfAbsent(second, k->new Except(first,second));
		}

		private Except(Filter first, Filter second) {
			this.first = first;
			this.second = second;
		}

		@Override
		public boolean filter(Event e) {
			return first.filter(e) && !second.filter(e);
		}

		@Override
		public String toString() {
			return "(minus " + first + " " + second + ")";
		}
	}
}