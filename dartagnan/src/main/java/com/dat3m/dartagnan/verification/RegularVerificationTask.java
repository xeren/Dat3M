package com.dat3m.dartagnan.verification;

import com.dat3m.dartagnan.program.Program;
import com.dat3m.dartagnan.witness.WitnessGraph;
import com.dat3m.dartagnan.wmm.Wmm;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;

@Options
public final class RegularVerificationTask extends VerificationTask {

    private RegularVerificationTask(
            Program program,
            Wmm memoryModel,
            WitnessGraph witness,
            Configuration config)
            throws InvalidConfigurationException {
        super(program, memoryModel, witness, config);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends VerificationTaskBuilder<RegularVerificationTask> {

        private Builder() {}

        @Override
        public RegularVerificationTask build(Program program, Wmm memoryModel) throws InvalidConfigurationException {
            return new RegularVerificationTask(program,memoryModel,witness,config.build());
        }
    }
}
