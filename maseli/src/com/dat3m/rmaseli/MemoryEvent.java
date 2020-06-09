package com.dat3m.rmaseli;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.IntExpr;
import java.util.function.Supplier;

/**
 * Events communicated between threads in executions of a program.
 * This includes read events as well as write events.
 */
public interface MemoryEvent
{

	/**
	 * Constant set iff the branch issuing this event is executed.
	 */
	BoolExpr branch();

	/**
	 * Constant bound to the address targeted in this event.
	 */
	IntExpr key();

	/**
	 * Constant bound to the value copied in this event.
	 */
	IntExpr value();

	static MemoryEvent of(
		Supplier<BoolExpr> branch,
		Supplier<IntExpr> key,
		Supplier<IntExpr> value)
	{
		return new MemoryEvent()
		{
			@Override public BoolExpr branch()
			{
				return branch.get();
			}
			@Override public IntExpr key()
			{
				return key.get();
			}
			@Override public IntExpr value()
			{
				return value.get();
			}
		};
	}
}
