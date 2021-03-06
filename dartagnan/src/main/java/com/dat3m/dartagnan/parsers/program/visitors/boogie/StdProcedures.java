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

public class StdProcedures {

	public static List<String> STDPROCEDURES = Arrays.asList(
			"$alloc",
			"$malloc",
			"calloc",
			"malloc",
			"fopen",
			"free",
			"memcpy",
			"memset",
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
		if(name.equals("$alloc") || name.equals("$malloc") || name.equals("calloc") || name.equals("malloc")) {
			alloc(visitor, ctx);
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
		if(name.startsWith("memcpy")) {
			// TODO: Implement this
			return;			
		}
		if(name.startsWith("memset")) {
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
			size = ((ExprInterface)ctx.call_params().exprs().expr(0).accept(visitor)).reduce().getValue();			
		} catch (Exception e) {
			String tmp = ctx.call_params().getText();
			tmp = tmp.contains(",") ? tmp.substring(0, tmp.indexOf(',')) : tmp.substring(0, tmp.indexOf(')')); 
			tmp = tmp.substring(tmp.lastIndexOf('(')+1);						
			size = Integer.parseInt(tmp);			
		}
		List<IConst> values = Collections.nCopies(size, new IConst(0));
		String ptr = ctx.call_params().Ident(0).getText();
		Register start = visitor.programBuilder.getOrCreateRegister(visitor.threadCount, visitor.currentScope.getID() + ":" + ptr);
		// Several threads can use the same pointer name but when using addDeclarationArray, 
		// the name should be unique, thus we add the process identifier.
		visitor.programBuilder.addDeclarationArray(visitor.currentScope.getID() + ":" + ptr, values);
		Address adds = visitor.programBuilder.getPointer(visitor.currentScope.getID() + ":" + ptr);
		visitor.programBuilder.addChild(visitor.threadCount, new Local(start, adds));
	}
}
