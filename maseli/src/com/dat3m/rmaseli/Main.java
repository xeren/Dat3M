package com.dat3m.rmaseli;
import com.microsoft.z3.FuncDecl;

import static com.dat3m.rmaseli.Communication.*;
import static com.dat3m.rmaseli.Statement.*;

public class Main
{

	public static void main(String[] argument)
	{
		Context context = new Context();

		defineEventType(context);
		defineLocation(context);
		defineInternal(context);
		defineExternal(context);
		defineCommunication(context);
		defineReadFrom(context);
		let(context, "po-loc", and(of("po"), of("loc")));
		let(context, "fr", join(inv(of("rf")), of("co")));
		let(context, "rfi", and(of("rf"), of("int")));
		let(context, "rfe", and(of("rf"), of("ext")));
		let(context, "coi", and(of("co"), of("int")));
		let(context, "coe", and(of("co"), of("ext")));
		let(context, "fri", and(of("fr"), of("int")));
		let(context, "fre", and(of("fr"), of("ext")));

		// X86 Total Store Ordering
		let(context, "com", or(or(of("co"), of("fr")), of("rf")));
		acyclic(context, or(of("po-loc"), of("com")));
		empty(context, and(of("rmw"), join(of("fre"), of("coe"))));
		let(context, "implied", and(
			and(of("po"), full(Set.of("write"), Set.of("read"))),
			or(full(Set.of("memory"), Set.of("atomic")), full(Set.of("atomic"), Set.of("memory")))
		));
		let(context, "com-tso", or(or(of("co"), of("fr")), of("rfe")));
		let(context, "po-tso", or(
			and(of("po"), not(full(Set.of("write"), Set.of("read")))),
			of("mfence")
		));
		let(context, "ghb-tso", or(or(of("po-tso"), of("com-tso")), of("implied")));
		acyclic(context, of("ghb-tso"));

		// havoc expressions may have different types and may co-occur
		Statement s6 = end();
		Statement s5 = local(4, 1, Integer.of(1), s6);
		Statement s4 = local(4, 0, Integer.of(1), s6);
		Statement s3 = branch(4, Proposition.havoc(0), s4, s5);
		Statement s2 = local(4, 3, Integer.of(1), s3);
		Statement s1 = local(4, 2, Integer.of(1), s3);
		Statement s0 = branch(4, Proposition.havoc(1), s1, s2);

		FuncDecl[] register = java.util.Arrays.stream(new String[]{"a", "b", "c", "d"})
			.map(s->context.mkFuncDecl(s, context.mkEventSort(), context.mkIntSort()))
			.toArray(FuncDecl[]::new);
		java.util.stream.Stream.of(s6, s5, s4, s3, s2, s1, s0).forEach(s->s.express(context, register));

		//c.defineThreadCount();
		//fixedpoint.add(context.mkEq(context.mkIntConst("threadcount"), context.mkInt(threads)));

	}
}
