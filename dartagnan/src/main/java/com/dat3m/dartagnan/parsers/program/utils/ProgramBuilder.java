package com.dat3m.dartagnan.parsers.program.utils;

import com.dat3m.dartagnan.program.Thread;
import com.dat3m.dartagnan.program.event.*;
import com.dat3m.dartagnan.asserts.AbstractAssert;
import com.dat3m.dartagnan.expression.IConst;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.memory.Address;
import com.dat3m.dartagnan.program.memory.Memory;

import java.util.*;

public class ProgramBuilder {

    private final Map<Integer, Thread> threads = new HashMap<>();

    private final Map<String, Address> pointers = new HashMap<>();

    private final Memory memory = new Memory();

    private final Map<String, Label> labels = new HashMap<>();

    private AbstractAssert ass;
    private AbstractAssert assFilter;

    private int lastOrigId = 0;

    public Program build(){
        Program program = new Program(memory);
        buildInitThreads();
        for(Thread thread : threads.values()){
            validateLabels(thread);
            program.add(thread);
        }
        program.setAss(ass);
        program.setAssFilter(assFilter);
        return program;
    }

    public void initThread(String name, int id){
        if(!threads.containsKey(id)){
            Skip threadEntry = new Skip();
            threadEntry.setOId(lastOrigId++);
            threads.putIfAbsent(id, new Thread(name, id, threadEntry));
        }
    }

    public void initThread(int id){
        initThread(String.valueOf(id), id);
    }

    public Event addChild(int thread, Event child){
        if(!threads.containsKey(thread)){
            throw new RuntimeException("Thread " + thread + " is not initialised");
        }
        child.setOId(lastOrigId++);
        threads.get(thread).append(child);
        return child;
    }

    public void setAssert(AbstractAssert ass){
        this.ass = ass;
    }

    public void setAssertFilter(AbstractAssert ass){
        this.assFilter = ass;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Declarators

    public void initLocEqLocPtr(String leftName, String rightName, int precision){
		pointer(leftName,precision).setInitialValue(pointer(rightName,precision));
    }

    public void initLocEqLocVal(String leftName, String rightName, int precision){
		pointer(leftName,precision).setInitialValue(pointer(rightName,precision).getInitialValue());
    }

    public void initLocEqConst(String locName, IConst iValue){
		pointer(locName,iValue.getPrecision()).setInitialValue(iValue);
    }

    public void initRegEqLocPtr(int regThread, String regName, String locName, int precision){
        Register reg = getOrCreateRegister(regThread, regName, precision);
        addChild(regThread, new Local(reg,pointer(locName,precision)));
    }

    public void initRegEqLocVal(int regThread, String regName, String locName, int precision){
        Register reg = getOrCreateRegister(regThread, regName, precision);
		addChild(regThread,new Local(reg,pointer(locName,precision).getInitialValue()));
    }

    public void initRegEqConst(int regThread, String regName, IConst iValue){
        addChild(regThread, new Local(getOrCreateRegister(regThread, regName, iValue.getPrecision()), iValue));
    }

    public void addDeclarationArray(String name, List<IConst> values, int precision){
        int size = values.size();
        List<Address> addresses = memory.malloc(name, size, precision);
        for(int i = 0; i < size; i++){
            String varName = name + "[" + i + "]";
            Address address = addresses.get(i);
            pointers.put(varName,address);
			address.setInitialValue(values.get(i));
        }
        pointers.put(name, addresses.get(0));
    }

    public void addDeclarationArray(String name, List<IConst> values){
    	addDeclarationArray(name, values, -1);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Utility

    public Event getLastEvent(int thread){
        return threads.get(thread).getExit();
    }

	public Address pointerTry(String name) {
		return pointers.get(name);
	}

	public Address pointer(String name, int precision){
		return pointers.computeIfAbsent(name,k->memory.malloc(name,1,precision).get(0));
	}

    public Register getRegister(int thread, String name){
        if(threads.containsKey(thread)){
            return threads.get(thread).getRegister(name);
        }
        return null;
    }

    public Register getOrCreateRegister(int threadId, String name, int precision){
        initThread(threadId);
        Thread thread = threads.get(threadId);
        Register register = thread.getRegister(name);
        if(register == null){
            return thread.addRegister(name, precision);
        }
        return register;
    }

    public Register getOrErrorRegister(int thread, String name){
        if(threads.containsKey(thread)){
            Register register = threads.get(thread).getRegister(name);
            if(register != null){
                return register;
            }
        }
        throw new ParsingException("Register " + thread + ":" + name + " is not initialised");
    }

    public boolean hasLabel(String name) {
    	return labels.containsKey(name);
    }
    
    public Label getOrCreateLabel(String name){
        labels.putIfAbsent(name, new Label(name));
        return labels.get(name);
    }

    public IConst getInitValue(Address address){
        return address.getInitialValue();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Private utility

    private int nextThreadId(){
        int maxId = -1;
        for(int key : threads.keySet()){
            maxId = Integer.max(maxId, key);
        }
        return maxId + 1;
    }

    private void buildInitThreads(){
        int nextThreadId = nextThreadId();
		for(var a : memory.getAllAddresses()) {
			var e = new Init(a);
            Thread thread = new Thread(nextThreadId, e);
            threads.put(nextThreadId, thread);
            nextThreadId++;
        }
    }

    private void validateLabels(Thread thread) throws ParsingException {
        Map<String, Label> threadLabels = new HashMap<>();
        Set<String> referencedLabels = new HashSet<>();
        Event e = thread.getEntry();
        while(e != null){
            if(e instanceof CondJump){
                referencedLabels.add(((CondJump) e).getLabel().getName());
            } else if(e instanceof Label){
                Label label = labels.remove(((Label) e).getName());
                if(label == null){
                    throw new ParsingException("Duplicated label " + ((Label) e).getName());
                }
                threadLabels.put(label.getName(), label);
            }
            e = e.getSuccessor();
        }

        for(String labelName : referencedLabels){
            if(!threadLabels.containsKey(labelName)){
                throw new ParsingException("Illegal jump to label " + labelName);
            }
        }
    }
}
