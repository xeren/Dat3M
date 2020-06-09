package com.dat3m.rmaseli;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Solver;
import com.microsoft.z3.IntExpr;
import java.util.*;
import java.util.function.Supplier;

/**
 * Each state describes some unrolling of a program in context of a memory model.
 */
public class Program extends Context
{
	private final Solver solver;
	private final Thread[] thread;

	/**
	 * TODO memory model
	 * @param register
	 * For each thread to be described by the program, the count of registers.
	 * @param code
	 * For each thread, a collection of code blocks.
	 *
	 */
	public Program(int[] register, Statement.Sequence[][] code)
	{
		assert register.length == code.length;
		this.solver = mkSolver(); // TODO choose tactic
		this.thread = new Thread[register.length];
		for(int i = 0; i < this.thread.length; i++)
			this.thread[i] = this.new Thread(i, register[i], code[i]);
		for(int i = 0; i < this.thread.length; i++)
		{
			Thread[] t = this.thread[i].extern;
			System.arraycopy(this.thread, 0, t, 0, i);
			System.arraycopy(this.thread, i + 1, t, i, this.thread.length - i - 1);
		}
		//TODO initialize
	}

	/**
	 * @return
	 * The current unrolling allows a violating computation.
	 * TODO return optional proof P {@code void satisfiable() throws P}
	 */
	public boolean reachable()
	{
		switch(solver.check())
		{
			case SATISFIABLE:
				return true;
			case UNSATISFIABLE:
				return false;
			default:
		}
		//TODO differentiate exceptions
		throw new RuntimeException(solver.getReasonUnknown());
	}

	/**
	 * Extends the current unrolling to a higher level.
	 */
	public void unroll()
	{
		for(Thread t: thread)
			t.unroll();
	}

	/**
 	 * @return
 	 * If all previous unrollings did not allow violating computations, then the new and all higher unrollings cannot introduce such.
 	 * TODO return optional proof P {@code void unroll() throws P}
	 */
	public boolean stable()
	{
		//TODO
		return false;
	}

	/**
	 * For each register, the number of assignments.
	 */
	private class Thread
	{
		final int index;
		// shortcut to other threads
		final Thread[] extern = new Thread[thread.length-1];
		// current number of assignments for each register
		final int[] register;
		// parsed program code, at least one, first is initial
		final Statement.Sequence[] code;
		// read events issued by this thread
		final Collection<MemoryEvent> readDynamic = new LinkedList<>();
		// read events with constant address issued by this thread
		final Map<Integer,Collection<MemoryEvent>> readStatic = new HashMap<>();
		// write events issued by this thread
		final Collection<MemoryEvent> writeDynamic = new LinkedList<>();
		// write events with constant address issued by this thread
		final Map<Integer,Collection<MemoryEvent>> writeStatic = new HashMap<>();
		// blocks to unroll
		Collection<Supplier<Block>> block = new ArrayList<>();

		Thread(int index, int register, Statement.Sequence[] code)
		{
			this.index = index;
			this.register = new int[register];
			this.code = code;
		}

		void unroll()
		{
			Collection<Supplier<Block>> pending = block;
			block = new ArrayList<>();
			for(Supplier<Block> b: pending)
				b.get();
		}

		/**
		 * Execution of a compound sequence of statements.
		 */
		class Block implements com.dat3m.rmaseli.Thread
		{
			// identifies this block inside its thread
			final int id;
			// identifies this block inside its program
			final String name;
			// the block before this
			final Block predecessor;
			// code producing this block
			final Statement.Sequence template;
			// version of each thread-local register in this block
			final int[] version = new int[register.length];
			// shortcut name of each register's current version
			final String[] symbol = new String[register.length];
			// write events in order issued from this block
			final ArrayList<MemoryEvent> write;
			// read events in order issued from this block
			final ArrayList<MemoryEvent> read;

			/**
			 * Called during unrolling the program.
			 */
			Block(int id, Block predecessor, Statement.Sequence template)
			{
				this.id = id;
				this.name = "block " + index + " " + id;
				this.predecessor = predecessor;
				this.template = template;
				if(predecessor!=null)
				{
					System.arraycopy(version, 0, predecessor.version, 0, register.length);
					System.arraycopy(symbol, 0, predecessor.symbol, 0, register.length);
				}
				else
				{
					System.arraycopy(version, 0, register, 0, register.length);
					for(int r = 0; r < register.length; ++r)
						symbol[r] = "register " + id + " " + r + " " + version[r];
				}
				write = new ArrayList<>(template.countWrite);
				read = new ArrayList<>(template.countRead);
			}

			/**
			 * Premise of effects issued by this block.
			 * @return
			 * Proposition satisfied iff this block is executed by a candidate.
			 */
			BoolExpr express()
			{
				return mkBoolConst(name);
			}

			/**
			 * Creates a new version of a register.
			 * @param r
			 * Thread-local index of a register.
			 */
			void update(int r)
			{
				version[r] = register[r]++;
				symbol[r] = "register " + id + " " + r + " " + version[r];
			}

			@Override
			public void write(int key, int value)
			{
				String sKey = symbol[key];
				String sValue = symbol[value];
				MemoryEvent e = MemoryEvent.of(()->mkBoolConst(name), ()->mkIntConst(sKey), ()->mkIntConst(sValue));
				//TODO writes to other reads
				write.add(e);
				writeDynamic.add(e);
			}

			@Override
			public void read(int key, int value)
			{
				String sKey = symbol[key];
				update(value);
				String sValue = symbol[value];
				MemoryEvent e = MemoryEvent.of(()->mkBoolConst(name), ()->mkIntConst(sKey), ()->mkIntConst(sValue));
				//TODO reads from some write event
				read.add(e);
				readDynamic.add(e);
			}

			@Override
			public void local(int destination, Integer value, boolean[] dependency)
			{
				IntExpr expression = value.express(Program.this, i->mkIntConst(symbol[i]));
				update(destination);
				solver.add(mkImplies(mkBoolConst(name), mkEq(mkIntConst(symbol[destination]), expression)));
				//TODO event
			}

			@Override
			public String toString()
			{
				return "(block " + template.hashCode() + ")";
			}
		}

		@Override
		public String toString()
		{
			StringBuilder s = new StringBuilder("(thread");
			for(Statement.Sequence q: code)
				s.append(' ').append(q);
			return s.append(')').toString();
		}
	}

	@Override
	public String toString()
	{
		StringBuilder s = new StringBuilder("(program");
		for(Thread t: thread)
			s.append(' ').append(t);
		return s.append(')').toString();
	}
}
