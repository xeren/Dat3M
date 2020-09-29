package com.dat3m.dartagnan.utils;

import com.dat3m.dartagnan.wmm.utils.Mode;
import com.dat3m.dartagnan.wmm.utils.alias.Alias;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static java.util.stream.Stream.concat;

public class Settings {

    public static final String TACTIC = "qfbv";

    private Mode mode;
    private final Alias alias;
    private final int bound;

    private boolean draw = false;
    private Set<String> relations = Set.of();

    public Settings(Mode mode, Alias alias, int bound){
        this.mode = mode == null ? Mode.KNASTER : mode;
        this.alias = alias == null ? Alias.CFIS : alias;
        this.bound = Math.max(1, bound);
    }

    public Settings(Mode mode, Alias alias, int bound, boolean draw, Collection<String> relations){
        this(mode, alias, bound);
        if(draw){
            this.draw = true;
            if(mode == Mode.KNASTER){
                this.mode = Mode.IDL;
            }

            this.relations = Graph.getDefaultRelations();
            if(relations != null) {
                this.relations = concat(this.relations.stream(), relations.stream()).collect(toUnmodifiableSet());
            }
        }
    }

    public Settings(Mode mode, Alias alias, int bound, boolean draw, String... relations){
        this(mode, alias, bound, draw, Arrays.asList(relations));
    }

    public Mode getMode(){
        return mode;
    }

    public Alias getAlias(){
        return alias;
    }

    public int getBound(){
        return bound;
    }

    public boolean getDrawGraph(){
        return draw;
    }

    public Set<String> getGraphRelations(){
        return relations;
    }

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        sb.append("mode=").append(mode).append(" alias=").append(alias).append(" bound=").append(bound);
        if(draw){
            sb.append(" draw=").append(String.join(",", relations));
        }
        return sb.toString();
    }
}
