package com.dat3m.dartagnan.program.memory;

import com.dat3m.dartagnan.program.memory.utils.IllegalMemoryAccessException;
import com.google.common.collect.ImmutableSet;

import static com.dat3m.dartagnan.program.utils.Utils.convertToIntegerFormula;
import static com.dat3m.dartagnan.program.utils.Utils.generalEqual;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import org.sosy_lab.java_smt.api.IntegerFormulaManager;
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula;
import org.sosy_lab.java_smt.api.SolverContext;

public class Memory {

	private final Map<Address,Location> map = new HashMap<>();
    private final Map<String, Location> locationIndex;
	private final Map<String,Array> arrays;

    private int nextIndex = 1;

    public Memory(){
        locationIndex = new HashMap<>();
        arrays = new HashMap<>();
    }

    public Location getLocationForAddress(Address address){
        return map.get(address);
    }

    public BooleanFormula encode(SolverContext ctx){
    	FormulaManager fmgr = ctx.getFormulaManager();
		BooleanFormulaManager bmgr = fmgr.getBooleanFormulaManager();
        IntegerFormulaManager imgr = fmgr.getIntegerFormulaManager();

		BooleanFormula enc = bmgr.makeTrue();
		for(var array : arrays.values()){
			Formula e1 = array.address.get(0).toIntFormula(ctx);
			for(int i = 1; i < array.address.size(); i++){
				IntegerFormula e2 = convertToIntegerFormula(array.address.get(i).toIntFormula(ctx), ctx);
				IntegerFormula newAddress = imgr.add(convertToIntegerFormula(e1, ctx), imgr.makeNumber(BigInteger.ONE));
				enc = bmgr.and(enc, generalEqual(e2, newAddress, ctx));
                e1 = e2;
            }
        }
        // Following SMACK, only address with constant values can have negative values.
        for(Address add : getAllAddresses()) {
        	if(!add.hasConstantValue()) {
        		enc = bmgr.and(enc, imgr.greaterThan(
        							convertToIntegerFormula(add.toIntFormula(ctx), ctx), 
        							imgr.makeNumber(BigInteger.ZERO)));
        	}
        }
        
        BooleanFormula distinct = getAllAddresses().size() > 1 ?
        		imgr.distinct(getAllAddresses().stream()
                		.map(a -> convertToIntegerFormula(a.toIntFormula(ctx), ctx))
                		.collect(Collectors.toList())) : 
                bmgr.makeTrue();
        
        return bmgr.and(enc, distinct);        	
    }

    // Assigns each Address a fixed memory address.
    public BooleanFormula fixedMemoryEncoding(SolverContext ctx) {
        FormulaManager fmgr = ctx.getFormulaManager();
		IntegerFormulaManager imgr = fmgr.getIntegerFormulaManager();

    	BooleanFormula[] addrExprs = getAllAddresses().stream().filter(x -> !x.hasConstantValue())
        		.map(add -> imgr.equal(convertToIntegerFormula(add.toIntFormula(ctx), ctx), 
        								imgr.makeNumber(add.getIntValue().intValue())))
        		.toArray(BooleanFormula[]::new);
        return fmgr.getBooleanFormulaManager().and(addrExprs);
    }

    public List<Address> malloc(String name, int size, int precision){
        if(!arrays.containsKey(name) && size > 0){
			var a = new Array(size, false);
			for(int i = 0; i < size; i++)
				a.address.add(new Address(a, nextIndex++, precision));
			arrays.put(name, a);
			return a.address;
        }
        throw new IllegalMemoryAccessException("Illegal malloc for " + name);
    }

    public Location getOrCreateLocation(String name, int precision){
        if(!locationIndex.containsKey(name)){
			var a = new Array(1, true);
			var location = new Location(name, new Address(a, nextIndex++, precision));
            map.put(location.getAddress(), location);
            locationIndex.put(name, location);
            return location;
        }
        return locationIndex.get(name);
    }

    public ImmutableSet<Address> getAllAddresses(){
        Set<Address> result = new HashSet<>(map.keySet());
		for(var array : arrays.values())
			result.addAll(array.address);
        return ImmutableSet.copyOf(result);
    }

    public boolean isArrayPointer(Address address) {
		return !address.array.isStatic;
    }
    
    public List<Address> getArrayfromPointer(Address address) {
		// This method shall be called after isArrayPointer to avoid returning null
		return address.array.address;
    }
}