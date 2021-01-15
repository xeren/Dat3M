package com.dat3m.ui.utils;

import com.dat3m.dartagnan.parsers.program.Arch;
import com.dat3m.dartagnan.utils.Settings;
import com.dat3m.dartagnan.wmm.utils.Mode;
import com.dat3m.ui.options.utils.Task;

public class UiOptions {

	private final Task task;
	private Arch source;
	private Arch target;
	private final Settings settings;
	private final boolean useCore;


	public UiOptions(Task task, Arch source, Arch target, Settings settings, boolean useCore) {
		this.task = task;
		this.source = source;
		this.target = target;
		this.settings = settings;
		this.useCore = useCore;
	}
	
	public Task getTask() {
		return task;
	}

	public Arch getSource(){
		return source;
	}

	public Arch getTarget(){
		return target;
	}

	public Settings getSettings(){
		return settings;
	}

	public boolean getUseCore() {
		return useCore;
	}

	public boolean validate(){
		if(task == Task.PORTABILITY && source == null){
			Utils.showError("Source settings must be specified for portability analysis");
			return false;
		}
		if(settings.getDrawGraph() && settings.getMode().equals(Mode.KNASTER)){
			Utils.showError("Execution graph is not available in Knaster-Tarski encoding mode");
			return false;
		}
		return true;
	}
}
