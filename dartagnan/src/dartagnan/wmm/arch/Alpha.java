package dartagnan.wmm.arch;

import static dartagnan.wmm.Encodings.satAcyclic;
import static dartagnan.wmm.Encodings.satCycle;
import static dartagnan.wmm.Encodings.satCycleDef;

import java.util.Set;
import java.util.stream.Collectors;

import com.microsoft.z3.*;

import dartagnan.program.*;
import dartagnan.program.event.Event;
import dartagnan.program.event.Local;
import dartagnan.program.event.MemEvent;
import dartagnan.wmm.Domain;
import dartagnan.wmm.EncodingsCAT;
import dartagnan.wmm.WmmInterface;

public class Alpha implements WmmInterface {

	public final String[] fences = {"mfence", "isync"};
	
	public BoolExpr encode(Program program, Context ctx, boolean approx, boolean idl) throws Z3Exception {
		if(program.hasRMWEvents()){
			throw new RuntimeException("RMW is not implemented for Alpha");
		}

		Set<Event> events = program.getEvents().stream().filter(e -> e instanceof MemEvent).collect(Collectors.toSet());
		Set<Event> eventsL = program.getEvents().stream().filter(e -> e instanceof MemEvent || e instanceof Local).collect(Collectors.toSet());

		BoolExpr enc = Domain.encodeFences(program, ctx, fences);
		enc = ctx.mkAnd(enc, EncodingsCAT.satUnion("co", "fr", events, ctx));
	    enc = ctx.mkAnd(enc, EncodingsCAT.satUnion("com", "(co+fr)", "rf", events, ctx));
	    enc = ctx.mkAnd(enc, EncodingsCAT.satUnion("poloc", "com", events, ctx));
	    enc = ctx.mkAnd(enc, EncodingsCAT.satUnion("com-alpha", "(co+fr)", "rfe", events, ctx));
	    enc = ctx.mkAnd(enc, EncodingsCAT.satTransFixPoint("idd", eventsL, approx, ctx));
	    enc = ctx.mkAnd(enc, EncodingsCAT.satIntersection("data", "idd^+", "RW", eventsL, ctx));
	    enc = ctx.mkAnd(enc, EncodingsCAT.satIntersection("poloc", "WR", events, ctx));
	    enc = ctx.mkAnd(enc, EncodingsCAT.satUnion("data", "(poloc&WR)", events, ctx));
	    enc = ctx.mkAnd(enc, EncodingsCAT.satTransFixPoint("(data+(poloc&WR))", events, approx, ctx));
	    enc = ctx.mkAnd(enc, EncodingsCAT.satIntersection("(data+(poloc&WR))^+", "RM", events, ctx));
	    enc = ctx.mkAnd(enc, EncodingsCAT.satIntersection("ctrl", "RW", events, ctx));
	    enc = ctx.mkAnd(enc, EncodingsCAT.satUnion("(ctrl&RW)", "ctrlisync", events, ctx));
	    enc = ctx.mkAnd(enc, EncodingsCAT.satUnion("dp-alpha", "((ctrl&RW)+ctrlisync)", "((data+(poloc&WR))^+&RM)", events, ctx));
	    enc = ctx.mkAnd(enc, EncodingsCAT.satUnion("dp-alpha", "rf", events, ctx));
	    enc = ctx.mkAnd(enc, EncodingsCAT.satUnion("WW", "RM", events, ctx));
	    enc = ctx.mkAnd(enc, EncodingsCAT.satIntersection("(WW+RM)", "loc", events, ctx));
	    enc = ctx.mkAnd(enc, EncodingsCAT.satIntersection("po", "((WW+RM)&loc)", events, ctx));
	    enc = ctx.mkAnd(enc, EncodingsCAT.satUnion("po-alpha", "(po&((WW+RM)&loc))", "mfence", events, ctx));
	    enc = ctx.mkAnd(enc, EncodingsCAT.satUnion("ghb-alpha", "po-alpha", "com-alpha", events, ctx));
	    return enc;
	}

	public BoolExpr Consistent(Program program, Context ctx) throws Z3Exception {
		Set<Event> events = program.getEvents().stream().filter(e -> e instanceof MemEvent).collect(Collectors.toSet());
		return ctx.mkAnd(satAcyclic("(poloc+com)", events, ctx), satAcyclic("(dp-alpha+rf)", events, ctx), satAcyclic("ghb-alpha", events, ctx));
	}
	
	public BoolExpr Inconsistent(Program program, Context ctx) throws Z3Exception {
		Set<Event> events = program.getEvents().stream().filter(e -> e instanceof MemEvent).collect(Collectors.toSet());
		BoolExpr enc = ctx.mkAnd(satCycleDef("(poloc+com)", events, ctx), satCycleDef("(dp-alpha+rf)", events, ctx), satCycleDef("ghb-alpha", events, ctx));
		enc = ctx.mkAnd(enc, ctx.mkOr(satCycle("(poloc+com)", events, ctx), satCycle("(dp-alpha+rf)", events, ctx), satCycle("ghb-alpha", events, ctx)));
		return enc;
	}

	
}