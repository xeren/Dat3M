package com.dat3m.dartagnan.utils.options;

import java.util.Set;

import org.apache.commons.cli.*;

public class DartagnanOptions extends BaseOptions {

    protected Set<String> supportedFormats = Set.of("litmus", "bpl");
    protected Integer cegar;
	
    public DartagnanOptions(){
        super();
        Option catOption = new Option("cat", true,
                "Path to the CAT file");
        catOption.setRequired(true);
        addOption(catOption);

        Option cegarOption = new Option("cegar", true,
                "Use CEGAR");
        addOption(cegarOption);
    }
    
    public void parse(String[] args) throws ParseException, RuntimeException {
    	super.parse(args);
        if(supportedFormats.stream().map(f -> programFilePath.endsWith(f)). allMatch(b -> b.equals(false))) {
            throw new RuntimeException("Unrecognized program format");
        }
        CommandLine cmd = new DefaultParser().parse(this, args);
        if(cmd.hasOption("cegar")) {
            cegar = Integer.parseInt(cmd.getOptionValue("cegar")) - 1;        	
        }
    }
    
    public Integer getCegar(){
        return cegar;
    }
}
