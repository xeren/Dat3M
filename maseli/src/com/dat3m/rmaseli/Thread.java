package com.dat3m.rmaseli;

/**
 * Acceptor of new events appended to a program's unrolling.
 * Used by {@link Statement}.
 * The implementation provides the issuing thread.
 * Program Order as well as data and address dependencies are inferred by the implementation.
 * TODO atomic event chains
 */
public interface Thread
{

	/**
	 * Introduces a simple independent write event.
	 * @param key
	 * Thread-local index of the register providing the address.
	 * @param value
	 * Thread-local index of the register providing the value.
	 */
	void write(int key, int value);

	/**
	 * Introduces a simple independent read event.
	 * @param key
	 * Thread-local index of the register providing the address.
	 * @param value
	 * Thread-local index of the register receiving the value.
	 */
	void read(int key, int value);

	/**
	 * Introduces a simple local evaluation.
	 * @param destination
	 * Thread-local index of the register receiving the result.
	 * @param expression
	 * Evaluated expression over registers.
	 * @param dependency
	 * Subset of those registers involved in the evaluation.
	 * Result of {@code for(int i=0;i<dependency.length;i++)dependency[i]=false; expression.register(dependency)}.
	 */
	void local(int destination, Integer expression, boolean[] dependency);

	/*
	 * Introduces a Total-Store-Ordering-specific fence event.
	 * This thread blocks until its previous write events become visible to all other threads.
	 * TODO this might be generalized to tagged events
	 */
	//void fence();

}
