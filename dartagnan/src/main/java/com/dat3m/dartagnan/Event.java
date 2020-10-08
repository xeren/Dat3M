package com.dat3m.dartagnan;

/**
 * Event issued in a compiled program.
 */
public interface Event {

	/**
	 * Uniquely identifies the instruction in the original program.
	 */
	int getOId();

	/**
	 * Uniquely identifies the instruction in the unrolled program.
	 * Pairs of events in a common branch of a same thread preserve this order.
	 */
	int getUId();

	/**
	 * Uniquely identifies this event in the compiled program.
	 * Pairs of events in a common branch of a same thread preserve this order.
	 */
	int getCId();

	/**
	 * For a dynamic class of events, checks this event for membership.
	 * @param eventclass
	 * Identifier of the class.
	 * @return
	 * True if this event is a member of {@code eventclass}.
	 */
	boolean is(String eventclass);
}
