package com.dat3m.dartagnan.parsers.program.visitors.boogie;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.dat3m.dartagnan.expression.ExprInterface;
import com.dat3m.dartagnan.expression.IConst;
import com.dat3m.dartagnan.parsers.BoogieParser.Call_cmdContext;
import com.dat3m.dartagnan.parsers.program.utils.ParsingException;
import com.dat3m.dartagnan.program.Register;
import com.dat3m.dartagnan.program.event.Local;
import com.dat3m.dartagnan.program.memory.Address;
import com.dat3m.dartagnan.program.utils.EType;

public class StdProcedures {
	
	// TODO: find a good way of dealing with allocation of dynamic size
	private static int MALLOC_ARRAY_SIZE = 100;
    // TODO: deal with this properly
    public static int I32_BYTES = 4;

	public static List<String> STDPROCEDURES = Arrays.asList(
			"external_alloc",
			"$alloc",
			"__assert_rtn",
			"assert_.i32",
			"$malloc",
			"calloc",
			"malloc",
			"fopen",
			"free",
			"memcpy",
			"$memcpy",
			"memset",
			"$memset",
			"nvram_read_byte", 
			"strcpy",
			"strcmp",
			"strncpy", 
			"llvm.stackrestore",
			"llvm.stacksave",
			"llvm.lifetime.start",
			"llvm.lifetime.end");
	
	public static void handleStdFunction(VisitorBoogie visitor, Call_cmdContext ctx) {
		String name = ctx.call_params().Define() == null ? ctx.call_params().Ident(0).getText() : ctx.call_params().Ident(1).getText();
		if(name.equals("$alloc") || name.equals("$malloc") || name.equals("calloc") || name.equals("malloc") || name.equals("external_alloc") ) {
			alloc(visitor, ctx);
			return;
		}
		if(name.equals("__assert_rtn") || name.equals("assert_.i32")) {
			__assert(visitor, ctx);
			return;
		}
		if(name.startsWith("fopen")) {
			// TODO: Implement this
			return;			
		}
		if(name.startsWith("free")) {
			// TODO: Implement this
			return;			
		}
		if(name.startsWith("memcpy") | name.startsWith("$memcpy")) {
			// TODO: Implement this
			return;			
		}
		if(name.startsWith("memset") || name.startsWith("$memset")) {
			throw new ParsingException(name + " cannot be handled");
		}
		if(name.startsWith("nvram_read_byte")) {
			throw new ParsingException(name + " cannot be handled");
		}
		if(name.startsWith("strcpy")) {
			throw new ParsingException(name + " cannot be handled");
		}
		if(name.startsWith("strcmp")) {
			// TODO: Implement this
			return;			
		}
		if(name.startsWith("strncpy")) {
			throw new ParsingException(name + " cannot be handled");
		}
		if(name.startsWith("llvm.stackrestore")) {
			// TODO: Implement this
			return;			
		}
		if(name.startsWith("llvm.stacksave")) {
			// TODO: Implement this
			return;			
		}
		if(name.startsWith("llvm.lifetime.start")) {
			// TODO: Implement this
			return;			
		}
		if(name.startsWith("llvm.lifetime.end")) {
			// TODO: Implement this
			return;			
		}
        throw new UnsupportedOperationException(name + " procedure is not part of STDPROCEDURES");
	}	
	
	private static void alloc(VisitorBoogie visitor, Call_cmdContext ctx) {
		int size;
		try {
			size = ((ExprInterface)ctx.call_params().exprs().expr(0).accept(visitor)).reduce().getValue()*I32_BYTES;			
		} catch (Exception e) {
			String tmp = ctx.call_params().getText();
			tmp = tmp.contains(",") ? tmp.substring(0, tmp.indexOf(',')) : tmp.substring(0, tmp.indexOf(')')); 
			tmp = tmp.substring(tmp.lastIndexOf('(')+1);
			size = Integer.parseInt(tmp)*MALLOC_ARRAY_SIZE;			
		}
		List<IConst> values = Collections.nCopies(size, new IConst(0, -1));
		String ptr = ctx.call_params().Ident(0).getText();
		Register start = visitor.thread.register(visitor.currentScope.getID() + ":" + ptr);
		// Several threads can use the same pointer name but when using addDeclarationArray, 
		// the name should be unique, thus we add the process identifier.
		visitor.programBuilder.addDeclarationArray(visitor.currentScope.getID() + ":" + ptr, values, start.getPrecision());
		Address adds = visitor.programBuilder.getPointer(visitor.currentScope.getID() + ":" + ptr);
		visitor.thread.add(new Local(start, adds));
		visitor.allocationRegs.add(start);
	}
	
	private static void __assert(VisitorBoogie visitor, Call_cmdContext ctx) {
    	Register ass = visitor.thread.register("assert_" + visitor.assertionIndex, -1);
    	visitor.assertionIndex++;
    	ExprInterface expr = (ExprInterface)ctx.call_params().exprs().accept(visitor);
    	if(expr instanceof IConst && ((IConst)expr).getValue() == 1) {
    		return;
    	}
    	Local event = new Local(ass, expr);
		event.addFilters(EType.ASSERTION);
		visitor.thread.add(event);
	}

}
