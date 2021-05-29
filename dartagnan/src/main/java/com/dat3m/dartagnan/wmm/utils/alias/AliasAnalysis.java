package com.dat3m.dartagnan.wmm.utils.alias;

import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IConst;
import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.utils.RegReaderData;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.google.common.collect.ImmutableSet;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.expression.IExprBin;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.*;
import com.dat3m.dartagnan.program.event.utils.RegWriter;
import com.dat3m.dartagnan.program.memory.Address;

import java.util.*;

/**
 *
 * @author flo
 */
public class AliasAnalysis {

    private List<Object> variables = new LinkedList<>();
    private ImmutableSet<Address> maxAddressSet;
    private Map<Register, Map<Event, Integer>> ssaMap;

	private final Map<Object,Set<Object>> edges = new HashMap<>();
	private final Map<Object,Set<Address>> addresses = new HashMap<>();
	private final Map<Register,Map<Integer,SSAReg>> ssa = new HashMap<>();

    public void calculateLocationSets(Program program) {
            maxAddressSet = program.getMemory().getAllAddresses();
            ssaMap = getRegSsaMap(program);
            cfsProcessLocs(program);
            cfsProcessRegs(program);
            cfsAlgorithm(program);
            processResults(program);
    }

    private void cfsProcessLocs(Program program) {
        for (Event ev : program.getCache().getEvents(FilterBasic.get(EType.MEMORY))) {
            MemEvent e = (MemEvent) ev;
            IExpr address = e.getAddress();

            // Collect for each v events of form: p = *v, *v = q
            if (address instanceof Register) {
                Register register = (Register) address;
                getSSAReg(register, ssaMap.get(register).get(e)).getEventsWithAddr().add(e);

            } else if (address instanceof Address) {
                // Rule register = &loc -> lo(register)={loc}
                if (e instanceof RegWriter) {
                    Register register = ((RegWriter) e).getResultRegister();
                    SSAReg ssaReg = getSSAReg(register, ssaMap.get(register).get(e));
                    addAddress(ssaReg, (Address) address);
                    variables.add(ssaReg);

                } else if (e instanceof Init) {
                    // Rule loc=&loc2 -> lo(loc)={loc2} (only possible in init events)
                    IExpr value = ((Init) e).getValue();
					if(value instanceof Address) {
						addAddress(address,(Address)value);
						variables.add(address);
                    }
                }
            } else {
                if (e instanceof RegWriter) {
                    Register register = ((RegWriter) e).getResultRegister();
                    SSAReg ssaReg = getSSAReg(register, ssaMap.get(register).get(e));
                    addAllAddresses(ssaReg, maxAddressSet);
                    variables.add(ssaReg);
                }

                // We allow for more address calculations
                e.setMaxAddressSet(maxAddressSet);
            }
        }
    }

    private void cfsProcessRegs(Program program) {
        for (Event ev : program.getCache().getEvents(FilterBasic.get(EType.LOCAL))) {
            if(ev instanceof Local) {
                Local e = (Local) ev;
                Register register = e.getResultRegister();
                int id = ssaMap.get(register).get(e) + 1;
                SSAReg ssaReg1 = getSSAReg(register, id);
                ExprInterface expr = e.getExpr();

                if (expr instanceof Register) {
                    // r1 = r2 -> add edge r2 --> r1
                    Register register2 = (Register) expr;
                    SSAReg ssaReg2 = getSSAReg(register2, ssaMap.get(register2).get(e));
                    addEdge(ssaReg2, ssaReg1);

                } else if (expr instanceof Address) {
                    // r = &a
                    addAddress(ssaReg1, (Address) expr);
                    variables.add(ssaReg1);
                }
            }
        }
    }

    private void cfsAlgorithm(Program program) {
        while (!variables.isEmpty()) {
            Object variable = variables.remove(0);
            // Process rules with *variable:
            for (Address address : getAddresses(variable)) {
				if(!(variable instanceof SSAReg))
					continue;
                    for (MemEvent e : ((SSAReg) variable).getEventsWithAddr()) {
                        // p = *variable:
                        if (e instanceof RegWriter) {
                            // Add edge from location to p
                            Register reg = ((RegWriter) e).getResultRegister();
                            SSAReg ssaReg = getSSAReg(reg, ssaMap.get(reg).get(e) + 1);
						if(addEdge(address,ssaReg))
							//add a to W if edge is new.
							variables.add(address);
                        } else if (e instanceof Store) {
                            // *variable = register
                            ExprInterface value = e.getMemValue();
                            if (value instanceof Register) {
                                Register register = (Register) value;
                                SSAReg ssaReg = getSSAReg(register, ssaMap.get(register).get(e));
                                // Add edge from register to location
							if(addEdge(ssaReg,address))
								// Add register to variables if edge is new.
								variables.add(ssaReg);
                        }
                    }
                }
            }
            // Process edges
            for (Object q : getEdges(variable)) {
                if (addAllAddresses(q, getAddresses(variable))) {
                    variables.add(q);
                }
            }
        }
    }

