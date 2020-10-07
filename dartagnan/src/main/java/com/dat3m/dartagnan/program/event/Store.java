package com.dat3m.dartagnan.program.event;

import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IExpr;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.utils.RegReaderData;
import com.dat3m.dartagnan.program.utils.EType;
import com.microsoft.z3.IntExpr;
import java.util.Set;

public class Store extends MemEvent implements RegReaderData {

	protected final ExprInterface value;
	private final Set<Register> dataRegs;

	public Store(IExpr address, ExprInterface value, String mo) {
		super(address, mo);
		this.value = value;
		dataRegs = Register.of(value);
		addFilters(EType.ANY, EType.VISIBLE, EType.MEMORY, EType.WRITE, EType.REG_READER);
	}

	protected Store(Store other) {
		super(other);
		this.value = other.value;
		dataRegs = other.dataRegs;
	}

	@Override
	public IntExpr getMemValueExpr(EncodeContext e) {
		return value.toZ3Int(this, e);
	}

	@Override
	public Set<Register> getDataRegs() {
		return dataRegs;
	}

	@Override
	public String toString() {
		return "store(*" + address + ", " + value + (mo != null ? ", " + mo : "") + ")";
	}

	@Override
	public String label() {
		return "W" + (mo != null ? "_" + mo : "");
	}

	@Override
	public ExprInterface getMemValue() {
		return value;
	}

	// Unrolling
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public Store getCopy() {
		return new Store(this);
	}
}
