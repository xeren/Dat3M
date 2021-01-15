package com.dat3m.dartagnan.parsers.program;

import com.dat3m.dartagnan.parsers.program.utils.ParsingException;
import com.dat3m.dartagnan.program.Program;

import java.io.*;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;

public class ProgramParser {

	private static final String TYPE_LITMUS_AARCH64 = "AARCH64";
	private static final String TYPE_LITMUS_PPC = "PPC";
	private static final String TYPE_LITMUS_X86 = "X86";
	private static final String TYPE_LITMUS_C = "C";

	private final Arch target;

	public ProgramParser() {
		this(Arch.NONE);
	}

	/**
	Prepares parsing a program.
	@param target
	Desired architecture to compile to.
	*/
	public ProgramParser(Arch target) {
		this.target = target;
	}

	public Program parse(File file) throws IOException {
		FileInputStream stream = new FileInputStream(file);
		String name = file.getName();
		String format = name.substring(name.lastIndexOf(".") + 1);
		ParserInterface parser = select(format, readFirstLine(file));
		CharStream charStream = CharStreams.fromStream(stream);
		Program program = parser.parse(charStream);
		stream.close();
		return program;
	}

	public Program parse(String raw, String format) {
		return select(format, raw).parse(CharStreams.fromString(raw));
	}

	private ParserInterface select(String format, String sample) {
		switch(format) {
			case "pts": return new ParserPorthos(target);
			case "bpl": return new ParserBoogie(target);
			case "litmus":
				if(startsWithIgnoreCase(sample, TYPE_LITMUS_AARCH64)) {
					return new ParserLitmusAArch64();
				} else if(startsWithIgnoreCase(sample, TYPE_LITMUS_C)) {
					return new ParserLitmusC();
				} else if(startsWithIgnoreCase(sample, TYPE_LITMUS_PPC)) {
					return new ParserLitmusPPC();
				} else if(startsWithIgnoreCase(sample, TYPE_LITMUS_X86)) {
					return new ParserLitmusX86();
				}
				throw new ParsingException("Unknown input file type");
		}
		throw new ParsingException("Unknown input file type");
	}

	private String readFirstLine(File file) throws IOException {
		FileReader fileReader = new FileReader(file);
		BufferedReader bufferedReader = new BufferedReader(fileReader);
		String line = bufferedReader.readLine();
		fileReader.close();
		return line;
	}

	private static boolean startsWithIgnoreCase(String string, String pattern) {
		return string.substring(0, pattern.length()).equalsIgnoreCase(pattern);
	}
}
