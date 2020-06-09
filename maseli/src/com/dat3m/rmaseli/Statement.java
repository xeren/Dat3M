package com.dat3m.rmaseli;

import java.util.ArrayList;

/**
 * Control statements defined by arbitrary programs.
 */
public interface Statement
{

	/**
	 * Instantiates new events based on this statement.
	 * Called during unrolling of a program.
	 */
	void unroll(Thread thread);

	/**
	 * Maximal count of read events produced by this statement.
	 */
	default int countRead()
	{
		return 0;
	}

	/**
	 * Maximal count of write events produced by this statement.
	 */
	default int countWrite()
	{
		return 0;
	}

	/**
	 * @param key
	 * Thread-local index of the register providing the address.
	 * @param value
	 * Thread-local index of the register receiving the value.
	 * @return
	 * Factory producing one read event per iteration.
	 */
	static Statement readRelaxed(int key, int value)
	{
		return new Statement()
		{
			@Override public void unroll(Thread thread)
			{
				thread.read(key, value);
			}
			@Override public int countRead()
			{
				return 1;
			}
			@Override public String toString()
			{
				return "(read relaxed " + key + " " + value + ")";
			}
		};
	}

	/**
	 * @param key
	 * Thread-local index of the register providing the address.
	 * @param value
	 * Thread-local index of the register providing the written value.
	 * @return
	 * Factory producing one write event per iteration.
	 */
	static Statement writeRelaxed(int key, int value)
	{
		return new Statement()
		{
			@Override public void unroll(Thread thread)
			{
				thread.write(key, value);
			}
			@Override public int countWrite()
			{
				return 1;
			}
			@Override public String toString()
			{
				return "(write relaxed " + key + " " + value + ")";
			}
		};
	}

	static Statement local(int registercount, int destination, Integer expression)
	{
		boolean[] dependency = new boolean[registercount];
		expression.register(dependency);
		return new Statement()
		{
			@Override public void unroll(Thread thread)
			{
				thread.local(destination, expression, dependency);
			}
			@Override public String toString()
			{
				return "(local " + destination + " " + expression + " " + java.util.Arrays.toString(dependency) + ")";
			}
		};
	}

	/**
	 * List of simple statements.
	 */
	class Sequence
	{
		// dynamic statements sequentially performed by the thread
		private final Statement[] statement;
		// maximal number of read events per iteration
		public final int countRead;
		// maximal number of write events per iteration
		public final int countWrite;

		public Sequence(Statement[] statement)
		{
			this.statement = statement;
			this.countRead = java.util.Arrays.stream(statement).mapToInt(Statement::countRead).sum();
			this.countWrite = java.util.Arrays.stream(statement).mapToInt(Statement::countWrite).sum();
		}

		public String toString()
		{
			StringBuilder s = new StringBuilder("(sequence ").append(hashCode());
			for(Statement t: statement)
				s.append(' ').append(t);
			return s.append(')').toString();
		}

	}

	/**
	 * List of simple statements ending with a conditional.
	 */
	class SequenceConditional extends Sequence
	{
		private final boolean[] dependency;
		private final Proposition condition;
		private final Sequence branchTrue;
		private final Sequence branchFalse;

		public SequenceConditional(Statement[] statement, int registercount, Proposition condition, Sequence branchTrue, Sequence branchFalse)
		{
			super(statement);
			this.dependency = new boolean[registercount];
			condition.register(dependency);
			this.condition = condition;
			this.branchTrue = branchTrue;
			this.branchFalse = branchFalse;
		}
	}
}
