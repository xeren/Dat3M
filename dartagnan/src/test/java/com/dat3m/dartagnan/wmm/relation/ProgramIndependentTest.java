package com.dat3m.dartagnan.wmm.relation;

import com.dat3m.dartagnan.parsers.cat.ParserCat;
import com.dat3m.dartagnan.utils.ResourceHelper;
import com.dat3m.dartagnan.wmm.Wmm;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.Files.walk;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class ProgramIndependentTest {

	public final Path path;
	public final Wmm model;

	@Parameterized.Parameters(name="{0}")
	public static Iterable<Object[]> data() throws IOException{
		return walk(Paths.get(ResourceHelper.CAT_RESOURCE_PATH+"cat"))
			.filter(Files::isRegularFile)
			.filter(p->p.toString().endsWith(".cat"))
			.map(m->new Object[]{m})
			.collect(toList());
	}

	public ProgramIndependentTest(Path p) throws IOException{
		path = p;
		model = new ParserCat().parse(p.toFile());
	}

	@Test
	public void localConsistent(){
		assertTrue(model.isLocalConsistent());
	}
}
