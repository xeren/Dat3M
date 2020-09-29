package com.dat3m.dartagnan.program.memory;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.program.memory.utils.IllegalMemoryAccessException;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.IntExpr;
import java.util.*;
import static java.util.stream.Collectors.toUnmodifiableSet;

public class Memory {

	private Map<Address,Location> map;
	private Map<String,Location> locationIndex;
	private Map<String,List<Address>> arrays;

	private int nextIndex = 0;

	public Memory() {
		map = new HashMap<>();
		locationIndex = new HashMap<>();
		arrays = new HashMap<>();
	}

	public Location getLocationForAddress(Address address) {
		return map.get(address);
	}

	/**
	 * All element addresses in an array are direct successors.
	 * An address might be known at compile time.
	 * Allocation never reuses addresses.
	 * @param context
	 * Builder of expressions.
	 * @return
	 * Proposition that memory allocations follow the above specifications.
	 */
	public BoolExpr encode(EncodeContext context) {
		Context ctx = context.context;
		List<BoolExpr> enc = new LinkedList<>();
		for(List<Address> array: arrays.values()) {
			int size = array.size();
			IntExpr e1 = array.get(0).toZ3Int(context);
			for(int i = 1; i < size; i++) {
				IntExpr e2 = array.get(i).toZ3Int(context);
				enc.add(context.eq(e2, ctx.mkAdd(e1, context.one())));
				e1 = e2;
			}
		}
		return context.and(context.and(enc),
			context.and(map.keySet().stream().filter(Address::hasConstValue).map(a->context.eq(a.toZ3Int(context), ctx.mkInt(a.getConstValue())))),
			ctx.mkDistinct(getAllAddresses().stream().map(a->a.toZ3Int(context)).toArray(IntExpr[]::new)));
	}

	public List<Address> malloc(String name, int size) {
		if(!arrays.containsKey(name) && size > 0) {
			List<Address> addresses = new ArrayList<>();
			for(int i = 0; i < size; i++) {
				addresses.add(new Address(nextIndex++));
			}
			arrays.put(name, addresses);
			return addresses;
		}
		throw new IllegalMemoryAccessException("Illegal malloc for " + name);
	}

	public Location getOrCreateLocation(String name) {
		if(!locationIndex.containsKey(name)) {
			Location location = new Location(name, new Address(nextIndex++));
			map.put(location.getAddress(), location);
			locationIndex.put(name, location);
			return location;
		}
		return locationIndex.get(name);
	}

	public Set<Address> getAllAddresses() {
		return arrays.values().stream().flatMap(Collection::stream).collect(toUnmodifiableSet());
	}
}