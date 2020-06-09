package com.dat3m.rmaseli;

public class Main
{

	public static void main(String[] argument)
	{
		Program p = new Program(
			new int[] {2, 2},
			new Statement.Sequence[][]{
				new Statement.Sequence[]{
					new Statement.Sequence(
						new Statement[]{
							Statement.local(2, 0, Integer.of(0)),
							Statement.local(2, 1, Integer.of(1)),
							Statement.writeRelaxed(0, 0),
							Statement.readRelaxed(1, 1)})},
				new Statement.Sequence[]{
					new Statement.Sequence(
						new Statement[]{
							Statement.local(2, 0, Integer.of(1)),
							Statement.local(2, 1, Integer.of(0)),
							Statement.writeRelaxed(0, 0),
							Statement.readRelaxed(1, 1)})}});
		System.out.println(p);
	}
}
