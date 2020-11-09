package com.dat3m.dartagnan.wmm.filter;

import com.dat3m.dartagnan.program.event.Event;

public abstract class FilterAbstract {

    protected String name;

    public String getName(){
        return name;
    }

    public void setName(String name){
        this.name = name;
    }

    public void initialise(){}

    public abstract boolean filter(Event e);

    public abstract boolean filter(com.dat3m.dartagnan.wmm.Event e);
}