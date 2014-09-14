package org.jenkins.plugin.templateWorkflows;

import static org.jenkins.plugin.templateWorkflows.TemplateWorkflowUtil.deleteJob;
import static org.jenkins.plugin.templateWorkflows.TemplateWorkflowUtil.deleteJobProperty;
import static org.jenkins.plugin.templateWorkflows.TemplateWorkflowUtil.getJobStatus;
import static org.jenkins.plugin.templateWorkflows.TemplateWorkflowUtil.isWorkflowNameBeenUsed;
import static org.jenkins.plugin.templateWorkflows.TemplateWorkflowUtil.mekeWorkflowJobsConsistent;
import static org.jenkins.plugin.templateWorkflows.TemplateWorkflowUtil.notUsesJobName;
import static org.jenkins.plugin.templateWorkflows.TemplateWorkflowUtil.validateJobStatus;
import hudson.Extension;
import hudson.model.ItemGroup;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.ViewJob;
import hudson.model.AbstractProject.AbstractProjectDescriptor;

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
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import com.thoughtworks.xstream.annotations.XStreamOmitField;


public class TemplatesWorkflowJob extends ViewJob<TemplatesWorkflowJob, TemplatesWorkflowRun> implements TopLevelItem {

	private static final String ACTION_REFRESH = "FormEvent";
	private static final String ACTION_SAVE = "Save";
	private static final String ACTION_CONTINUE = "Continue";
	private static final String ACTION_DELETE = "Delete Only";
	private static final String ACTION_DELETE_FULL = "Delete Workflow and Jobs";
	
	private static final String FORM_STATE_NEW = "NEW";
	private static final String FORM_STATE_EDIT = "EDIT";

	private String formState;
	
	private TemplateWorkflowInstances templateInstances;

	private String templateName;
	private String templateInstanceName;
	private Boolean overwriteExistingJob;
	private Map<String, String> jobParameters;
	private Map<String, String> jobRelation;

	private TemplateWorkflowInstance selectedInstance;

	@XStreamOmitField
	private Map<String, List<String>> errorMessages;
	@XStreamOmitField
	private Map<String, String> templateList;

	public TemplatesWorkflowJob(final ItemGroup<TopLevelItem> itemGroup, final String name) {
		super(itemGroup, name);
	}

