package com.dat3m.dartagnan.wmm;

/**
 * Shared data structure consisting of a list of unary atoms, binary atoms, equalities and variables marked as free.
 * Variables are symbolized and identified by integers.
 * Predicates are identified by strings.
 * Used when encoding relations and filters in first order logic ({@link com.dat3m.dartagnan.wmm.utils.Mode#FO}).
 * @author r.maseli@tu-bs.de
 */
public interface Clause {

	@FunctionalInterface
	interface EqConsumer {
		void of(int first, int second);
	}

	@FunctionalInterface
	interface SetConsumer {
		void of(String name, int member);
	}

	@FunctionalInterface
	interface EdgeConsumer {
		void of(String name, int domain, int range);
	}

	@FunctionalInterface
	interface FreeConsumer {
		void of(int variable);
	}

	/**
	 * Iterates contained equalities in unspecified order.
	 * @param out
	 * Action performed for each contained equality.
	 */
	default void eq(EqConsumer out) {
	}

	/**
	 * Iterates contained unary atoms in unspecified order.
	 * @param out
	 * Action performed for each contained unary atom.
	 */
	default void set(SetConsumer out) {
	}

	/**
	 * Iterates contained binary atoms in unspecified order.
	 * @param out
	 * Action performed for each contained binary atom.
	 */
	default void edge(EdgeConsumer out) {
	}

	/**
	 * Iterates contained free variables.
	 * @param out
	 * Action performed for each contained free variable.
	 */
	default void free(FreeConsumer out) {
	}

	/**
	 * Creates a clause that only contains one equality.
	 * @param first
	 * Some variable identifier.
	 * @param second
	 * Identifier of a variable equalized with {@code first}.
	 * @return
	 * Created collection.
	 */
	static Clause eq(int first, int second) {
		return new Clause() {
			@Override
			public void eq(EqConsumer out) {
				out.of(first, second);
			}
		};
	}

	/**
	 * Creates a clause that only contains one unary atom.
	 * @param name
	 * Predicate identifier of the atom.
	 * @param member
	 * Identifier of the atom's argument.
	 * @return
	 * Created collection.
	 */
	static Clause set(String name, int member) {
		return new Clause() {
			@Override
			public void set(SetConsumer out) {
				out.of(name, member);
			}
		};
	}

	/**
	 * Creates a clause that only contains one binary atom.
	 * @param name
	 * Identifier of the predicate.
	 * @param domain
	 * Identifier of the first argument.
	 * @param range
	 * Identifier of the second argument.
	 * @return
	 * Created collection.
	 */
	static Clause edge(String name, int domain, int range) {
		return new Clause() {
			@Override
			public void edge(EdgeConsumer out) {
				out.of(name, domain, range);
			}
		};
	}

	/**
	 * Creates a clause that only contains one free variable.
	 * @param variable
	 * Identifier of the free variable.
	 * @return
	 * Created collection.
	 */
	static Clause free(int variable) {
		return new Clause() {
			@Override
			public void free(FreeConsumer out) {
				out.of(variable);
			}
		};
	}

	/**
	 * Creates the union of two clauses.
	 * @param other
	 * Second clause to be combined with this clause.
	 * @return
	 * Created collection.
	 */
	default Clause combine(Clause other) {
		Clause parent = this;
		return new Clause() {
			@Override
			public void eq(EqConsumer out) {
				parent.eq(out);
				other.eq(out);
			}
			@Override
			public void set(SetConsumer out) {
				parent.set(out);
				other.set(out);
			}
			@Override
			public void edge(EdgeConsumer out) {
				parent.edge(out);
				other.edge(out);
			}
			@Override
			public void free(FreeConsumer out) {
				parent.free(out);
				other.free(out);
			}
		};
	}
}
