package com.dat3m.dartagnan.wmm.relation.base;

import com.dat3m.dartagnan.Dartagnan;
import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.dartagnan.Event;
import com.dat3m.dartagnan.parsers.cat.ParserCat;
import com.dat3m.dartagnan.parsers.program.ProgramParser;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.utils.ResourceHelper;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.relation.base.memory.RelRf;
import com.dat3m.dartagnan.wmm.utils.Mode;
import com.dat3m.dartagnan.wmm.utils.alias.Alias;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.dat3m.dartagnan.utils.Result.FAIL;
import static org.junit.Assert.*;

public class RelRfTest {

	@Test
	public void testUninitializedMemory() throws IOException {
		Settings settings = new Settings(Mode.KNASTER, Alias.CFIS, 1);
		RelRf.FLAG_CAN_ACCESS_UNINITIALIZED_MEMORY = true;

		String programPath = ResourceHelper.TEST_RESOURCE_PATH + "wmm/relation/basic/rf/";
		String wmmPath = ResourceHelper.CAT_RESOURCE_PATH + "cat/linux-kernel.cat";

		Program p1 = new ProgramParser().parse(new File(programPath + "C-rf-01.litmus"));
		Program p2 = new ProgramParser().parse(new File(programPath + "C-rf-02.litmus"));

		Wmm wmm = new ParserCat().parse(new File(wmmPath));

		RelRf.FLAG_USE_SEQ_ENCODING_REL_RF = false;
		try(EncodeContext context = new EncodeContext(Settings.TACTIC)) {
			assertEquals(FAIL, Dartagnan.testProgram(context, new ProgramCache(p1), wmm, p1.getArch(), settings));
		}
		try(EncodeContext context = new EncodeContext(Settings.TACTIC)) {
			assertEquals(FAIL, Dartagnan.testProgram(context, new ProgramCache(p2), wmm, p2.getArch(), settings));
		}

		RelRf.FLAG_USE_SEQ_ENCODING_REL_RF = true;
		try(EncodeContext context = new EncodeContext(Settings.TACTIC)) {
			assertEquals(FAIL, Dartagnan.testProgram(context, new ProgramCache(p1), wmm, p1.getArch(), settings));
		}
		try(EncodeContext context = new EncodeContext(Settings.TACTIC)) {
			assertEquals(FAIL, Dartagnan.testProgram(context, new ProgramCache(p2), wmm, p2.getArch(), settings));
		}
	}

	@Test
	public void testDuplicatedEdges() throws IOException {
		String p1Path = ResourceHelper.TEST_RESOURCE_PATH + "wmm/relation/basic/rf/C-rf-03.litmus";
		String p2Path = ResourceHelper.TEST_RESOURCE_PATH + "wmm/relation/basic/rf/C-rf-04.litmus";

		String wmmPath = ResourceHelper.CAT_RESOURCE_PATH + "cat/linux-kernel.cat";
		Wmm wmm = new ParserCat().parse(new File(wmmPath));

		Settings settings = new Settings(Mode.KNASTER, Alias.CFIS, 1);

		RelRf.FLAG_CAN_ACCESS_UNINITIALIZED_MEMORY = false;
		RelRf.FLAG_USE_SEQ_ENCODING_REL_RF = false;
		doTestDuplicatedEdges(p1Path, wmm, settings);
		doTestDuplicatedEdges(p2Path, wmm, settings);
		RelRf.FLAG_USE_SEQ_ENCODING_REL_RF = true;
		doTestDuplicatedEdges(p1Path, wmm, settings);
		doTestDuplicatedEdges(p2Path, wmm, settings);

		RelRf.FLAG_CAN_ACCESS_UNINITIALIZED_MEMORY = true;
		RelRf.FLAG_USE_SEQ_ENCODING_REL_RF = false;
		doTestDuplicatedEdges(p1Path, wmm, settings);
		doTestDuplicatedEdges(p2Path, wmm, settings);
		RelRf.FLAG_USE_SEQ_ENCODING_REL_RF = true;
		doTestDuplicatedEdges(p1Path, wmm, settings);
		doTestDuplicatedEdges(p2Path, wmm, settings);
	}

	private void doTestDuplicatedEdges(String programPath, Wmm wmm, Settings settings) throws IOException {
		Program program = new ProgramParser().parse(new File(programPath));
		program.unroll(settings.getBound(), 0);
		program.compile(program.getArch(), 0);

		Map<Integer,Event> events = new HashMap<>() {{
			put(2, null);
			put(5, null);
			put(8, null);
		}};
		extractEvents(program, events);

		try(EncodeContext context = new EncodeContext(Settings.TACTIC)) {
			ProgramCache cache = new ProgramCache(program);

			context.rule(program.getAss().encode(context));
			if(program.getAssFilter() != null) {
				context.rule(program.getAssFilter().encode(context));
			}
			program.encodeCF(context);
			program.encodeFinalRegisterValues(context);
			wmm.encode(context, cache, settings);
			// Don't add constraint of MM, they can also forbid illegal edges

			assertTrue(context.check());

			context.rule(context.edge("rf", events.get(5), events.get(2)));
			assertTrue(context.check());

			context.rule(context.edge("rf", events.get(8), events.get(2)));
			assertFalse(context.check());
		}
	}

	private void extractEvents(Program program, Map<Integer,Event> events) {
		for(Event e: program.getCache().getEvents(FilterBasic.get(EType.ANY))) {
			for(int id: events.keySet()) {
				if(e.getCId() == id) {
					events.put(id, e);
				}
			}
		}
		for(int id: events.keySet()) {
			assertNotNull(events.get(id));
		}
	}
}
