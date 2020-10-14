package com.dat3m.dartagnan.wmm.filter;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.Event;
import com.dat3m.dartagnan.wmm.Clause;
import com.dat3m.dartagnan.wmm.ProgramCache;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class FilterIntersection extends FilterAbstract {

	private final static Map<String,FilterIntersection> instances = new HashMap<>();

	public static FilterIntersection get(FilterAbstract filter1, FilterAbstract filter2) {
		String key = mkName(filter1, filter2);
		instances.putIfAbsent(key, new FilterIntersection(filter1, filter2));
		return instances.get(key);
	}

	private static String mkName(FilterAbstract filter1, FilterAbstract filter2) {
		return (filter1 instanceof FilterBasic ? filter1.toString() : "( " + filter1.toString() + " )")
			+ " & " + (filter2 instanceof FilterBasic ? filter2.toString() : "( " + filter2.toString() + " )");
	}

	private final FilterAbstract filter1;
	private final FilterAbstract filter2;

	private FilterIntersection(FilterAbstract filter1, FilterAbstract filter2) {
		this.filter1 = filter1;
		this.filter2 = filter2;
	}

	@Override
	public boolean filter(Event e) {
		return filter1.filter(e) && filter2.filter(e);
	}

	@Override
	public void encodeFO(EncodeContext e, ProgramCache p) {
		if(e.add(this))
			return;
		super.encodeFO(e, p);
		filter1.encodeFO(e, p);
		filter2.encodeFO(e, p);
	}

	@Override
	protected Stream<Clause> termFO(int x) {
		Clause[] c1 = filter1.nameFO(x).toArray(Clause[]::new);
		return filter2.nameFO(x).flatMap(c2->Arrays.stream(c1).map(c2::combine));
	}

	@Override
	public String toString() {
		return mkName(filter1, filter2);
	}

	@Override
	public int hashCode() {
		return filter1.hashCode() & filter2.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;

		if(obj == null || getClass() != obj.getClass())
			return false;

		FilterIntersection fObj = (FilterIntersection) obj;
		return fObj.filter1.equals(filter1) && fObj.filter2.equals(filter2);
	}
}
