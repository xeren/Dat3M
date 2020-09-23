package com.dat3m.porthos;

import com.dat3m.dartagnan.Dartagnan;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.ProgramCache;
import com.dat3m.dartagnan.EncodeContext;
import com.dat3m.porthos.utils.options.PorthosOptions;
import com.microsoft.z3.*;
import com.microsoft.z3.enumerations.Z3_ast_print_mode;
import com.dat3m.dartagnan.parsers.program.ProgramParser;
import com.dat3m.dartagnan.parsers.cat.ParserCat;
import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.utils.Graph;
import com.dat3m.dartagnan.wmm.Wmm;
import com.dat3m.dartagnan.wmm.utils.Arch;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;

import static com.dat3m.porthos.Encodings.encodeCommonExecutions;
import static com.dat3m.porthos.Encodings.encodeReachedState;

public class Porthos {

    public static void main(String[] args) throws IOException {

        PorthosOptions options = new PorthosOptions();
        try {
            options.parse(args);
        }
        catch (Exception e){
            if(e instanceof UnsupportedOperationException){
                System.out.println(e.getMessage());
            }
            new HelpFormatter().printHelp("PORTHOS", options);
            System.exit(1);
            return;
        }

        Wmm mcmS = new ParserCat().parse(new File(options.getSourceModelFilePath()));
        Wmm mcmT = new ParserCat().parse(new File(options.getTargetModelFilePath()));

        ProgramParser programParser = new ProgramParser();
        Program pSource = programParser.parse(new File(options.getProgramFilePath()));
        Program pTarget = programParser.parse(new File(options.getProgramFilePath()));

        Context ctx = new Context();
        Solver s1 = ctx.mkSolver(ctx.mkTactic(Settings.TACTIC));
        Solver s2 = ctx.mkSolver(ctx.mkTactic(Settings.TACTIC));

        Arch source = options.getSource();
        Arch target = options.getTarget();
        Settings settings = options.getSettings();
        System.out.println("Settings: " + options.getSettings());

        EncodeContext context = new EncodeContext(ctx);
        PorthosResult result = testProgram(context, s1, s2, pSource, pTarget, source, target, mcmS, mcmT, settings);

        if(result.getIsPortable()){
            System.out.println("The program is state-portable");
            System.out.println("Iterations: " + result.getIterations());

        } else {
            System.out.println("The program is not state-portable");
            System.out.println("Iterations: " + result.getIterations());
            if(settings.getDrawGraph()) {
                ctx.setPrintMode(Z3_ast_print_mode.Z3_PRINT_SMTLIB_FULL);
                Dartagnan.drawGraph(new Graph(context, s1.getModel(), pSource, pTarget, settings.getGraphRelations()), options.getGraphFilePath());
                System.out.println("Execution graph is written to " + options.getGraphFilePath());
            }
        }

        ctx.close();
    }

    public static PorthosResult testProgram(EncodeContext context,
        Solver s1, Solver s2,
        Program pSource, Program pTarget,
        Arch source, Arch target,
        Wmm sourceWmm, Wmm targetWmm,
        Settings settings){

        Context ctx = context.context;

        pSource.unroll(settings.getBound(), 0);
        pTarget.unroll(settings.getBound(), 0);

        int nextId = pSource.compile(source, 0);
        pTarget.compile(target, nextId);

        ProgramCache cCache = new ProgramCache(pSource);
        BoolExpr sourceCF = pSource.encodeCF(context);
        BoolExpr sourceFV = pSource.encodeFinalRegisterValues(context);
        sourceWmm.encode(context, cCache, settings);
        BoolExpr sourceMM = context.allRules();

        ProgramCache cTarget = new ProgramCache(pTarget);
        s1.add(pTarget.encodeCF(context));
        s1.add(pTarget.encodeFinalRegisterValues(context));
        targetWmm.encode(context, cTarget, settings);
        s1.add(context.allRules());
        s1.add(targetWmm.consistent(context));

        s1.add(sourceCF);
        s1.add(sourceFV);
        s1.add(sourceMM);
        s1.add(sourceWmm.inconsistent(context));

        s1.add(encodeCommonExecutions(context, pTarget, pSource));

        s2.add(sourceCF);
        s2.add(sourceFV);
        s2.add(sourceMM);
        s2.add(sourceWmm.consistent(context));

        boolean isPortable = true;
        int iterations = 1;

        Status lastCheck = s1.check();

        while(lastCheck == Status.SATISFIABLE) {
            Model model = s1.getModel();
            BoolExpr reachedState = encodeReachedState(pTarget, model, ctx);
            s2.push();
            s2.add(reachedState);
            if(s2.check() == Status.UNSATISFIABLE) {
                isPortable = false;
                break;
            }
            s2.pop();
            s1.add(ctx.mkNot(reachedState));
            iterations++;
            lastCheck = s1.check();
        }
        return new PorthosResult(isPortable, iterations, pSource, pTarget);
    }
}