	@JavaScriptMethod
	public JSONObject selectInstance(final String instanceName) {

		cleanFields();

		if(instanceName == null ||  !getSafeTemplateInstances().getInstances().containsKey(instanceName)){
			this.selectedInstance = new TemplateWorkflowInstance();
			this.formState = FORM_STATE_NEW;
		}
		else {
			this.selectedInstance = getSafeTemplateInstances().getInstances().get(instanceName).clone();
			this.formState = FORM_STATE_EDIT;
			this.templateInstanceName = selectedInstance.getInstanceName();
			this.templateName = selectedInstance.getTemplateName();
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
		//Hudson.getInstance().getACL().hasPermission(Hudson.ADMINISTER)

		getErrorMap().clear();

		String postedAction = req.getParameter("action");
		if(null == postedAction){
			postedAction = ACTION_REFRESH;
		}
		
		if(ACTION_DELETE.equals(postedAction) || ACTION_DELETE_FULL.equals(postedAction)){
			Map<String, String> relatedJobs = selectedInstance.getRelatedJobs();
			getSafeTemplateInstances().getInstances().remove(selectedInstance.getInstanceName());
			for (Entry<String, String> entry : relatedJobs.entrySet()) {
				validateJobStatus(entry.getValue());
			}
			for (Entry<String, String> entry : relatedJobs.entrySet()) {
				if(ACTION_DELETE_FULL.equals(postedAction)){
					deleteJob(entry.getValue());
				}
				if(ACTION_DELETE.equals(postedAction)){
					deleteJobProperty(entry.getValue());
				}
			}
			finishAndRedirect(req, rsp);
			return;
		}
		
		JSONObject submittedForm = req.getSubmittedForm();
		
		if(ACTION_CONTINUE.equals(postedAction)){
			
			templateName = submittedForm.getString("templateName");
			templateInstanceName = submittedForm.getString("templateInstanceName");
			
			// Validate
			validateNames();
			if(!getErrorMap().isEmpty()){
				forwardBack(req, rsp);
				return;
			}			
			
			selectedInstance.setInstanceName(templateInstanceName);
			selectedInstance.setTemplateName(templateName);
			selectedInstance.setWorkFlowOwner(this.getName());

			fillJobRelation();
			fillJobParameters();
			
			this.formState = FORM_STATE_EDIT;
			forwardBack(req, rsp);
			return;			
			
		}
			
		if(ACTION_SAVE.equals(postedAction)){
			
			// Get posted data
			
			overwriteExistingJob = submittedForm.getBoolean("overwriteExistingJob");
			if( overwriteExistingJob == null ){
				overwriteExistingJob = Boolean.FALSE;
			}		
			
			for (Object key : submittedForm.keySet()) {
				if(key.toString().startsWith("relatedJob")){
					String[] keyArr = key.toString().split("#");
					String jobKey = keyArr.length == 1 ? null : keyArr[1];
					String value = submittedForm.getString(key.toString());
					if(jobRelation.containsKey(jobKey)){
						jobRelation.put(jobKey, value == null ? value : value.trim());
					}
				}
				if(key.toString().startsWith("parameter")){
					String[] keyArr = key.toString().split("#");
					String propertyKey = keyArr.length == 1 ? null : keyArr[1];
					if(jobParameters.containsKey(propertyKey)){
						jobParameters.put(propertyKey, submittedForm.getString(key.toString()));
					}
				}
			}

			validateForm(submittedForm);

			if(!getErrorMap().isEmpty()){
				forwardBack(req, rsp);
				return;
			}

			selectedInstance.setJobParameters(jobParameters);
			selectedInstance.setRelatedJobs(jobRelation);

			getSafeTemplateInstances().getInstances().put(selectedInstance.getInstanceName(), selectedInstance);

			mekeWorkflowJobsConsistent();

			finishAndRedirect(req, rsp);
			return;

		}		
						
		if(ACTION_REFRESH.equals(postedAction)){

			forwardBack(req, rsp);
			return;
		}

	}

	private void fillJobRelation() {
		jobRelation = TemplateWorkflowUtil.fillJobRelation(selectedInstance);
	}

	private void fillJobParameters() {
		jobParameters = TemplateWorkflowUtil.fillJobParameters(selectedInstance);
	}

	private void forwardBack(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
		rsp.forwardToPreviousPage(req);
	}

	private void finishAndRedirect(StaplerRequest req, StaplerResponse rsp) throws IOException {
		cleanFields();
		this.save();
		rsp.sendRedirect2(req.getContextPath() + "/job/" + getName());
	}
	
	private void validateNames() throws Exception{
		
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
		
		// Validate Workflow Name (templateInstanceName)
		if( StringUtils.isNotEmpty(templateInstanceName) ){
			if(isWorkflowNameBeenUsed(templateInstanceName)){
				addMessage("templateInstanceName", "Workflow Name is been used.");
				return;
			}
		}				
	}

	private void validateForm(JSONObject submittedForm){

		for (Entry<String, String> entry : jobRelation.entrySet()) {

			if(StringUtils.isEmpty(entry.getValue())){
				addMessage(null, String.format("We need a not used name to clone the Job '%s'.",entry.getKey()));
				continue;
			}

			if(!overwriteExistingJob){
				try {
					if( notUsesJobName(entry.getValue()) ){
						addMessage(null, String.format("The name '%s' is been used by another Job.",entry.getValue()));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			String status = getJobStatus(entry.getValue());
			if(status != null){
				addMessage(null, String.format("There are required Job '%s' with status %s.", entry.getValue(), status));
			}
			status = getJobStatus(entry.getKey());
			if(status != null){
				addMessage(null, String.format("There are required Job '%s' with status %s.", entry.getKey(), status));
			}

		}

	}

	private void cleanFields() {
		getErrorMap().clear();
		this.templateInstanceName = null;
		this.templateName = null;
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

	public Boolean getOverwriteExistingJob() {
		return overwriteExistingJob;
	}

	public Set<String> getAllWorkflowTemplateNames() {
		return TemplateWorkflowUtil.getAllWorkflowTemplateNames();
	}

	public Collection<TemplateWorkflowInstance> getTemplateInstances() {
		ArrayList<TemplateWorkflowInstance> all = null;
		all = new ArrayList<TemplateWorkflowInstance>(getSafeTemplateInstances().values());
		Collections.sort(all);
		return all;
	}

	public String getProjectDesc() {
		if (getSafeTemplateInstances().getInstances().size() == 0) {
			return "This Project does not have any Associated Workflows";
		}
		return String.format("This Project has %d Associated Workflows", getSafeTemplateInstances().getInstances().size());
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

	public TemplateWorkflowInstances getSafeTemplateInstances() {
		if(templateInstances == null){
			templateInstances = new TemplateWorkflowInstances();
		}
		return templateInstances;
	}
	
	public String getFormState() {
		return formState;
	}

	@Override
	protected void reload() {
	};

	@Exported
	public List<TemplateWorkflowInstance> getInstances() {
		ArrayList<TemplateWorkflowInstance> retorno = new ArrayList<TemplateWorkflowInstance>();		
		retorno.addAll(templateInstances.getInstances().values());		
		return retorno;
	}	
	
	/**
	 * http://localhost:8080/job/workflow/api/json?pretty=true
	 * https://wiki.jenkins-ci.org/display/JENKINS/Exposing+data+to+the+remote+API
	 */
	
}



