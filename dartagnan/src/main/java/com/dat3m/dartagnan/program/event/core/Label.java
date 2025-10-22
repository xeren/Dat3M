package com.dat3m.dartagnan.program.event.core;

import com.dat3m.dartagnan.expression.Expression;
import com.dat3m.dartagnan.expression.ExpressionFactory;
import com.dat3m.dartagnan.program.event.AbstractEvent;
import com.dat3m.dartagnan.program.event.EventVisitor;
import com.dat3m.dartagnan.program.event.Tag;

import java.util.Set;
import java.util.stream.Collectors;

public class Label extends AbstractEvent {

    private String name;
    private Expression initPost;
    private Expression post;

    public Label(String name){
        this.name = name;
    }

    protected Label(Label other){
        super(other);
        this.name = other.name;
        this.initPost = other.initPost;
        this.post = other.post;
    }

    public String getName(){ return name; }
    public void setName(String name) { this.name = name;}

    public Set<CondJump> getJumpSet() {
        return getUsers().stream()
                .filter(CondJump.class::isInstance).map(CondJump.class::cast)
                .collect(Collectors.toSet());
    }

    @Override
    public String defaultString() {
        return name + ":" + (hasTag(Tag.SPINLOOP) ? "\t### SPINLOOP" : "");
    }

    @Override
    public Label getCopy(){
        return new Label(this);
    }

    @Override
    public <T> T accept(EventVisitor<T> visitor) {
		return visitor.visitLabel(this);
	}

    @Override
    public Expression pre(final Expression post, final ExpressionFactory expressions) {
        if (initPost == null) {
            initPost = post;
        }
        this.post = post;
        return post;
    }

    public Expression getPost() {
        return post;
    }

    public void resetPost() {
        post = initPost;
    }

    public void resetInitPost() {
        initPost = null;
    }
}