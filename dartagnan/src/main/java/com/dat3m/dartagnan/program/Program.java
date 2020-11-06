package com.dat3m.dartagnan.program;

import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.program.utils.ThreadCache;
import com.dat3m.dartagnan.wmm.Computation;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.dat3m.dartagnan.wmm.utils.Utils;
import com.google.common.collect.ImmutableSet;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Model;
import com.dat3m.dartagnan.asserts.AbstractAssert;
import com.dat3m.dartagnan.asserts.AssertCompositeOr;
import com.dat3m.dartagnan.asserts.AssertInline;
import com.dat3m.dartagnan.asserts.AssertTrue;
import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.INonDet;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.Local;
import com.dat3m.dartagnan.program.event.utils.RegWriter;
import com.dat3m.dartagnan.program.memory.Location;
import com.dat3m.dartagnan.program.memory.Memory;

import static com.dat3m.dartagnan.wmm.utils.Utils.edge;

import java.util.*;
import java.util.stream.Collectors;

public class Program {

    private String name;
	private AbstractAssert ass;
    private AbstractAssert assFilter;
	private List<Thread> threads;
	private final ImmutableSet<Location> locations;
	private Memory memory;
	private Arch arch;
    private ThreadCache cache;
    private boolean isUnrolled;
    private boolean isCompiled;

    public Program(Memory memory, ImmutableSet<Location> locations){
        this("", memory, locations);
    }

	public Program (String name, Memory memory, ImmutableSet<Location> locations) {
		this.name = name;
		this.memory = memory;
		this.locations = locations;
		this.threads = new ArrayList<>();
	}

	public boolean isCompiled(){
        return isCompiled;
    }

    public boolean isUnrolled(){
        return isUnrolled;
    }

	public String getName(){
        return name;
    }

	public void setName(String name){
	    this.name = name;
    }

	public void setArch(Arch arch){
	    this.arch = arch;
    }

	public Arch getArch(){
	    return arch;
    }

    public Memory getMemory(){
        return this.memory;
    }

    public AbstractAssert getAss() {
        return ass;
    }

    public void setAss(AbstractAssert ass) {
        this.ass = ass;
    }

    public AbstractAssert getAssFilter() {
        return assFilter;
    }

    public void setAssFilter(AbstractAssert ass) {
        this.assFilter = ass;
    }

    public void add(Thread t) {
		threads.add(t);
	}

    public ThreadCache getCache(){
        if(cache == null){
            cache = new ThreadCache(getEvents());
        }
        return cache;
    }

    public void clearCache(){
    	for(Thread t : threads){
    		t.clearCache();
    	}
    }

    public List<Thread> getThreads() {
        return threads;
    }

    public ImmutableSet<Location> getLocations(){
        return locations;
    }

	public List<Event> getEvents(){
        List<Event> events = new ArrayList<>();
		for(Thread t : threads){
			events.addAll(t.getCache().getEvents(FilterBasic.get(EType.ANY)));
		}
		return events;
	}

	public AbstractAssert createAssertion() {
		AbstractAssert ass = new AssertTrue();
		List<Event> assertions = new ArrayList<>();
		for(Thread t : threads){
			assertions.addAll(t.getCache().getEvents(FilterBasic.get(EType.ASSERTION)));
		}
    	if(!assertions.isEmpty()) {
    		ass = new AssertInline((Local)assertions.get(0));
    		for(int i = 1; i < assertions.size(); i++) {
    			ass = new AssertCompositeOr(ass, new AssertInline((Local)assertions.get(i)));
    		}
    	}
		return ass;
	}

    // Unrolling
    // -----------------------------------------------------------------------------------------------------------------

    public int unroll(int bound, int nextId) {
        for(Thread thread : threads){
            nextId = thread.unroll(bound, nextId);
        }
        isUnrolled = true;
        cache = null;
        return nextId;
    }


    // Compilation
    // -----------------------------------------------------------------------------------------------------------------

    public int compile(Arch target, int nextId) {
        for(Thread thread : threads){
            nextId = thread.compile(target, nextId);
        }
        isCompiled = true;
        cache = null;
        return nextId;
    }


    // Encoding
    // -----------------------------------------------------------------------------------------------------------------

