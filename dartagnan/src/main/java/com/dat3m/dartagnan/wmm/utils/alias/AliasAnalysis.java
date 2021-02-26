package com.dat3m.dartagnan.wmm.utils.alias;

import com.dat3m.dartagnan.expression.ExprInterface;
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
import com.dat3m.dartagnan.program.memory.Location;

import java.util.*;

/**
 *
 * @author flo
 */
public class AliasAnalysis {

    private final List<Object> variables = new LinkedList<>();
    private final ImmutableSet<Address> maxAddressSet;
    private final Map<Register,Map<Event,Integer>> ssaMap;
    private final Map<Object,Set<Object>> edges = new HashMap<>();
    private final Map<Object,Set<Address>> addresses = new HashMap<>();
    private final Map<Register,Set<MemEvent>> events = new HashMap<>();
    private final Map<Register,Map<Integer,SSAReg>> ssa = new HashMap<>();

    public AliasAnalysis(Program program, Alias alias) {
        if(alias == Alias.NONE){
            maxAddressSet = null;
            ssaMap = null;
            calculateLocationSetsNoAlias(program);
        } else if (alias == Alias.CFS){
            maxAddressSet = program.getMemory().getAllAddresses();
            ssaMap = getRegSsaMap(program);
            cfsProcessLocs(program);
            cfsProcessRegs(program);
            cfsAlgorithm(program);
            processResults(program);
        } else {
            maxAddressSet = program.getMemory().getAllAddresses();
            ssaMap = null;
            processLocs(program);
            processRegs(program);
            algorithm(program);
            processResults(program);
        }
    }

    private void processLocs(Program program) {
        for (Event ev : program.getCache().getEvents(FilterBasic.get(EType.MEMORY))) {
            MemEvent e = (MemEvent) ev;
            IExpr address = e.getAddress();

            // Collect for each v events of form: p = *v, *v = q
            if (address instanceof Register) {
                events.computeIfAbsent((Register)address,k->new HashSet<>()).add(e);

            } else if (address instanceof Address) {
                // Rule register = &loc -> lo(register) = {loc}
                if (e instanceof RegWriter) {
                    Register register = ((RegWriter) e).getResultRegister();
                    address(register).add((Address)address);
                    variables.add(register);

                } else if (e instanceof Init) {
                    // Rule loc = &loc2 -> lo(loc) = {loc2} (only possible in init events)
                    Location loc = program.getMemory().getLocationForAddress((Address) address);
                    IExpr value = ((Init) e).getValue();
                    if (loc != null && value instanceof Address) {
                        address(loc).add((Address)value);
                        variables.add(loc);
                    }
                }

            } else {
                // r = *(CompExpr) -> loc(r) = max
                if (e instanceof RegWriter) {
                    Register register = ((RegWriter) e).getResultRegister();
                    address(register).addAll(maxAddressSet);
                    variables.add(register);
                }
                // We allow for more address calculations
                e.setMaxAddressSet(maxAddressSet);
            }
        }
    }

    private void cfsProcessLocs(Program program) {
        for (Event ev : program.getCache().getEvents(FilterBasic.get(EType.MEMORY))) {
            MemEvent e = (MemEvent) ev;
            IExpr address = e.getAddress();

            // Collect for each v events of form: p = *v, *v = q
            if (address instanceof Register) {
                Register register = (Register) address;
                SSAReg ssaReg = getSSAReg(register, ssaMap.get(register).get(e));
                ssaReg.getEventsWithAddr().add(e);

            } else if (address instanceof Address) {
                // Rule register = &loc -> lo(register)={loc}
                if (e instanceof RegWriter) {
                    Register register = ((RegWriter) e).getResultRegister();
                    SSAReg ssaReg = getSSAReg(register, ssaMap.get(register).get(e));
                    address(ssaReg).add((Address)address);
                    variables.add(ssaReg);

                } else if (e instanceof Init) {
                    // Rule loc=&loc2 -> lo(loc)={loc2} (only possible in init events)
                    Location loc = program.getMemory().getLocationForAddress((Address) address);
                    IExpr value = ((Init) e).getValue();
                    if (loc != null && value instanceof Address) {
                        address(loc).add((Address)value);
                        variables.add(loc);
                    }
                }
            } else {
                if (e instanceof RegWriter) {
                    Register register = ((RegWriter) e).getResultRegister();
                    SSAReg ssaReg = getSSAReg(register, ssaMap.get(register).get(e));
                    address(ssaReg).addAll(maxAddressSet);
                    variables.add(ssaReg);
                }

                // We allow for more address calculations
                e.setMaxAddressSet(maxAddressSet);
            }
        }
    }

