package org.jenkins.plugin.templateWorkflows;

import hudson.Extension;
import hudson.model.ItemGroup;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.ViewJob;
import hudson.model.AbstractProject.AbstractProjectDescriptor;
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
import java.util.TreeMap;

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
	private static final String ACTION_DELETE = "Delete Only";
	private static final String ACTION_DELETE_FULL = "Delete Workflow and Jobs";

	private TemplateWorkflowInstances templateInstances;
	
	private String templateName;
	private String templateInstanceName;
	private Boolean useTemplatePrefix;
	private Boolean overwriteExistingJob;	
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
			this.useTemplatePrefix = selectedInstance.getUseTemplatePrefix();
			this.jobParameters =  selectedInstance.getJobParameters();
			this.jobRelation = selectedInstance.getRelatedJobs();
			this.overwriteExistingJob = Boolean.FALSE;
			fillJobRelation();
			fillJobParameters();
		}
		
		return new JSONObject();
	}


	public synchronized void doConfigWorkflow(StaplerRequest req, StaplerResponse rsp) throws Exception {

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
		if( useTemplatePrefix == null ){
			useTemplatePrefix = Boolean.TRUE;
		}
		
		overwriteExistingJob = submittedForm.getBoolean("overwriteExistingJob");
		if( overwriteExistingJob == null ){
			overwriteExistingJob = Boolean.FALSE;
		}
		
		for (Object key : submittedForm.keySet()) {
			if(key.toString().startsWith("relatedJob")){
				String[] keyArr = key.toString().split("#");
				String jobKey = keyArr.length == 1 ? null : keyArr[1];
				String value = submittedForm.getString(key.toString());
				jobRelation.put(jobKey, value == null ? value : value.trim());				
			}
			if(key.toString().startsWith("parameter")){
				String[] keyArr = key.toString().split("#");
				String jobKey = keyArr.length == 1 ? null : keyArr[1];
				jobParameters.put(jobKey, submittedForm.getString(key.toString()));				
			}			
		}
				
		if(ACTION_DELETE.equals(postedAction) || ACTION_DELETE_FULL.equals(postedAction)){
			templateInstances.getInstances().remove(selectedInstance.getInstanceName());
			for (Entry<String, String> entry : jobRelation.entrySet()) {
				if(ACTION_DELETE_FULL.equals(postedAction)){
					TemplateWorkflowUtil.deleteJob(entry.getValue());
				}
				if(ACTION_DELETE.equals(postedAction)){
					TemplateWorkflowUtil.deleteJobProperty(entry.getValue());
				}
			}				
			finishAndRedirect(req, rsp);
		}
		else if(ACTION_REFRESH.equals(postedAction)){
			
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
					//TODO: Validar notUsesWorkflowName(selectedInstance.getInstanceName())
					selectedInstance.setInstanceName( templateInstanceName );
				} else if( !selectedInstance.getInstanceName().equals(templateInstanceName)){ // renamed
					//TODO: Validar notUsesWorkflowName(selectedInstance.getInstanceName())
					// TODO: check name conflict
					addMessage(null, "Rename Workflow is not supported yet!");
					forwardBack(req, rsp);
					return;
				}
				
				selectedInstance.setTemplateName(templateName);
				selectedInstance.setUseTemplatePrefix(useTemplatePrefix);
				selectedInstance.setJobParameters(jobParameters);
				selectedInstance.setRelatedJobs(jobRelation);
				
				if(templateInstances == null){
					templateInstances = new TemplateWorkflowInstances();
				}
				
				templateInstances.getInstances().put(selectedInstance.getInstanceName(), selectedInstance);
				String workflowName = selectedInstance.getInstanceName();
				
				// do the magic				
				for (Entry<String, String> relatedJobEntry : jobRelation.entrySet()) {
					TemplateWorkflowUtil.createOrUpdate(this.getName(), workflowName, 
							relatedJobEntry.getKey(), relatedJobEntry.getValue(), 
							jobParameters, jobRelation);					
				}
				
				finishAndRedirect(req, rsp);
			}
			
		}

	}

	
	@SuppressWarnings("rawtypes")
	private void fillJobRelation() {

		List<Job> reletedJob = TemplateWorkflowUtil.getRelatedJobs(templateName);
		Map<String, String> newJobRelation = new TreeMap<String, String>();
		for (Job job : reletedJob) {
			if(getJobRelation().containsKey(job.getName())){
				newJobRelation.put(job.getName(), getJobRelation().get(job.getName()));
			} else if(useTemplatePrefix != null && useTemplatePrefix){
				newJobRelation.put(job.getName(), templateInstanceName + "-" + job.getName());
			} else {
				newJobRelation.put(job.getName(), null);				
			}
		}
		jobRelation = newJobRelation;
		
	}
	
	private void fillJobParameters() {
		
		Map<String, String> reletedProperties = TemplateWorkflowUtil.getTemplateParamaters(templateName);
		Map<String, String> newJobParameters = new TreeMap<String, String>();
		for (Entry<String, String> entry : reletedProperties.entrySet()) {
			newJobParameters.put(entry.getKey(), getJobParameters().get(entry.getKey()));
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
			return;
		}
		
		this.templateName = StringUtils.trimToEmpty(templateName);
		if(StringUtils.isEmpty(templateName)){
			addMessage("templateName", "Template Name is required");
			return;
		}
		
		for (Entry<String, String> entry : jobRelation.entrySet()) {
			
			if(StringUtils.isEmpty(entry.getValue())){
				addMessage(null, String.format("We need a not used name to clone the Job '%s'.",entry.getKey()));
				continue;
			}
			
			if(!overwriteExistingJob){
				try {
					if( TemplateWorkflowUtil.notUsesJobName(entry.getValue()) ){
						addMessage(null, String.format("The name '%s' is been used by another Job.",entry.getValue()));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			String status = TemplateWorkflowUtil.getJobStatus(entry.getValue());
			if(status != null){
				addMessage(null, String.format("There are required Job '%s' with status %s.", entry.getValue(), status));
			}
			status = TemplateWorkflowUtil.getJobStatus(entry.getKey());
			if(status != null){
				addMessage(null, String.format("There are required Job '%s' with status %s.", entry.getKey(), status));
			}
			
		}
		
	}
	
	private void cleanFields() {
		getErrorMap().clear();
		this.templateInstanceName = null;
		this.templateName = null;		
		this.useTemplatePrefix = Boolean.TRUE;
		this.overwriteExistingJob = Boolean.FALSE;	
		this.jobParameters = null;
		this.jobRelation = null;
		
	}
	
	public Map<String, String> getJobRelation() {
		if(jobRelation == null){
			jobRelation = new TreeMap<String, String>();
		}
		return jobRelation;
	}
	
	public Map<String, String> getJobParameters() {
		if(jobParameters == null){
			jobParameters = new TreeMap<String, String>();
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

	public Boolean getOverwriteExistingJob() {
		return overwriteExistingJob;
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
	
	public boolean containsJob(String job) {
		return Jenkins.getInstance().getJobNames().contains(job);
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
		}

	}

	@Override
	protected void reload() {
	};
	
}
