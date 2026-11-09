package com.dat3m.dartagnan.program.processing;

import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.ExpressionFactory;
import com.dat3m.dartagnan.expression.Type;
import com.dat3m.dartagnan.expression.type.TypeFactory;
import com.dat3m.dartagnan.program.Function;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.EventFactory;
import com.dat3m.dartagnan.program.event.core.Label;
import com.dat3m.dartagnan.program.event.core.Local;
import com.dat3m.dartagnan.program.memory.Memory;
import org.junit.Test;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProcessingTest {

    private static final TypeFactory types = TypeFactory.getInstance();
    private static final ExpressionFactory expressions = ExpressionFactory.getInstance();
    private static final EventFactory events = EventFactory.getInstance();

    @Test
    public void memToReg0() throws InvalidConfigurationException {
        final Program program = new Program(new Memory(), Program.SourceLanguage.LLVM);
        final Function f = new Function("f", types.getFunctionType(types.getVoidType(), List.of()), List.of(), 0, null);
        program.addFunction(f);
        final Register r0 = f.newRegister("r0", program.getArchType());
        final Register r1 = f.newRegister("r1", types.getBooleanType());
        final Event alloc = newAlloc(r0, types.getBooleanType(), 1);
        final Event storeFalse = events.newStore(r0, expressions.makeFalse());
        final Event loadFalse = events.newLoad(r1, r0);
        final Event storeTrue = events.newStore(r0, expressions.makeNot(r1));
        final Event loadTrue = events.newLoad(r1, r0);
        final Event assertTrue = events.newAssert(r1, "assert true");
        f.append(List.of(alloc, storeFalse, loadFalse, storeTrue, loadTrue, assertTrue));
        final Configuration config = Configuration.builder().build();
        MemToReg.fromConfig(config).run(f);
        final List<Event> events = f.getEvents();
        assertEquals(5, events.size());
        assertLocal(events.get(0));
        assertLocal(events.get(1));
        assertLocal(events.get(2));
        assertLocal(events.get(3));
        assertComparable(assertTrue, events.get(4));
    }

    @Test
    public void memToReg1() throws InvalidConfigurationException {
        final Program program = new Program(new Memory(), Program.SourceLanguage.LLVM);
        final Function f = new Function("f", types.getFunctionType(types.getVoidType(), List.of()), List.of(), 0, null);
        program.addFunction(f);
        final Register r0 = f.newRegister("r0", program.getArchType());
        final Register r1 = f.newRegister("r1", program.getArchType());
        final Register r2 = f.newRegister("r2", program.getArchType());
        final Register r3 = f.newRegister("r3", types.getBooleanType());
        final Event allocX = newAlloc(r0, types.getBooleanType(), 2);
        final Event allocY = newAlloc(r1, program.getArchType(), 1);
        final Event storeIndex = events.newStore(r1,
                expressions.makeITE(
                        program.newConstant(types.getBooleanType()),
                        expressions.makeValue(1, program.getArchType()),
                        expressions.makeValue(0, program.getArchType())));
        final Event loadIndex = events.newLoad(r2, r1);
        final Event storeTrue = events.newStore(expressions.makeAdd(r0, r2), expressions.makeTrue());
        final Event loadTrue = events.newLoad(r3, expressions.makeAdd(r0, r2));
        final Event assertTrue = events.newAssert(r3, "assert true");
        f.append(List.of(allocX, allocY, storeIndex, loadIndex, storeTrue, loadTrue, assertTrue));
        final Configuration config = Configuration.builder().build();
        MemToReg.fromConfig(config).run(f);
        final List<Event> events = f.getEvents();
        assertEquals(6, events.size());
        assertComparable(allocX, events.get(0));
        assertLocal(events.get(1));
        assertLocal(events.get(2));
        assertComparable(storeTrue, events.get(3));
        assertComparable(loadTrue, events.get(4));
        assertComparable(assertTrue, events.get(5));
    }

    @Test
    public void memToReg2() throws InvalidConfigurationException {
        final Program program = new Program(new Memory(), Program.SourceLanguage.LLVM);
        final Function f = new Function("f", types.getFunctionType(types.getVoidType(), List.of()), List.of(), 0, null);
        program.addFunction(f);
        final Register r0 = f.newRegister("r0", program.getArchType());
        final Register r1 = f.newRegister("r1", program.getArchType());
        final Register r2 = f.newRegister("r2", program.getArchType());
        final Register r3 = f.newRegister("r3", types.getBooleanType());
        final Event allocX = newAlloc(r0, types.getBooleanType(), 2);
        final Event allocY = newAlloc(r1, program.getArchType(), 1);
        final Label labelThen = events.newLabel("then");
        final Label labelEndIf = events.newLabel("endIf");
        final Event jumpNondet = events.newJump(program.newConstant(types.getBooleanType()), labelThen);
        final Event gotoEndIf = events.newGoto(labelEndIf);
        final Event store0 = events.newStore(r1, expressions.makeValue(0, program.getArchType()));
        final Event store1 = events.newStore(r1, expressions.makeValue(1, program.getArchType()));
        final Event loadIndex = events.newLoad(r2, r1);
        final Event storeTrue = events.newStore(expressions.makeAdd(r0, r2), expressions.makeTrue());
        final Event loadTrue = events.newLoad(r3, expressions.makeAdd(r0, r2));
        final Event assertTrue = events.newAssert(r3, "assert true");
        f.append(List.of(allocX, allocY,
                jumpNondet, store0, gotoEndIf, labelThen, store1, labelEndIf,
                loadIndex, storeTrue, loadTrue, assertTrue));
        final Configuration config = Configuration.builder().build();
        MemToReg.fromConfig(config).run(f);
        final List<Event> events = f.getEvents();
        assertEquals(11, events.size());
        assertComparable(allocX, events.get(0));
        assertComparable(jumpNondet, events.get(1));
        assertLocal(events.get(2));
        assertComparable(gotoEndIf, events.get(3));
        assertComparable(labelThen, events.get(4));
        assertLocal(events.get(5));
        assertComparable(labelEndIf, events.get(6));
        assertLocal(events.get(7));
        assertComparable(storeTrue, events.get(8));
        assertComparable(loadTrue, events.get(9));
        assertComparable(assertTrue, events.get(10));
    }

    @Test
    public void memToReg3() throws InvalidConfigurationException {
        final Program program = new Program(new Memory(), Program.SourceLanguage.LLVM);
        final Function f = new Function("f", types.getFunctionType(types.getVoidType(), List.of()), List.of(), 0, null);
        program.addFunction(f);
        final Register r0 = f.newRegister("r0", program.getArchType());
        final Register r1 = f.newRegister("r1", program.getArchType());
        final Register r2 = f.newRegister("r2", program.getArchType());
        final Register r3 = f.newRegister("r3", types.getBooleanType());
        final Event allocX = newAlloc(r0, types.getBooleanType(), 2);
        final Event allocY = newAlloc(r1, program.getArchType(), 1);
        final Label labelThen = events.newLabel("then");
        final Label labelEndIf = events.newLabel("endIf");
        final Event jumpNondet = events.newJump(program.newConstant(types.getBooleanType()), labelThen);
        final Event gotoEndIf = events.newGoto(labelEndIf);
        final Event store0 = events.newStore(r1, r0);
        final Event store1 = events.newStore(r1, expressions.makeAdd(r0, expressions.makeValue(1, program.getArchType())));
        final Event loadIndex = events.newLoad(r2, r1);
        final Event storeTrue = events.newStore(r2, expressions.makeTrue());
        final Event loadTrue = events.newLoad(r3, r2);
        final Event assertTrue = events.newAssert(r3, "assert true");
        f.append(List.of(allocX, allocY,
                jumpNondet, store0, gotoEndIf, labelThen, store1, labelEndIf,
                loadIndex, storeTrue, loadTrue, assertTrue));
        final Configuration config = Configuration.builder().build();
        MemToReg.fromConfig(config).run(f);
        final List<Event> events = f.getEvents();
        assertEquals(11, events.size());
        assertComparable(allocX, events.get(0));
        assertComparable(jumpNondet, events.get(1));
        assertLocal(events.get(2));
        assertComparable(gotoEndIf, events.get(3));
        assertComparable(labelThen, events.get(4));
        assertLocal(events.get(5));
        assertComparable(labelEndIf, events.get(6));
        assertLocal(events.get(7));
        assertComparable(storeTrue, events.get(8));
        assertComparable(loadTrue, events.get(9));
        assertComparable(assertTrue, events.get(10));
    }

    @Test
    public void removeDeadCondJumpsKeepsJumpAfterFallThrough() {
        // if (c) goto skip; L: if (!c) goto then; goto end; then: r = 1; skip: end:
        // L is only reached by falling through, i.e. when c is false, so "if (!c)" is live.
        final Program program = new Program(new Memory(), Program.SourceLanguage.LLVM);
        final Function f = new Function("f", types.getFunctionType(types.getVoidType(), List.of()), List.of(), 0, null);
        program.addFunction(f);
        final Expression c = program.newConstant(types.getBooleanType());
        final Register r = f.newRegister("r", program.getArchType());
        final Label labelL = events.newLabel("L");
        final Label labelThen = events.newLabel("then");
        final Label labelSkip = events.newLabel("skip");
        final Label labelEnd = events.newLabel("end");
        final Event jumpC = events.newJump(c, labelSkip);
        final Event jumpNotC = events.newJump(expressions.makeNot(c), labelThen);
        final Event gotoEnd = events.newGoto(labelEnd);
        final Event local = events.newLocal(r, expressions.makeValue(1, program.getArchType()));
        f.append(List.of(jumpC, labelL, jumpNotC, gotoEnd, labelThen, local, labelSkip, labelEnd));
        RemoveDeadCondJumps.newInstance().run(f);
        assertTrue(f.getEvents().contains(jumpNotC));
        assertTrue(f.getEvents().contains(local));
    }

    @Test
    public void removeDeadCondJumpsKeepsJumpAfterJumpToNextLabel() {
        // if (c) goto L; L: if (!c) goto then; goto end; then: r = 1; end:
        // L is reached both by the jump (c holds) and by falling through (c does not hold).
        final Program program = new Program(new Memory(), Program.SourceLanguage.LLVM);
        final Function f = new Function("f", types.getFunctionType(types.getVoidType(), List.of()), List.of(), 0, null);
        program.addFunction(f);
        final Expression c = program.newConstant(types.getBooleanType());
        final Register r = f.newRegister("r", program.getArchType());
        final Label labelL = events.newLabel("L");
        final Label labelThen = events.newLabel("then");
        final Label labelEnd = events.newLabel("end");
        final Event jumpC = events.newJump(c, labelL);
        final Event jumpNotC = events.newJump(expressions.makeNot(c), labelThen);
        final Event gotoEnd = events.newGoto(labelEnd);
        final Event local = events.newLocal(r, expressions.makeValue(1, program.getArchType()));
        f.append(List.of(jumpC, labelL, jumpNotC, gotoEnd, labelThen, local, labelEnd));
        RemoveDeadCondJumps.newInstance().run(f);
        assertTrue(f.getEvents().contains(jumpNotC));
        assertTrue(f.getEvents().contains(local));
    }

    @Test
    public void removeDeadCondJumpsRemovesJumpOnlyReachedWhenGuardIsFalse() {
        // if (c) goto L; goto end; L: if (!c) goto then; goto end; then: r = 1; end:
        // L is only reached by the jump, i.e. when c holds, so "if (!c)" is dead.
        final Program program = new Program(new Memory(), Program.SourceLanguage.LLVM);
        final Function f = new Function("f", types.getFunctionType(types.getVoidType(), List.of()), List.of(), 0, null);
        program.addFunction(f);
        final Expression c = program.newConstant(types.getBooleanType());
        final Register r = f.newRegister("r", program.getArchType());
        final Label labelL = events.newLabel("L");
        final Label labelThen = events.newLabel("then");
        final Label labelEnd = events.newLabel("end");
        final Event jumpC = events.newJump(c, labelL);
        final Event gotoEnd0 = events.newGoto(labelEnd);
        final Event jumpNotC = events.newJump(expressions.makeNot(c), labelThen);
        final Event gotoEnd1 = events.newGoto(labelEnd);
        final Event local = events.newLocal(r, expressions.makeValue(1, program.getArchType()));
        f.append(List.of(jumpC, gotoEnd0, labelL, jumpNotC, gotoEnd1, labelThen, local, labelEnd));
        RemoveDeadCondJumps.newInstance().run(f);
        assertFalse(f.getEvents().contains(jumpNotC));
    }

    @Test
    public void removeDeadCondJumpsRemovesJumpOnlyReachedByFallThroughWithSameGuard() {
        // if (c) goto skip; L: if (c) goto then; goto end; then: r = 1; skip: end:
        // L is only reached by falling through, i.e. when c is false, so the second "if (c)" is dead.
        final Program program = new Program(new Memory(), Program.SourceLanguage.LLVM);
        final Function f = new Function("f", types.getFunctionType(types.getVoidType(), List.of()), List.of(), 0, null);
        program.addFunction(f);
        final Expression c = program.newConstant(types.getBooleanType());
        final Register r = f.newRegister("r", program.getArchType());
        final Label labelL = events.newLabel("L");
        final Label labelThen = events.newLabel("then");
        final Label labelSkip = events.newLabel("skip");
        final Label labelEnd = events.newLabel("end");
        final Event jumpC0 = events.newJump(c, labelSkip);
        final Event jumpC1 = events.newJump(c, labelThen);
        final Event gotoEnd = events.newGoto(labelEnd);
        final Event local = events.newLocal(r, expressions.makeValue(1, program.getArchType()));
        f.append(List.of(jumpC0, labelL, jumpC1, gotoEnd, labelThen, local, labelSkip, labelEnd));
        RemoveDeadCondJumps.newInstance().run(f);
        assertTrue(f.getEvents().contains(jumpC0));
        assertFalse(f.getEvents().contains(jumpC1));
    }

    private Event newAlloc(Register address, Type type, int count) {
        final Expression size = expressions.makeValue(count, address.getFunction().getProgram().getArchType());
        return events.newAlloc(address, type, size, false, false);
    }

    private void assertLocal(Event event) {
        assertTrue(event instanceof Local);
    }

    private void assertComparable(Event expected, Event actual) {
        assertEquals(expected, actual);
    }
}
