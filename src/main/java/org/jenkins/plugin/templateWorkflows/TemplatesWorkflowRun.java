package org.jenkins.plugin.templateWorkflows;

import hudson.model.Run;

import java.io.File;
import java.io.IOException;

public class TemplatesWorkflowRun extends Run<TemplatesWorkflowJob, TemplatesWorkflowRun> {

	public TemplatesWorkflowRun(TemplatesWorkflowJob project) throws IOException {
		super(project);
	}

	public TemplatesWorkflowRun(TemplatesWorkflowJob project, File buildDir) throws IOException {
		super(project, buildDir);
	}

}
