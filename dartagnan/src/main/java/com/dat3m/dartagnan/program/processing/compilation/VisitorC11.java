package com.dat3m.dartagnan.program.processing.compilation;

import com.dat3m.dartagnan.configuration.Arch;
import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.Type;
import com.dat3m.dartagnan.expression.type.BooleanType;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.MemoryEvent;
import com.dat3m.dartagnan.program.event.Tag;
import com.dat3m.dartagnan.program.event.core.*;
import com.dat3m.dartagnan.program.event.lang.catomic.*;
import com.dat3m.dartagnan.program.event.lang.llvm.*;

import java.util.List;

import static com.dat3m.dartagnan.program.event.EventFactory.*;
import static com.dat3m.dartagnan.program.event.Tag.C11.*;
import static com.google.common.base.Verify.verify;

public class VisitorC11 extends VisitorBase {

    @Override
    public List<Event> visitLoad(Load e) {
        return tagList(eventSequence(e));
    }

    @Override
    public List<Event> visitStore(Store e) {
        return tagList(eventSequence(e));
    }

    // =============================================================================================
    // =========================================== C11 =============================================
    // =============================================================================================

    @Override
    public List<Event> visitAtomicCmpXchg(AtomicCmpXchg e) {
        Register resultRegister = e.getResultRegister();
        Expression address = e.getAddress();
        String mo = e.getMo();
        Expression expectedAddr = e.getAddressOfExpected();
        Type type = resultRegister.getType();
        Register booleanResultRegister = type instanceof BooleanType ? resultRegister :
                e.getFunction().newRegister(types.getBooleanType());
        Local castResult = type instanceof BooleanType ? null :
                events.newLocal(resultRegister, expressions.makeCast(booleanResultRegister, type));
        Register regExpected = e.getFunction().newRegister(type);
        Register regValue = e.getFunction().newRegister(type);
        Load loadExpected = events.newLoad(regExpected, expectedAddr);
        Store storeExpected = events.newStore(expectedAddr, regValue);
        Label casFail = events.newLabel("CAS_fail");
        Label casEnd = events.newLabel("CAS_end");
        Local casCmpResult = events.newLocal(booleanResultRegister, expressions.makeEQ(regValue, regExpected));
        CondJump branchOnCasCmpResult = events.newJumpUnless(booleanResultRegister, casFail);
        CondJump gotoCasEnd = events.newGoto(casEnd);
        Load loadValue = events.newRMWLoadWithMo(regValue, address, loadMO(mo));
        Store storeValue = events.newRMWStoreWithMo(loadValue, address, e.getStoreValue(), storeMO(mo));

        return tagList(e, eventSequence(
                loadExpected,
                loadValue,
                casCmpResult,
                branchOnCasCmpResult,
                storeValue,
                gotoCasEnd,
                casFail,
                storeExpected,
                casEnd,
                castResult
        ));
    }

    @Override
    public List<Event> visitAtomicFetchOp(AtomicFetchOp e) {
        Register resultRegister = e.getResultRegister();
        Expression address = e.getAddress();
        String mo = e.getMo();

        Register dummyReg = e.getFunction().newRegister(resultRegister.getType());
        Load load = events.newRMWLoadWithMo(resultRegister, address, loadMO(mo));
        Local localOp = events.newLocal(dummyReg, expressions.makeIntBinary(resultRegister, e.getOperator(), e.getOperand()));
        RMWStore store = events.newRMWStoreWithMo(load, address, dummyReg, storeMO(mo));

        return tagList(e, eventSequence(
                load,
                localOp,
                store
        ));
    }

    @Override
    public List<Event> visitAtomicOp(AtomicOp e) {
        Expression address = e.getAddress();
        String mo = e.getMo();

        Register dummyReg = e.getFunction().newRegister(e.getAccessType());
        Load load = events.newRMWLoad(dummyReg, address);
        Local localOp = events.newLocal(dummyReg, expressions.makeIntBinary(dummyReg, e.getOperator(), e.getOperand()));
        RMWStore store = events.newRMWStoreWithMo(load, address, dummyReg, Tag.C11.storeMO(mo));

        load.addTags(Tag.C11.ATOMIC, Tag.C11.NORETURN); // Note that the load has no mo, but is still atomic!
        store.addTags(Tag.C11.NORETURN);

        return tagList(e, eventSequence(
                load,
                localOp,
                store
        ));
    }

    @Override
    public List<Event> visitAtomicLoad(AtomicLoad e) {
        return tagList(e, eventSequence(
                events.newLoadWithMo(e.getResultRegister(), e.getAddress(), loadMO(e.getMo()))
        ));
    }

    @Override
    public List<Event> visitAtomicStore(AtomicStore e) {
        return tagList(e, eventSequence(
                events.newStoreWithMo(e.getAddress(), e.getMemValue(), storeMO(e.getMo()))
        ));
    }