    private void processRegs(Program program) {
        for (Event ev : program.getCache().getEvents(FilterBasic.get(EType.LOCAL))) {
            if(ev instanceof Local) {
                Local e = (Local) ev;
                Register register = e.getResultRegister();
                ExprInterface expr = e.getExpr();

                if (expr instanceof Register) {
                    // r1 = r2 -> add edge r2 --> r1
                    addEdge(expr,register);

                } else if (expr instanceof Address) {
                    // r = &a
                    address(register).add((Address)expr);
                    variables.add(register);
                }
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
                    address(ssaReg1).add((Address)expr);
                    variables.add(ssaReg1);
                }
            }
        }
    }

    private void algorithm(Program program) {
        while (!variables.isEmpty()) {
            Object variable = variables.remove(0);
            if(variable instanceof Register){
                // Process rules with *variable:
                for (Address address : addresses.getOrDefault(variable,ImmutableSet.of())) {
                    Location location = program.getMemory().getLocationForAddress(address);
                    if (location != null) {
                        for (MemEvent e : events.getOrDefault(variable,ImmutableSet.of())) {
                            // p = *variable:
                            if (e instanceof RegWriter) {
                                // Add edge from location to p
                                if(addEdge(location,((RegWriter) e).getResultRegister())) {
                                    // Add location to variables if edge is new.
                                    variables.add(location);
                                }
                            } else if (e instanceof Store) {
                                // *variable = register
                                ExprInterface value = e.getMemValue();
                                if (value instanceof Register) {
                                    Register register = (Register) value;
                                    // Add edge from register to location
                                    if(addEdge(register,location)) {
                                        // Add register to variables if edge is new.
                                        variables.add(register);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Process edges
            for (Object q : edges.getOrDefault(variable,ImmutableSet.of())) {
                if (address(q).addAll(addresses.getOrDefault(variable,ImmutableSet.of()))) {
                    variables.add(q);
                }
            }
        }
    }

    private void cfsAlgorithm(Program program) {
        while (!variables.isEmpty()) {
            Object variable = variables.remove(0);
            // Process rules with *variable:
            for (Address address : addresses.getOrDefault(variable,ImmutableSet.of())) {
                Location location = program.getMemory().getLocationForAddress(address);
                if (location != null && variable instanceof SSAReg) {
                    for (MemEvent e : ((SSAReg) variable).getEventsWithAddr()) {
                        // p = *variable:
                        if (e instanceof RegWriter) {
                            // Add edge from location to p
                            Register reg = ((RegWriter) e).getResultRegister();
                            SSAReg ssaReg = getSSAReg(reg, ssaMap.get(reg).get(e) + 1);
                            if(addEdge(location,ssaReg)) {
                                //add a to W if edge is new.
                                variables.add(location);
                            }
                        } else if (e instanceof Store) {
                            // *variable = register
                            ExprInterface value = e.getMemValue();
                            if (value instanceof Register) {
                                Register register = (Register) value;
                                SSAReg ssaReg = getSSAReg(register, ssaMap.get(register).get(e));
                                // Add edge from register to location
                                if(addEdge(ssaReg,location)) {
                                    // Add register to variables if edge is new.
                                    variables.add(ssaReg);
                                }
                            }
                        }
                    }
                }
            }
            // Process edges
            for (Object q : edges.getOrDefault(variable,ImmutableSet.of())) {
                if (address(q).addAll(addresses.getOrDefault(variable,ImmutableSet.of()))) {
                    variables.add(q);
                }
            }
        }
    }

    private void processResults(Program program) {
    	// Used to have pointer analysis when having arrays and structures
    	Map<Register, Address> bases = new HashMap<>();
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
    		} else if(exp instanceof IExprBin) {
    			IExpr base = exp.getBase();
    			if(base instanceof Address) {
    				bases.put(reg, (Address)base);	
    			} else if(base instanceof Register && bases.containsKey(base)) {
    				bases.put(reg, bases.get(base));
    			}
    		}
    	}

        for (Event e : program.getCache().getEvents(FilterBasic.get(EType.MEMORY))) {
            IExpr address = ((MemEvent) e).getAddress();
            Set<Address> adresses;
            if (address instanceof Register) {
            	if(bases.containsKey(address)) {
            		adresses = ImmutableSet.of(bases.get(address));
            	} else {
                    adresses = addresses.getOrDefault(address,ImmutableSet.of());
            	}
            } else if (address instanceof Address) {
                    adresses = ImmutableSet.of(((Address) address));
            } else {
                adresses = maxAddressSet;
            }
            if (adresses.size() == 0) {
                adresses = maxAddressSet;
            }
            ImmutableSet<Address> addr = ImmutableSet.copyOf(adresses);
            ((MemEvent) e).setMaxAddressSet(addr);
        }
    }

    private void calculateLocationSetsNoAlias(Program program) {
        ImmutableSet<Address> maxAddressSet = program.getMemory().getAllAddresses();
        for (Event e : program.getCache().getEvents(FilterBasic.get(EType.MEMORY))) {
            IExpr address = ((MemEvent) e).getAddress();
            if (address instanceof Address) {
                ((MemEvent) e).setMaxAddressSet(ImmutableSet.of((Address) address));
            } else {
                ((MemEvent) e).setMaxAddressSet(maxAddressSet);
            }
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

    private boolean addEdge(Object x, Object y) {
        return edges.computeIfAbsent(x,k->new HashSet<>()).add(y);
    }

    private Set<Address> address(Object x) {
        return addresses.computeIfAbsent(x,k->new HashSet<>());
    }

    SSAReg getSSAReg(Register r, int i){
        ssa.putIfAbsent(r, new HashMap<>());
        ssa.get(r).putIfAbsent(i, new SSAReg(i, r));
        return ssa.get(r).get(i);
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
                        addEdge(getSSAReg(r, indexMap.get(r)), getSSAReg(r, indexMapClone.get(r)));
                    } else if(indexMap.get(r) > indexMapClone.get(r)){
                        addEdge(getSSAReg(r, indexMapClone.get(r)), getSSAReg(r, indexMap.get(r)));
                    }
                }
                i += t1Events.size() + t2Events.size();
            }
        }
    }
}
