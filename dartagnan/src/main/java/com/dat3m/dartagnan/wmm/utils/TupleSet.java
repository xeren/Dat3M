package com.dat3m.dartagnan.wmm.utils;

import com.dat3m.dartagnan.program.event.Event;

import java.util.*;

public class TupleSet implements Set<Tuple>{

    private Set<Tuple> tuples = new HashSet<>();
    private Map<Event, Set<Tuple>> byFirst = new HashMap<>();
    private boolean isUpdated = false;

    @Override
    public boolean add(Tuple e){
        boolean result = tuples.add(e);
        isUpdated |= result;
        return result;
    }

    @Override
    public boolean addAll(Collection<? extends Tuple> c){
        boolean result = c instanceof TupleSet ? tuples.addAll(((TupleSet)c).tuples) : tuples.addAll(c);
        isUpdated |= result;
        return result;
    }

    @Override
    public void clear(){
        tuples.clear();
        isUpdated = true;
    }

    @Override
    public boolean contains(Object e){
        return tuples.contains(e);
    }

    @Override
    public boolean containsAll(Collection<?> c){
        return c instanceof TupleSet ? tuples.containsAll(((TupleSet)c).tuples) : tuples.containsAll(c);
    }

    @Override
    public boolean equals(Object obj){
        if (this == obj)
            return true;

        if (obj == null || getClass() != obj.getClass())
            return false;

        return tuples.equals(((TupleSet)obj).tuples);
    }

    @Override
    public int hashCode(){
        return tuples.hashCode();
    }

    @Override
    public boolean isEmpty(){
        return tuples.isEmpty();
    }

    @Override
    public Iterator<Tuple> iterator(){
        return tuples.iterator();
    }

    @Override
    public boolean remove(Object e){
        boolean result = tuples.remove(e);
        isUpdated |= result;
        return result;
    }

    @Override
    public boolean removeAll(Collection<?> c){
        boolean result = c instanceof TupleSet ? tuples.removeAll(((TupleSet)c).tuples) : tuples.removeAll(c);
        isUpdated |= result;
        return result;
    }

    @Override
    public boolean retainAll(Collection<?> c){
        boolean result = c instanceof TupleSet ? tuples.retainAll(((TupleSet)c).tuples) : tuples.retainAll(c);
        isUpdated |= result;
        return result;
    }

    @Override
    public int size(){
        return tuples.size();
    }

    @Override
    public Object[] toArray(){
        return tuples.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a){
        return tuples.toArray(a);
    }

    @Override
    public String toString(){
        return tuples.toString();
    }

    public Set<Tuple> getByFirst(Event e){
        if(isUpdated){
            updateAuxiliary();
        }
        byFirst.putIfAbsent(e, new HashSet<>());
        return byFirst.get(e);
    }

    private void updateAuxiliary(){
        byFirst.clear();
        for(Tuple e : tuples){
            byFirst.putIfAbsent(e.getFirst(), new HashSet<>());
            byFirst.get(e.getFirst()).add(e);
        }
        isUpdated = false;
    }
}
