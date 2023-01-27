package com.dat3m.dartagnan.parsers.program.utils;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.File;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.Files.getLastModifiedTime;
import static java.util.Arrays.asList;

public class Compilation {

    private static final Logger logger = LogManager.getLogger(Compilation.class);

    public static File compileWithClang(File file, String cflags) throws Exception {
        String output = getOutputName(file, ".ll");

        ArrayList<String> cmd = new ArrayList<>(asList("clang", "-Xclang", "-disable-O0-optnone", "-S",
                "-emit-llvm", "-g", "-gcolumn-info", "-o", output));
        // We use cflags when using the UI and fallback top CFLAGS otherwise
        cflags = cflags.isEmpty() ? System.getenv().getOrDefault("CFLAGS", "") : cflags;
        // Needed to handle more than one flag in CFLAGS
        cmd.addAll(asList(cflags.split(" ")));
        cmd.add(file.getAbsolutePath());

        return runCmd(cmd, output);
    }

    public static File applyLlvmPasses(File file) throws Exception {
        String output = getOutputName(file, "-opt.ll");
        return runCmd(asList("atomic-replace", file.getAbsolutePath(), output), output);
    }

    public static File compileWithSmack(File file, String cflags) throws Exception {
        String output = getOutputName(file, ".bpl");

        ArrayList<String> cmd = new ArrayList<String>();
        cmd.add("smack");
        // Needed to handle more than one flag in SMACK_FLAGS
        String smackFlags = System.getenv("SMACK_FLAGS");
        if (smackFlags != null && !smackFlags.isBlank()) {
            cmd.addAll(asList(smackFlags.split(" ")));
        }
        if (!cflags.isBlank()) {
            cmd.add("--clang-options=" + cflags);
        }
        cmd.addAll(asList("-bpl", output, file.getAbsolutePath()));
        return runCmd(cmd, output);
    }

    private static String getOutputName(File file, String postfix) {
        return System.getenv("DAT3M_OUTPUT") + "/" +
                file.getName().substring(0, file.getName().lastIndexOf('.')) + postfix;
    }

    private static File runCmd(List<String> cmd, String outputFileName) throws Exception {
        logger.debug(String.join(" ", cmd));
        Instant t0 = Instant.now();
        ProcessBuilder processBuilder = new ProcessBuilder(cmd);
        // "Unless the standard input and output streams are promptly written and read respectively
        // of the sub process, it may block or deadlock the sub process."
        //		https://www.developer.com/design/understanding-java-process-and-java-processbuilder/
        // The lines below take care of this.
        File log = File.createTempFile("log", null);
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(log);
        Process proc = processBuilder.start();
        proc.waitFor();
        File result = new File(outputFileName);
        if (proc.exitValue() == 1 && getLastModifiedTime(Paths.get(outputFileName)).toInstant().isBefore(t0)) {
            String errorString =  Files.asCharSource(log, Charsets.UTF_8).read();
            throw new Exception(errorString);
        }
        return result;
    }
}
