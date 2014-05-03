package org.jenkins.plugin.templateWorkflows;

import hudson.Extension;
import hudson.model.ItemGroup;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.ViewJob;
import hudson.model.AbstractProject.AbstractProjectDescriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Job;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.bind.JavaScriptMethod;

import com.thoughtworks.xstream.annotations.XStreamOmitField;

public class TemplatesWorkflowJob extends ViewJob<TemplatesWorkflowJob, TemplateswWorkflowRun> implements TopLevelItem {

	private TemplateWorkflowInstances templateInstances;
	
	private String templateName;
	private String templateInstanceName;
	private Boolean useTemplatePrefix;
	private Boolean useExistingJob;	
	private Map<String, String> jobParameters;
	private Map<String, String> jobRelation;
	
	@Deprecated 
	private static List<Job> relatedJobs;
	@XStreamOmitField
	private Map<String, List<String>> errorMessages;
	@XStreamOmitField
	private Map<String, String> templateList;

	public TemplatesWorkflowJob(final ItemGroup<TopLevelItem> itemGroup, final String name) {
		super(itemGroup, name);
		// TODO: create a compatibility code
		if(relatedJobs != null){
			relatedJobs.clear();
		}
	}
	
	@JavaScriptMethod
	public JSONObject selectInstance(final String instanceName) {
		this.templateInstanceName = instanceName;
		return new JSONObject();
	}

//	public String getTemplateName() {
//		return this.templateName;
//	}
//
//	public String getTemplateInstanceName() {
//		return this.templateInstanceName;
//	}

	public Collection<TemplateWorkflowInstance> getTemplateInstances() {

		ArrayList<TemplateWorkflowInstance> all = null;

		if (this.templateInstances == null) {
			all = new ArrayList<TemplateWorkflowInstance>();
		}

		all = new ArrayList<TemplateWorkflowInstance>(this.templateInstances.values());
		Collections.sort(all);

		return all;
	}

	public String getProjectDesc() {
		if (this.templateInstances == null || this.templateInstances.getInstances() == null || this.templateInstances.getInstances().size() == 0) {
			return "This Project does not have any Associated Workflows";
		}
		return "This Project has " + this.templateInstances.getInstances().size() + " Associated Workflows";
	}

	@Override
	public Jenkins getParent() {
		return Jenkins.getInstance();
	}

	@Override
	public synchronized void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, FormException {

		if( !req.getReferer().endsWith("/configureWorkflow") ){
			super.doConfigSubmit(req, rsp);
			return;
		}
		
		getErrorMessages().clear();

		JSONObject submittedForm = req.getSubmittedForm();
		this.templateName = "teste2c";
		// super.doConfigSubmit(req, rsp);
		// submit(req, rsp);

		addMessage(null, "teste");

		rsp.forwardToPreviousPage(req);
	}


	public List<String> getErrorMessages() {
		return getErrorMessages(null);
	}
	
	public List<String> getErrorMessages(String fieldId) {
		if (errorMessages == null) {
			errorMessages = new HashMap<String,List<String>>();
		}
		if(!errorMessages.containsKey(fieldId)){
			return new ArrayList<String>();
		}
		return errorMessages.get(fieldId);
	}
	
	private void addMessage(String fieldId, String message){
		List<String> messageList; 
		if(!errorMessages.containsKey(fieldId)){
			messageList = new ArrayList<String>();
			errorMessages.put(fieldId, messageList);
		}
		else {
			messageList = errorMessages.get(fieldId);
		}
		messageList.add(message);
	}	
	
	public TopLevelItemDescriptor getDescriptor() {
		return new DescriptorImpl(this);
	}

	@Extension
	public static final class DescriptorImpl extends AbstractProjectDescriptor {

		private TemplatesWorkflowJob templatesWorkflowJob;

		// annotated classes must have a public no-argument constructor
		public DescriptorImpl() {
		}

		public DescriptorImpl(TemplatesWorkflowJob templatesWorkflowJob) {
			this.templatesWorkflowJob = templatesWorkflowJob;
		}

		@Override
		public String getDisplayName() {
			return "Template Workflow Job";
		}

		@Override
		public String getConfigPage() {
			return super.getConfigPage();
		}

		@SuppressWarnings("rawtypes")
		@Override
		public TopLevelItem newInstance(final ItemGroup paramItemGroup, final String paramString) {
			return new TemplatesWorkflowJob(Jenkins.getInstance(), paramString);
			//return templatesWorkflowJob;
		}
/*
		public ListBoxModel doFillTemplateNameItems(@QueryParameter String templateName) {
			if (this.templatesWorkflowJob != null)
				this.templatesWorkflowJob.templateName = templateName;
			ListBoxModel m = new ListBoxModel();
			m.add("Teste2a", "teste2a");
			m.add("Teste2b", "teste2b");
			m.add("Teste2c", "teste2c");
			return m;
		}
*/
		// public ListBoxModel doFillTemplateJobsNameItems(@QueryParameter
		// String templateName) {
		// ListBoxModel m = new ListBoxModel();
		// m.add("Teste1a", "teste1a");
		// return m;
		// }

		// public FormValidation doCheckCount(@QueryParameter String value) {
		// return FormValidation.validatePositiveInteger(value);
		// }

	}

	@Override
	protected void reload() {
		System.out.println("reload");
	};
}