    @Override
    public List<Event> visitAtomicThreadFence(AtomicThreadFence e) {
        return tagList(e, eventSequence(
                newFence(e.getMo())
        ));
    }

    @Override
    public List<Event> visitAtomicXchg(AtomicXchg e) {
        Expression address = e.getAddress();
        String mo = e.getMo();

        Load load = events.newRMWLoadWithMo(e.getResultRegister(), address, loadMO(mo));
        RMWStore store = events.newRMWStoreWithMo(load, address, e.getValue(), storeMO(mo));

        return tagList(e, eventSequence(
                load,
                store
        ));
    }

    @Override
    public List<Event> visitControlBarrier(ControlBarrier e) {
        Event barrier = events.newControlBarrier(e.getName(), e.getInstanceId(), e.getExecScope());
        barrier.addTags(MO_ACQUIRE_RELEASE);
        return tagList(e, eventSequence(barrier));
    }

    // =============================================================================================
    // =========================================== LLVM ============================================
    // =============================================================================================

    @Override
    public List<Event> visitLlvmLoad(LlvmLoad e) {
        return tagList(eventSequence(
                events.newLoadWithMo(e.getResultRegister(), e.getAddress(), loadMO(e.getMo()))
        ));
    }

    @Override
    public List<Event> visitLlvmStore(LlvmStore e) {
        return tagList(eventSequence(
                events.newStoreWithMo(e.getAddress(), e.getMemValue(), storeMO(e.getMo()))
        ));
    }

    @Override
    public List<Event> visitLlvmXchg(LlvmXchg e) {
        Register resultRegister = e.getResultRegister();
        Expression address = e.getAddress();
        String mo = e.getMo();

        Load load = events.newRMWLoadWithMo(resultRegister, address, loadMO(mo));
        Store store = events.newRMWStoreWithMo(load, address, e.getValue(), storeMO(mo));

        return tagList(eventSequence(
                load,
                store
        ));
    }

    @Override
    public List<Event> visitLlvmRMW(LlvmRMW e) {
        Register resultRegister = e.getResultRegister();
        Expression address = e.getAddress();
        String mo = e.getMo();

        Register dummyReg = e.getFunction().newRegister(resultRegister.getType());
        Local localOp = events.newLocal(dummyReg, expressions.makeIntBinary(resultRegister, e.getOperator(), e.getOperand()));

        Load load = events.newRMWLoadWithMo(resultRegister, address, loadMO(mo));
        Store store = events.newRMWStoreWithMo(load, address, dummyReg, storeMO(mo));

        return tagList(eventSequence(
                load,
                localOp,
                store
        ));
    }

    @Override
    protected List<Event> newLlvmCmpXchg(Register oldValue, Register success, Expression address, Expression expected,
            Expression newValue, String mo, boolean strong) {
        verify(success.getType() instanceof BooleanType, "Non-boolean success register.");

        Local casCmpResult = events.newLocal(success, expressions.makeEQ(oldValue, expected));
        Label casEnd = events.newLabel("CAS_end");
        CondJump branchOnCasCmpResult = events.newJumpUnless(success, casEnd);

        Load load = events.newRMWLoadExclusiveWithMo(oldValue, address, loadMO(mo));
        Store store = events.newRMWStoreExclusiveWithMo(address, newValue, strong, false, storeMO(mo));

        return tagList(eventSequence(
                load,
                casCmpResult,
                branchOnCasCmpResult,
                store,
                strong ? null : events.newExecutionStatus(success, store),
                strong ? null : events.newLocal(success, expressions.makeNot(success)),
                casEnd
        ));
    }

    @Override
    public List<Event> visitLlvmFence(LlvmFence e) {
        return tagList(eventSequence(
                newFence(e.getMo())
        ));
    }

    protected List<Event> tagList(List<Event> in) {
        in.forEach(e -> tagEvent(null, e));
        return in;
    }

    protected List<Event> tagList(Event originalEvent, List<Event> in) {
        in.forEach(e -> tagEvent(originalEvent, e));
        return in;
    }

    private Event newFence(String name) {
        return events.newGenericVisibleEvent(name, Tag.FENCE, name);
    }

    private void tagEvent(Event originalEvent, Event e) {
        if (originalEvent != null) {
            String scope = Tag.getScopeTag(originalEvent, Arch.OPENCL);
            List<String> spaces = Tag.OpenCL.getSpaceTags(originalEvent);
            if (e instanceof MemoryEvent || e instanceof GenericVisibleEvent) {
                if (!scope.isEmpty()) {
                    e.addTags(scope);
                }
                e.addTags(spaces);
            }
        }
        if (e instanceof MemoryEvent) {
            MemoryCoreEvent.MemoryOrder mo = e.getMetadata(MemoryCoreEvent.MemoryOrder.class);
            boolean canRace = mo == null || mo.value().equals(NONATOMIC);
            e.addTags(canRace ? NONATOMIC : ATOMIC);
        }
    }
}