package com.dat3m.dartagnan;
import com.dat3m.dartagnan.parsers.cat.ParserCat;
import com.dat3m.dartagnan.parsers.program.ProgramParser;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.utils.Arch;
import com.dat3m.dartagnan.wmm.utils.Mode;
import com.dat3m.dartagnan.wmm.utils.alias.Alias;
import com.microsoft.z3.*;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;

/**
 * Entry point for testing Bounded Model Checking on a program with respect to a certain memory model.
 * Performs this task for all encoding modes implemented in this module.
 */
public abstract class Compare
{

	public static void main(
		String[] argument)
		throws IOException
	{
		Context context = new Context();
		int bound = 2;
		Alias alias = Alias.CFS;
		Arch target = Arch.NONE;

		EnumMap<Mode,Settings> settings = new EnumMap<>(Mode.class);
		for(Mode m: Mode.values())
			settings.put(m, new Settings(m, alias, bound));

		Wmm model = new ParserCat().parse(new File(argument[0]));

		EnumMap<Mode,Program> program = new EnumMap<>(Mode.class);
		for(Mode m: Mode.values())
		{
			Program p = new ProgramParser().parse(new File(argument[1]));
			program.put(m, p);
			p.unroll(bound, 0);
			p.compile(target, 0);
		}

		EnumMap<Mode,Boolean> result = new EnumMap<>(Mode.class);
		for(Mode m: Mode.values())
		{
			Program p = program.get(m);
			long timeStart = System.nanoTime();
			Solver s = context.mkSolver();
			s.add(p.encodeUINonDet(context),
				p.encodeCF(context),
				p.encodeFinalRegisterValues(context),
				model.encode(p, context, settings.get(m)),
				model.consistent(p, context));
			if(null != p.getAss())
				s.add(p.getAss().encode(context));
			if(null != p.getAssFilter())
				s.add(p.getAssFilter().encode(context));
			long timeEncode = System.nanoTime();
			result.put(m, (null != p.getAss() && p.getAss().getInvert()) != (Status.SATISFIABLE == s.check()));
			long timeSolve = System.nanoTime();
			System.out.printf("%d %d ", timeEncode - timeStart, timeSolve - timeEncode);
		}
		System.out.println();

		if(result.values().stream().anyMatch(x->x) && result.values().stream().anyMatch(x->!x))
			System.err.println(result);
	}

}