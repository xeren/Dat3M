package com.dat3m.dartagnan.wmm.filter;

import com.dat3m.dartagnan.Event;

import java.util.HashMap;
import java.util.Map;

public class FilterUnion extends FilterAbstract {

    private final static Map<String, FilterUnion> instances = new HashMap<>();

    public static FilterUnion get(FilterAbstract filter1, FilterAbstract filter2){
        String key = mkName(filter1, filter2);
        instances.putIfAbsent(key, new FilterUnion(filter1, filter2));
        return instances.get(key);
    }

    private static String mkName(FilterAbstract filter1, FilterAbstract filter2){
        return (filter1 instanceof FilterBasic ? filter1.toString() : "( " + filter1.toString() + " )")
                + " | " + (filter2 instanceof FilterBasic ? filter2.toString() : "( " + filter2.toString() + " )");
    }

    private FilterAbstract filter1;
    private FilterAbstract filter2;

    private FilterUnion(FilterAbstract filter1, FilterAbstract filter2){
        this.filter1 = filter1;
        this.filter2 = filter2;
    }

    @Override
    public boolean filter(Event e){
        return filter1.filter(e) || filter2.filter(e);
    }

    @Override
    public String toString(){
        return mkName(filter1, filter2);
    }

    @Override
    public int hashCode() {
        return filter1.hashCode() | filter2.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;

        if (obj == null || getClass() != obj.getClass())
            return false;

        FilterUnion fObj = (FilterUnion) obj;
        return fObj.filter1.equals(filter1) && fObj.filter2.equals(filter2);
    }
}
