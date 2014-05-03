package org.jenkins.plugin.templateWorkflows;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class TemplateWorkflowProperty extends JobProperty<AbstractProject<?, ?>> {

	private String templateName;
	private boolean isStartingWorkflowJob;

	public boolean getIsStartingWorkflowJob() {
		return isStartingWorkflowJob;
	}

	public void setStartingWorkflowJob(boolean isStartingWorkflowJob) {
		this.isStartingWorkflowJob = isStartingWorkflowJob;
	}

	public void setTemplateName(String templateName) {
		this.templateName = templateName;
	}

	@DataBoundConstructor
	public TemplateWorkflowProperty(String templateName, boolean isStartingWorkflowJob) {
		this.templateName = templateName;
		this.isStartingWorkflowJob = isStartingWorkflowJob;
	}

	public String getTemplateName() {
		return templateName;
	}

	@Extension
	public static class DescriptorImpl extends JobPropertyDescriptor {
		@Override
		public String getDisplayName() {
			return "Template Workflow";
		}

		// TODO: its necessary? https://github.com/jenkinsci/dev-mode-plugin/blob/723b0a2441385e839fff02d1fde4b8faa405b708/src/main/java/org/jenkinsci/plugins/devmode/JobPropertyGenerator.java
		@Override
		public TemplateWorkflowProperty newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			return formData.has("template-project") ? req.bindJSON(TemplateWorkflowProperty.class, formData.getJSONObject("template-project")) : null;
		}
	}
}
