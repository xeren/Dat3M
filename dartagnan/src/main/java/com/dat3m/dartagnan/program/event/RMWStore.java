package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.program.utils.EType;

public class RMWStore extends Store {

	public final Load loadEvent;

	public RMWStore(Load loadEvent, IExpr address, ExprInterface value, String... mo) {
		super(address, value, mo);
		this.loadEvent = loadEvent;
		addFilters(EType.RMW);
		loadEvent.addFilters(EType.RMW);
	}

	public RMWStore(RMWStore other, Load copy) {
		super(other);
		this.loadEvent = copy;
	}
}
