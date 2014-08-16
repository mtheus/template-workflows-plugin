package org.jenkins.plugin.templateWorkflows;

import static org.jenkins.plugin.templateWorkflows.TemplateWorkflowUtil.deleteJob;
import static org.jenkins.plugin.templateWorkflows.TemplateWorkflowUtil.deleteJobProperty;
import static org.jenkins.plugin.templateWorkflows.TemplateWorkflowUtil.getJobStatus;
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
		}
		else {
			this.selectedInstance = getSafeTemplateInstances().getInstances().get(instanceName).clone();

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
			getSafeTemplateInstances().getInstances().remove(selectedInstance.getInstanceName());
			for (Entry<String, String> entry : jobRelation.entrySet()) {
				validateJobStatus(entry.getValue());
				if(ACTION_DELETE_FULL.equals(postedAction)){
					deleteJob(entry.getValue());
				}
				if(ACTION_DELETE.equals(postedAction)){
					deleteJobProperty(entry.getValue());
				}
			}
			finishAndRedirect(req, rsp);
		}
		else if(ACTION_REFRESH.equals(postedAction)){

			//TODO: Validar notUsesWorkflowName(selectedInstance.getInstanceName())
			selectedInstance.setInstanceName( templateInstanceName );
			selectedInstance.setTemplateName(templateName);
			selectedInstance.setUseTemplatePrefix(useTemplatePrefix);
			selectedInstance.setWorkFlowOwner(this.getName());

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
				selectedInstance.setWorkFlowOwner(this.getName());
				selectedInstance.setJobParameters(jobParameters);
				selectedInstance.setRelatedJobs(jobRelation);

				getSafeTemplateInstances().getInstances().put(selectedInstance.getInstanceName(), selectedInstance);

				mekeWorkflowJobsConsistent();

				finishAndRedirect(req, rsp);
			}

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

	@Override
	protected void reload() {
	};

}
