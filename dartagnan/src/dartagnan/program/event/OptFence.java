package dartagnan.program.event;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Z3Exception;

public class OptFence extends Fence {

    public OptFence(String name){
        super(name);
    }

    public OptFence(String name, int condLevel){
        super(name, condLevel);
    }

    public OptFence(String name, int condLevel, String atomic){
        super(name, condLevel, atomic);
    }

    public BoolExpr encodeCF(Context ctx) throws Z3Exception {
        return ctx.mkBoolConst(cfVar());
    }

    public OptFence clone() {
        return new OptFence(name, condLevel, atomic);
    }
}