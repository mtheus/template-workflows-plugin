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
import java.util.Map.Entry;
import java.util.Set;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.bind.JavaScriptMethod;

import com.thoughtworks.xstream.annotations.XStreamOmitField;

public class TemplatesWorkflowJob extends ViewJob<TemplatesWorkflowJob, TemplateswWorkflowRun> implements TopLevelItem {

	private static final String ACTION_REFRESH = "FormEvent";
	private static final String ACTION_SAVE = "Save";
	private static final String ACTION_DELETE = "Delete";

	private TemplateWorkflowInstances templateInstances;
	
	private String templateName;
	private String templateInstanceName;
	private Boolean useTemplatePrefix;
	private Boolean useExistingJob;	
	private Map<String, String> jobParameters;
	private Map<String, String> jobRelation;
	
	private TemplateWorkflowInstance selectedInstance;
	
	@SuppressWarnings("rawtypes")
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
		
		cleanFields();
		
		if(instanceName == null || templateInstances == null || templateInstances.getInstances() == null
				|| !templateInstances.getInstances().containsKey(instanceName)){
			this.selectedInstance = new TemplateWorkflowInstance();			
		}
		else {			
			this.selectedInstance = templateInstances.getInstances().get(instanceName);
			
			this.templateInstanceName = selectedInstance.getInstanceName();
			this.templateName = selectedInstance.getTemplateName();
			fillJobRelation();
			fillJobParameters();
		}
		
		return new JSONObject();
	}


	public synchronized void doConfigWorkflow(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, FormException {

		//TODO: There are some security issues? 
		
		getErrorMap().clear();
		
		String postedAction = req.getParameter("action");
		if(null == postedAction){
			postedAction = ACTION_REFRESH;
		}

		JSONObject submittedForm = req.getSubmittedForm();
		templateName = submittedForm.getString("templateName");
		templateInstanceName = submittedForm.getString("templateInstanceName");
		useTemplatePrefix = submittedForm.getBoolean("useTemplatePrefix");
		useExistingJob = submittedForm.getBoolean("useExistingJob");
		
		for (Object key : submittedForm.keySet()) {
			if(key.toString().startsWith("relatedJob")){
				String[] keyArr = key.toString().split("#");
				String jobKey = keyArr.length == 1 ? null : keyArr[1];
				jobRelation.put(jobKey, submittedForm.getString(key.toString()));				
			}
			if(key.toString().startsWith("parameter")){
				String[] keyArr = key.toString().split("#");
				String jobKey = keyArr.length == 1 ? null : keyArr[1];
				jobParameters.put(jobKey, submittedForm.getString(key.toString()));				
			}			
		}
		
		
		if(ACTION_DELETE.equals(postedAction)){			
			templateInstances.getInstances().remove(selectedInstance.getInstanceName());			
			finishAndRedirect(req, rsp);
		}
		else if(ACTION_REFRESH.equals(postedAction)){
			
			// Fill de maps
			fillJobRelation();
			fillJobParameters();
			forwardBack(req, rsp);
		}
		else if(ACTION_SAVE.equals(postedAction)){
			
			validateForm(submittedForm);
			
			if(!getErrorMap().isEmpty()){
				forwardBack(req, rsp);
			}
			else {
				
				if(selectedInstance.getInstanceName() == null){ // is new
					selectedInstance.setInstanceName( templateInstanceName );
				} else if( !selectedInstance.getInstanceName().equals(templateInstanceName)){ // renamed
					// todo: check name conflict
					addMessage(null, "Rename Workflow is not supported yet!");
					forwardBack(req, rsp);
					return;
				}
				
				// TODO: copy values to selectedInstance.
				
				
				templateInstances.getInstances().put(selectedInstance.getInstanceName(), selectedInstance); 				
				finishAndRedirect(req, rsp);
			}
			
		}

	}

	
	@SuppressWarnings("rawtypes")
	private void fillJobRelation() {

		List<Job> reletedJob = TemplateWorkflowUtil.getRelatedJobs(templateName);
		Map<String, String> newJobRelation = getJobRelation();
		newJobRelation.clear();
		for (Job job : reletedJob) {
			newJobRelation.put(job.getName(), jobRelation.get(job.getName()));
		}		
		jobRelation = newJobRelation;
		
	}
	
	private void fillJobParameters() {
		
		Map<String, String> reletedProperties = TemplateWorkflowUtil.getTemplateParamaters(templateName);
		Map<String, String> newJobParameters = getJobParameters();
		newJobParameters.clear();
		for (Entry<String, String> entry : reletedProperties.entrySet()) {
			newJobParameters.put(entry.getKey(), jobRelation.get(entry.getKey()));
		}		
		jobParameters = newJobParameters;
		
	}

	private void forwardBack(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
		rsp.forwardToPreviousPage(req);
	}

	private void finishAndRedirect(StaplerRequest req, StaplerResponse rsp) throws IOException {
		cleanFields();
		this.save();
		rsp.sendRedirect2(req.getContextPath() + "/job/" + getName());
	}
	
	private void validateForm(JSONObject submittedForm){
				 		
		this.templateInstanceName = StringUtils.trimToEmpty(templateInstanceName);
		if(StringUtils.isEmpty(templateInstanceName)){
			addMessage("templateInstanceName", "Workflow Name is required");
		}
		
	}
	
	private void cleanFields() {
		getErrorMap().clear();
		this.templateInstanceName = null;
		this.templateName = null;		
		this.useTemplatePrefix = null;
		this.useExistingJob = null ;	
		this.jobParameters = null;
		this.jobRelation = null;
		
	}
	
	public Map<String, String> getJobRelation() {
		if(jobRelation == null){
			jobRelation = new HashMap<String, String>();
		}
		return jobRelation;
	}
	
	public Map<String, String> getJobParameters() {
		if(jobParameters == null){
			jobParameters = new HashMap<String, String>();
		}
		return jobParameters;
	}	

	public String getTemplateName() {
		return this.templateName;
	}

	public String getTemplateInstanceName() {
		return this.templateInstanceName;
	}

	public Boolean getUseTemplatePrefix() {
		return useTemplatePrefix;
	}

	public Boolean getUseExistingJob() {
		return useExistingJob;
	}

	public Set<String> getAllWorkflowTemplateNames() {
		return TemplateWorkflowUtil.getAllWorkflowTemplateNames();
	}
	
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

	public List<String> getErrorMessages() {
		return getErrorMessages(null);
	}
	
	public List<String> getErrorMessages(String fieldId) {
		getErrorMap();
		if(!errorMessages.containsKey(fieldId)){
			return new ArrayList<String>();
		}
		return errorMessages.get(fieldId);
	}

	private Map<String, List<String>> getErrorMap() {
		if (errorMessages == null) {
			errorMessages = new HashMap<String,List<String>>();
		}
		return errorMessages;
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

		@SuppressWarnings("unused")
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

		// public FormValidation doCheckCount(@QueryParameter String value) {
		// return FormValidation.validatePositiveInteger(value);
		// }

	}

	@Override
	protected void reload() {
	};
	
}