    private void processResults(Program program) {
    	// Used to have pointer analysis when having arrays and structures
    	Map<Register, Address> bases = new HashMap<>();
    	Map<Register, Integer> offsets = new HashMap<>();
    	for (Event ev : program.getCache().getEvents(FilterBasic.get(EType.LOCAL))) {
    		// Not only Local events have EType.LOCAL tag
    		if(!(ev instanceof Local)) {
    			continue;
    		}
    		Local l = (Local)ev;
    		ExprInterface exp = l.getExpr();
    		Register reg = l.getResultRegister();
			if(exp instanceof Address) {
    			bases.put(reg, (Address)exp);
                offsets.put(reg, 0);
            } else if(exp instanceof IExprBin) {
    			IExpr base = exp.getBase();
    			if(base instanceof Address) {
    				bases.put(reg, (Address)base);
    				if(((IExprBin) exp).getRHS() instanceof IConst) {
        				offsets.put(reg, ((IConst)((IExprBin) exp).getRHS()).getIntValue().intValue());    					
    				}
    			} else if(base instanceof Register && bases.containsKey(base)) {
    				bases.put(reg, bases.get(base));
    				if(((IExprBin) exp).getRHS() instanceof IConst) {
        				offsets.put(reg, ((IConst)((IExprBin) exp).getRHS()).getIntValue().intValue());    					
    				}
    			}
    		}
    	}

        for (Event e : program.getCache().getEvents(FilterBasic.get(EType.MEMORY))) {
            IExpr address = ((MemEvent) e).getAddress();
            Set<Address> addresses;
            if (address instanceof Register) {
				var b = bases.get(address);
				if(null!=b) {
					var a = b.getLocation().getAddress();
					var o = offsets.get(address);
					addresses = null!=o ? ImmutableSet.of(a.get(o)) : new HashSet<>(a);
            	} else {
            	    addresses = maxAddressSet;
            	    //TODO: This line of code is buggy. It causes many WMM benchmarks to fail
                    //addresses = graph.getAddresses(((Register) address));
            	}
            } else if (address instanceof Address) {
                    addresses = ImmutableSet.of(((Address) address));
            } else {
                addresses = maxAddressSet;
            }
            if (addresses.size() == 0) {
                addresses = maxAddressSet;
            }
            ImmutableSet<Address> addr = ImmutableSet.copyOf(addresses);
            ((MemEvent) e).setMaxAddressSet(addr);
        }
    }

    private Map<Register, Map<Event, Integer>> getRegSsaMap(Program program){
        Map<Register, Map<Event, Integer>> ssaMap = new HashMap<>();
        Map<Register, Integer> indexMap = new HashMap<>();
        for(Thread thread : program.getThreads()){
            List<Event> events = thread.getCache().getEvents(FilterBasic.get(EType.ANY));
            mkSsaIndices(events, ssaMap, indexMap);
        }
        return ssaMap;
    }


    private void mkSsaIndices(
            List<Event> events,
            Map<Register, Map<Event, Integer>> ssaMap,
            Map<Register, Integer> indexMap
    ){
        for(int i = 0; i < events.size(); i++){
            Event e = events.get(i);

            if(e instanceof RegReaderData){
                for(Register register : ((RegReaderData)e).getDataRegs()){
                    ssaMap.putIfAbsent(register, new HashMap<>());
                    ssaMap.get(register).put(e, indexMap.getOrDefault(register, 0));
                }
            }

            if(e instanceof MemEvent){
                for(Register register : ((MemEvent)e).getAddress().getRegs()){
                    ssaMap.putIfAbsent(register, new HashMap<>());
                    ssaMap.get(register).put(e, indexMap.getOrDefault(register, 0));
                }
            }

            if(e instanceof RegWriter){
                Register register = ((RegWriter)e).getResultRegister();
                int index = indexMap.getOrDefault(register, 0);
                ssaMap.putIfAbsent(register, new HashMap<>());
                ssaMap.get(register).put(e, index);
                indexMap.put(register, ++index);
            }

            if(e instanceof If){
                Map<Register, Integer> indexMapClone = new HashMap<>(indexMap);
                List<Event> t1Events = ((If)e).getMainBranchEvents();
                List<Event> t2Events = ((If)e).getElseBranchEvents();
                mkSsaIndices(t1Events, ssaMap, indexMap);
                mkSsaIndices(t2Events, ssaMap, indexMapClone);

                for(Register r : indexMapClone.keySet()){
                    indexMap.put(r, Integer.max(indexMap.getOrDefault(r, 0), indexMapClone.get(r)));
                    if(indexMap.get(r) < indexMapClone.get(r)){
                        addEdge(getSSAReg(r,indexMap.get(r)), getSSAReg(r, indexMapClone.get(r)));
                    } else if(indexMap.get(r) > indexMapClone.get(r)){
                        addEdge(getSSAReg(r,indexMapClone.get(r)),getSSAReg(r, indexMap.get(r)));
                    }
                }
                i += t1Events.size() + t2Events.size();
            }
        }
    }

	private boolean addEdge(Object v1, Object v2){
		return edges.computeIfAbsent(v1,k->new HashSet<>()).add(v2);
	}

	private Set<Object> getEdges(Object v){
		return edges.getOrDefault(v, ImmutableSet.of());
	}

	private void addAddress(Object v, Address a){
		addresses.computeIfAbsent(v,k->new HashSet<>()).add(a);
	}

	private boolean addAllAddresses(Object v, Set<Address> s){
		return addresses.computeIfAbsent(v,k->new HashSet<>()).addAll(s);
	}

	private Set<Address> getAddresses(Object v){
		return addresses.getOrDefault(v, ImmutableSet.of());
	}

	private SSAReg getSSAReg(Register r, int i){
		return ssa.computeIfAbsent(r,k->new HashMap<>()).computeIfAbsent(i,k->new SSAReg(i,r));
	}
}
