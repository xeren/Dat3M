package com.dat3m.dartagnan.wmm.relation;

import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.utils.Settings;
import com.microsoft.z3.*;
import java.util.HashSet;
import java.util.LinkedList;

public class EncodeContext {

	public final Context context;
	public final Program program;
	public final Settings settings;
	public final Sort sortEvent;
	private final HashSet<Relation> done = new HashSet<>();
	private final LinkedList<BoolExpr> rule = new LinkedList<>();

	public EncodeContext(Context context, Program program, Settings settings) {
		this.context = context;
		this.program = program;
		this.settings = settings;
		sortEvent = context.mkIntSort();
	}

	public boolean add(Relation self) {
		return done.add(self);
	}

	public void rule(BoolExpr assertion) {
		rule.add(assertion);
	}

	public BoolExpr allRules() {
		return context.mkAnd(rule.toArray(new BoolExpr[0]));
	}
}
