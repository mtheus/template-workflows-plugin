package org.jenkins.plugin.templateWorkflows;

import java.util.HashMap;
import java.util.Map;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import com.google.common.collect.Maps;

@ExportedBean
public class TemplateWorkflowInstance implements Comparable<TemplateWorkflowInstance> {

	private String instanceName;
	private String templateName;
	private String workFlowOwner;
	private Map<String, String> jobParameters;
	private Map<String, String> relatedJobs;
	private Boolean useTemplatePrefix;

	public TemplateWorkflowInstance() {
		this.jobParameters = new HashMap<String, String>();
		this.relatedJobs = new HashMap<String, String>();
		this.useTemplatePrefix = Boolean.TRUE;
	}

	public TemplateWorkflowInstance(final String templateName, final String instanceName, final String workFlowOwner) {
		this.templateName = templateName;
		this.instanceName = instanceName;
		this.workFlowOwner = workFlowOwner;
		this.jobParameters = new HashMap<String, String>();
		this.relatedJobs = new HashMap<String, String>();
		this.useTemplatePrefix = Boolean.TRUE;
	}

	public TemplateWorkflowInstance clone(){
		TemplateWorkflowInstance clone = new TemplateWorkflowInstance();
		clone.templateName = this.templateName;
		clone.instanceName = this.instanceName;
		clone.workFlowOwner = this.workFlowOwner;
		clone.jobParameters = Maps.newHashMap(this.jobParameters);
		clone.relatedJobs =  Maps.newHashMap(this.relatedJobs);
		clone.useTemplatePrefix = this.useTemplatePrefix;
		return clone;
	}

	@Exported
	public String getInstanceName() {
		return this.instanceName;
	}

	@Exported
	public String getTemplateName() {
		return this.templateName;
	}

	@Exported
	public String getWorkFlowOwner() {
		return workFlowOwner;
	}

	@Exported
	public int getRelatedJobsSize() {
		if( this.relatedJobs == null ) {
			return 0;
		}
		return this.relatedJobs.size();
	}

	@Exported
	public Map<String, String> getRelatedJobs() {
		return this.relatedJobs;
	}

	@Exported
	public Map<String, String> getJobParameters() {
		return this.jobParameters;
	}

	public void setInstanceName(String instanceName) {
		this.instanceName = instanceName;
	}

	public void setTemplateName(String templateName) {
		this.templateName = templateName;
	}

	public void setJobParameters(Map<String, String> jobParameters) {
		this.jobParameters = jobParameters;
	}

	public void setRelatedJobs(Map<String, String> relatedJobs) {
		this.relatedJobs = relatedJobs;
	}

	public void setWorkFlowOwner(String workFlowOwner) {
		this.workFlowOwner = workFlowOwner;
	}

	public Boolean getUseTemplatePrefix() {
		return useTemplatePrefix;
	}

	public void setUseTemplatePrefix(Boolean useTemplatePrefix) {
		this.useTemplatePrefix = useTemplatePrefix;
	}

	public int compareTo(final TemplateWorkflowInstance o) {
		return this.instanceName.compareTo(o.instanceName);
	}

}
