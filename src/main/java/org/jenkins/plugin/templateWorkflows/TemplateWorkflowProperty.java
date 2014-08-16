package org.jenkins.plugin.templateWorkflows;

import hudson.Extension;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.AbstractProject;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class TemplateWorkflowProperty extends JobProperty<AbstractProject<?, ?>> {

	private String templateName;
	private boolean isWorkflowCreatedJob;
	private String workflowJobName;
	private String workflowName;
	
	public boolean isWorkflowCreatedJob() {
		return isWorkflowCreatedJob;
	}

	public void setWorkflowCreatedJob(boolean isWorkflowCreatedJob) {
		this.isWorkflowCreatedJob = isWorkflowCreatedJob;
	}

	public void setTemplateName(String templateName) {
		this.templateName = templateName;
	}	
	
	public String getWorkflowJobName() {
		return workflowJobName;
	}

	public void setWorkflowJobName(String workflowJobName) {
		this.workflowJobName = workflowJobName;
	}

	public String getWorkflowName() {
		return workflowName;
	}

	public void setWorkflowName(String workflowName) {
		this.workflowName = workflowName;
	}

	public String getFullWorkFlowJobName() {
		return Jenkins.getInstance().getRootUrl() + "job/" + workflowJobName;
	}	

	@DataBoundConstructor
	public TemplateWorkflowProperty(String templateName, boolean isWorkflowCreatedJob, 
			String workflowJobName, String workflowName) {		
		this.templateName = templateName;
		this.isWorkflowCreatedJob = isWorkflowCreatedJob;
		this.workflowJobName = workflowJobName;
		this.workflowName = workflowName;
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