    public BoolExpr encodeCF(Context ctx) {
        for(Event e : getEvents()){
            e.initialise(ctx);
        }
        BoolExpr enc = memory.encode(ctx);
        for(Thread t : threads){
            enc = ctx.mkAnd(enc, t.encodeCF(ctx));
        }
        return enc;
    }

    public BoolExpr encodeFinalRegisterValues(Context ctx){
        Map<Register, List<Event>> eMap = new HashMap<>();
        for(Event e : getCache().getEvents(FilterBasic.get(EType.REG_WRITER))){
            Register reg = ((RegWriter)e).getResultRegister();
            eMap.putIfAbsent(reg, new ArrayList<>());
            eMap.get(reg).add(e);
        }

        BoolExpr enc = ctx.mkTrue();
        for (Register reg : eMap.keySet()) {
            List<Event> events = eMap.get(reg);
            events.sort(Collections.reverseOrder());
            for(int i = 0; i <  events.size(); i++){
                BoolExpr lastModReg = eMap.get(reg).get(i).exec();
                for(int j = 0; j < i; j++){
                    lastModReg = ctx.mkAnd(lastModReg, ctx.mkNot(events.get(j).exec()));
                }
                enc = ctx.mkAnd(enc, ctx.mkImplies(lastModReg,
                        ctx.mkEq(reg.getLastValueExpr(ctx), ((RegWriter)events.get(i)).getResultRegisterExpr())));
            }
        }
        return enc;
    }
    
    public BoolExpr encodeNoBoundEventExec(Context ctx){
    	BoolExpr enc = ctx.mkTrue();
        for(Event e : getCache().getEvents(FilterBasic.get(EType.BOUND))){
        	enc = ctx.mkAnd(enc, ctx.mkNot(e.exec()));
        }
        return enc;
    }
    
    public BoolExpr encodeUINonDet(Context ctx) {
    	BoolExpr enc = ctx.mkTrue();
        for(Event e : getCache().getEvents(FilterBasic.get(EType.LOCAL))){
        	if(!(e instanceof Local)) {
        		continue;
        	}
        	ExprInterface expr = ((Local)e).getExpr();
			if(expr instanceof INonDet) {
	        	enc = ctx.mkAnd(enc, ctx.mkGe(((INonDet)expr).toZ3Int(e, ctx), ctx.mkInt(((INonDet)expr).getMin())));
	        	enc = ctx.mkAnd(enc, ctx.mkLe(((INonDet)expr).toZ3Int(e, ctx), ctx.mkInt(((INonDet)expr).getMax())));
			}
        }
        return enc;  	
    }

    public BoolExpr getRf(Context ctx, Model m) {
    	BoolExpr enc = ctx.mkTrue();
        for(Event r : getCache().getEvents(FilterBasic.get(EType.READ))){
            for(Event w : getCache().getEvents(FilterBasic.get(EType.WRITE))){
        		if(m.getConstInterp(edge("rf", w, r, ctx)) != null && m.getConstInterp(edge("rf", w, r, ctx)).isTrue()) {
        			enc = ctx.mkAnd(enc, edge("rf", w, r, ctx));
        		}
        	}
        }
        return enc;  	
    }

    public Computation extract(Context context, Model model) {
		List<Event> writes = getCache().getEvents(FilterBasic.get(EType.WRITE));
		Computation result = new Computation(getCache().getEvents(FilterBasic.get(EType.READ)).stream()
			.collect(Collectors.toMap(Event::getCId, r->writes.stream()
				.filter(w->Optional.ofNullable(model.getConstInterp(edge("rf", w, r, context))).filter(Expr::isTrue).isPresent())
				.findAny().orElseThrow(()->new IllegalStateException("unsatisfied read in model")).getCId())));
		for(Thread t : threads) {
			Computation.Thread thread = result.new Thread();
			t.getCache().getEvents(FilterBasic.get(EType.ANY)).stream()
				.filter(e->model.getConstInterp(e.exec()).isTrue())
				.sorted((a,b)->a.equals(b) ? 0 : Optional.ofNullable(model.getConstInterp(Utils.edge("po", a, b, context))).filter(Expr::isTrue).isPresent() ? 1 : -1)
				.forEach(e->e.extract(model, thread));
		}
    	return result;
	}
}