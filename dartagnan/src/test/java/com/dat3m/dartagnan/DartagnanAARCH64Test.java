package com.dat3m.dartagnan;

import com.dat3m.dartagnan.parsers.program.Arch;
import com.dat3m.dartagnan.utils.Result;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.Wmm;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;

@RunWith(Parameterized.class)
public class DartagnanAARCH64Test extends AbstractDartagnanTest {

    @Parameterized.Parameters(name = "{index}: {0} {4}")
    public static Iterable<Object[]> data() throws IOException {
        return buildParameters("litmus/AARCH64/", "cat/aarch64.cat", Arch.ARM8);
    }

    public DartagnanAARCH64Test(String path, Result expected, Arch target, Wmm wmm, Settings settings) {
        super(path, expected, target, wmm, settings);
    }
}
