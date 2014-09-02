package org.jenkins.plugin.templateWorkflows;

import hudson.model.Item;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.Job;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.stream.StreamSource;

import jenkins.model.Jenkins;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Sets;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class TemplateWorkflowUtil {

	private static final Logger LOGGER = Logger.getLogger(TemplateWorkflowUtil.class.getName());

	public static void mekeWorkflowJobsConsistent() throws Exception {

		//all workflow jobs
		Map<String, TemplatesWorkflowJob> workflowsJobs = findAllTemplateWorkflowJob();

		Set<String> allTemplateNames = getAllWorkflowTemplateNames();

		// find all workflows and update
		Map<String, TemplateWorkflowInstance> workflowsIntances = findAllTemplateWorkflowInstance();
		for (Entry<String, TemplateWorkflowInstance> templateWorkflowInstanceEntry : workflowsIntances.entrySet()) {
			updateJobsFromTemplateWorkflowInstance(templateWorkflowInstanceEntry.getValue());
		}

		// find all clonedjobs and if inconsistent with their workflow, delete
		List<Job> clonedJobs = findAllClonedJobs();
		for (Job clonedJob : clonedJobs) {

			TemplateWorkflowProperty templateWorkflowProperty = getTemplateWorkflowProperty(clonedJob);

			// deleted instance
			if(!workflowsIntances.containsKey(templateWorkflowProperty.getWorkflowName())){
				LOGGER.info(String.format("JOB %s deleted. (1)", clonedJob.getName()));
				clonedJob.delete();
				continue;
			}

			// deleted workflow job
			if(!workflowsJobs.containsKey(templateWorkflowProperty.getWorkflowJobName())){
				LOGGER.info(String.format("JOB %s deleted. (2)", clonedJob.getName()));
				clonedJob.delete();
				continue;
			}

			// deleted template group
			if(!allTemplateNames.contains(templateWorkflowProperty.getTemplateName())){
				LOGGER.info(String.format("JOB %s deleted. (3)", clonedJob.getName()));
				clonedJob.delete();
				continue;
			}

			// deleted worflow job to copy
			TemplateWorkflowInstance workflowInstance = workflowsIntances.get(templateWorkflowProperty.getWorkflowName());
			if(!workflowInstance.getRelatedJobs().containsValue(clonedJob.getName())){
				LOGGER.info(String.format("JOB %s deleted. (4)", clonedJob.getName()));
				clonedJob.delete();
				continue;
			}

		}

	}

	public static void updateJobsFromTemplateWorkflowInstance(TemplateWorkflowInstance instance) throws Exception {

		// do the magic
		for (Entry<String, String> relatedJobEntry : instance.getRelatedJobs().entrySet()) {

			/*
			 * If it is a fired change event, adding a new job in the relation, but the default
			 * job name property is disabled, the name could be null.
			 * It'll be ignored and the user need to open the interface to update manually.
			 */
			if(relatedJobEntry.getValue() != null){
				createOrUpdate(instance.getWorkFlowOwner(), instance.getInstanceName(), instance.getTemplateName(),
					relatedJobEntry.getKey(), relatedJobEntry.getValue(),
					instance.getJobParameters(), instance.getRelatedJobs());
			}
		}

	}

	public static Map<String, String> fillJobRelation(TemplateWorkflowInstance instance){

		List<Job> reletedJob = getRelatedJobs(instance.getTemplateName());
		Map<String, String> newJobRelation = new TreeMap<String, String>();
		for (Job job : reletedJob) {
			if(instance.getRelatedJobs().containsKey(job.getName())){
				newJobRelation.put(job.getName(), instance.getRelatedJobs().get(job.getName()));
			} else if(instance.getUseTemplatePrefix() != null && instance.getUseTemplatePrefix()){
				newJobRelation.put(job.getName(), instance.getInstanceName() + "-" + fixTagWords(job.getName()));
			} else {
				newJobRelation.put(job.getName(), null);
			}
		}
		return newJobRelation;

	}

	private static String fixTagWords(String name) {
		// FIXME: use regex and put in a global jenkins configuration.
		String fixedName = name;
		fixedName = fixedName.replace("template", "");
		fixedName = fixedName.replace("example", "");
		fixedName = fixedName.replace("--", "-");
		fixedName = fixedName.replace("__", "_");
		fixedName = fixedName.startsWith("-") ? fixedName.substring(1) : fixedName;
		return fixedName;
	}

	public static Map<String, String> fillJobParameters(TemplateWorkflowInstance instance){

		Map<String, String> reletedProperties = getTemplateParamaters(instance.getTemplateName());
		Map<String, String> newJobParameters = new TreeMap<String, String>();
		for (Entry<String, String> entry : reletedProperties.entrySet()) {
			newJobParameters.put(entry.getKey(), instance.getJobParameters().get(entry.getKey()));
		}
		return newJobParameters;
	}

	public static void updateTemplateWorkflowInstance(List<TemplateWorkflowInstance> instances) throws Exception {
		Exception exp = null;
		for (TemplateWorkflowInstance instance : instances) {
			try {
				updateTemplateWorkflowInstance(instance);
			} catch (Exception e) {
				exp = e;
			}
		}
		if(exp != null){
			throw exp;
		}
	}


	public static void updateTemplateWorkflowInstance(TemplateWorkflowInstance instance) throws Exception {

		TemplatesWorkflowJob workFlowJob = findTemplatesWorkflowJobByName(instance.getWorkFlowOwner());
		if(null == workFlowJob){
			throw new Exception(String.format("Workflow Job %s does not exists.",instance.getWorkFlowOwner()));
		}

		workFlowJob.getSafeTemplateInstances().getInstances().put(instance.getInstanceName(), instance);
		workFlowJob.save();
		LOGGER.info(String.format("JOB %s, Worflow %s: saved.", workFlowJob.getName(), instance.getInstanceName()));

	}

	public static Set<String> getAllWorkflowTemplateNames() {

		Set<String> allWorkflowTemplateNames = new LinkedHashSet<String>();

		List<Item> allItems = Jenkins.getInstance().getAllItems();
		for (Item i : allItems) {
			Collection<? extends Job> allJobs = i.getAllJobs();
			for (Job job : allJobs) {
				if(itIsATemplateJob(job)){
					TemplateWorkflowProperty templateWorkflowProperty = getTemplateWorkflowProperty(job);
					for (String tName : templateWorkflowProperty.getTemplateName().split(",")) {
						allWorkflowTemplateNames.add(tName.trim());
					}
				}
			}
		}
		return allWorkflowTemplateNames;
	}

	public static List<Job> getRelatedJobs(final String templateName) {
		List<Job> relatedJobs = new ArrayList<Job>();
		List<Item> allItems = Jenkins.getInstance().getAllItems();
		for (Item jenkinsItem : allItems) {
			Collection<? extends Job> allJobs = jenkinsItem.getAllJobs();
			for (Job job : allJobs) {
				if (itIsATemplateJob(job)) {
					TemplateWorkflowProperty templateWorkflowProperty = getTemplateWorkflowProperty(job);
					for (String tName : templateWorkflowProperty.getTemplateName().split(",")) {
						if (templateName != null && templateName.equalsIgnoreCase(tName.trim())) {
							relatedJobs.add(job);
						}
					}
				}
			}
		}
		return relatedJobs;
	}

	public static Map<String, String> getTemplateParamaters(final String templateName) {

		try {

			List<Job> relatedJobs = getRelatedJobs(templateName);

			Pattern pattern = Pattern.compile("@@(.*?)@@");

			Map<String, String> jobParameters = new HashMap<String, String>();
			for (Job job : relatedJobs) {
				List<String> lines;
				lines = FileUtils.readLines(job.getConfigFile().getFile());
				for (String line : lines) {
					Matcher matcher = pattern.matcher(line);
					while (matcher.find()) {
						jobParameters.put(matcher.group(1), null);
					}
				}
			}

			Map<String, String> map = new LinkedHashMap<String, String>();
			List<String> paramsName = new ArrayList<String>(jobParameters.keySet());
			Collections.sort(paramsName);
			for (String k : paramsName) {
				map.put(k, jobParameters.get(k));
			}

			return map;

		} catch (IOException e) {
			return new HashMap<String, String>();
		}

	}

	public static String getJobStatus(final String jobName) {
		String status = null;

		AbstractProject job = (AbstractProject) Jenkins.getInstance().getItem(jobName);
		if (job != null && job.isBuilding()) {
			status = "Building";
		} else if (job != null && job.isInQueue()) {
			status = "Queued";
		}

		return status;
	}

	public static void validateJobStatus(final String jobName) throws Exception {
		//FIXME: change parameter jobName to a set, to be more effective during the isRunning validation
		String jobStatus = getJobStatus(jobName);
		if(jobStatus != null){
			throw new Exception(String.format("The Job %s is %s.", jobName, jobStatus));
		}
	}

	public static TemplateWorkflowProperty getTemplateWorkflowProperty(final String jobName) {
		Job job = (Job) Jenkins.getInstance().getItem(jobName);
		if(job != null){
			return getTemplateWorkflowProperty(job);
		}
		return null;
	}

	public static TemplateWorkflowProperty getTemplateWorkflowProperty(Job job) {
		TemplateWorkflowProperty templateWorkflowProperty = (TemplateWorkflowProperty) job.getProperty(TemplateWorkflowProperty.class);
		return templateWorkflowProperty;
	}

	public static void deleteJobProperty(final String jobName) throws Exception {
		Job job = (Job) Jenkins.getInstance().getItem(jobName);
		if (job != null) {
			job.removeProperty(TemplateWorkflowProperty.class);
		}
	}

	public static void deleteJob(final String jobName) throws Exception {
		Job job = (Job) Jenkins.getInstance().getItem(jobName);
		if (job != null) {
			job.delete();
		}
	}

	public static boolean notUsesJobName(final String jobName) throws Exception {

		boolean used = false;

		if (StringUtils.isBlank(jobName)) {
			throw new Exception("Empty Job Name.");
		}

		Job job = (Job) Jenkins.getInstance().getItem(jobName);
		if(job != null){
			if(!itIsAClonedJob(job)){
				used = true;
			}
		}

		return used;

	}

	public static boolean notUsesWorkflowName(final String workflowName) throws Exception {

		boolean used = false;

		if (StringUtils.isBlank(workflowName)) {
			throw new Exception("Empty Workflow Name.");
		}

		List<TemplatesWorkflowJob> allTemplatesWorkflowJobs = findAllTemplatesWorkflowJobJobs();
		for (TemplatesWorkflowJob templatesWorkflowJob : allTemplatesWorkflowJobs) {
			Collection<TemplateWorkflowInstance> templateInstances = templatesWorkflowJob.getTemplateInstances();
			if(templateInstances != null){
				for (TemplateWorkflowInstance templateWorkflowInstance : templateInstances) {
					String instanceWorkflowName = templateWorkflowInstance.getInstanceName();
					if(StringUtils.equalsIgnoreCase(instanceWorkflowName, workflowName)){
						used = true;
					}
				}
			}
		}

		return used;

	}

	public static List<Job> findAllWorkflowsInstancesJobs() {
		List<Job> result = new ArrayList<Job>();
		List<Item> allItems = Jenkins.getInstance().getAllItems();
		for (Item i : allItems) {
			Collection<? extends Job> allJobs = i.getAllJobs();
			for (Job j : allJobs) {
				TemplateWorkflowInstances templateWorkflowInstances = (TemplateWorkflowInstances) j.getProperty(TemplateWorkflowInstances.class);
				if(templateWorkflowInstances != null){
					result.add(j);
				}
			}
		}
		return result;
	}

	public static Map<String, TemplateWorkflowInstance> findAllTemplateWorkflowInstance() {

		Map<String, TemplateWorkflowInstance> mapTemplateWorkflowInstances = new HashMap<String, TemplateWorkflowInstance>();

		for (TemplatesWorkflowJob templatesWorkflowJob : findAllTemplatesWorkflowJobJobs()) {
			Collection<TemplateWorkflowInstance> templatesWorkflowJobInstances = templatesWorkflowJob.getTemplateInstances();
			for (TemplateWorkflowInstance templateWorkflowInstance : templatesWorkflowJobInstances) {
				mapTemplateWorkflowInstances.put(templateWorkflowInstance.getInstanceName(), templateWorkflowInstance);
			}
		}

		return mapTemplateWorkflowInstances;

	}

	public static List<TemplateWorkflowInstance> findTemplateWorkflowInstanceRelatedWtihTemplateName(String templateName) {

		List<TemplateWorkflowInstance> listTemplateWorkflowInstances = new ArrayList<TemplateWorkflowInstance>();

		if (StringUtils.isBlank(templateName)) {
			return listTemplateWorkflowInstances;
		}

		Set<String> templatesName = Sets.newHashSet(templateName.split(","));

		for (TemplatesWorkflowJob templatesWorkflowJob : findAllTemplatesWorkflowJobJobs()) {
			Collection<TemplateWorkflowInstance> templatesWorkflowJobInstances = templatesWorkflowJob.getTemplateInstances();
			for (TemplateWorkflowInstance templateWorkflowInstance : templatesWorkflowJobInstances) {
				if(templatesName.contains( templateWorkflowInstance.getTemplateName() )){
					listTemplateWorkflowInstances.add(templateWorkflowInstance.clone());
				}
			}
		}

		return listTemplateWorkflowInstances;

	}

	public static Map<String, TemplatesWorkflowJob> findAllTemplateWorkflowJob() {

		Map<String, TemplatesWorkflowJob> mapTemplateWorkflowJob = new HashMap<String, TemplatesWorkflowJob>();

		for (TemplatesWorkflowJob templatesWorkflowJob : findAllTemplatesWorkflowJobJobs()) {
			mapTemplateWorkflowJob.put(templatesWorkflowJob.getName(), templatesWorkflowJob);
		}

		return mapTemplateWorkflowJob;

	}

	public static TemplatesWorkflowJob findTemplatesWorkflowJobByName(String jobName) {
		TemplatesWorkflowJob templatesWorkflowJob = null;
		Job job = (Job) Jenkins.getInstance().getItem(jobName);
		if (job != null && job instanceof TemplatesWorkflowJob ) {
			templatesWorkflowJob = (TemplatesWorkflowJob) job;
		}
		return templatesWorkflowJob;
	}

	public static List<TemplatesWorkflowJob> findAllTemplatesWorkflowJobJobs() {
		List<TemplatesWorkflowJob> result = new ArrayList<TemplatesWorkflowJob>();
		List<Item> allItems = Jenkins.getInstance().getAllItems();
		for (Item i : allItems) {
			Collection<? extends Job> allJobs = i.getAllJobs();
			for (Job j : allJobs) {
				if(j instanceof TemplatesWorkflowJob){
					result.add((TemplatesWorkflowJob)j);
				}
			}
		}
		return result;
	}

	public static List<Job> findAllClonedJobs() {
		List<Job> result = new ArrayList<Job>();
		List<Item> allItems = Jenkins.getInstance().getAllItems();
		for (Item jenkinsItem : allItems) {
			if(jenkinsItem instanceof Job){
				Job job = (Job)jenkinsItem;
				if(itIsAClonedJob(job)){
					result.add(job);
				}
			}
		}
		return result;
	}

	public static boolean itIsATemplateJob(Job job) {

		TemplateWorkflowProperty jobProperty = TemplateWorkflowUtil.getTemplateWorkflowProperty(job);
		if( jobProperty != null && !jobProperty.isWorkflowCreatedJob()
				&& StringUtils.isNotBlank(jobProperty.getTemplateName()) ){
			return true;
		}

		return false;
	}

	public static boolean itIsAClonedJob(Job job) {

		TemplateWorkflowProperty jobProperty = TemplateWorkflowUtil.getTemplateWorkflowProperty(job);
		if( jobProperty != null && jobProperty.isWorkflowCreatedJob() ){
			return true;
		}

		return false;
	}

	private static boolean createOrUpdate(
			final String workflowJobName,
			final String workflowName,
			final String templateName,
			final String templateJobName,
			final String clonedJobName,
			final Map<String, String> clonedJobParams,
			final Map<String, String> clonedJobGroup
			) throws Exception {

		Job templateJob = (Job) Jenkins.getInstance().getItem(templateJobName);
		String jobXml = FileUtils.readFileToString(templateJob.getConfigFile().getFile());

		for (String origJob : clonedJobGroup.keySet()) {
			jobXml = jobXml.replaceAll(">\\s*" + origJob + "\\s*</", ">" + clonedJobGroup.get(origJob) + "</");
			jobXml = jobXml.replaceAll(",\\s*" + origJob, "," + clonedJobGroup.get(origJob));
			jobXml = jobXml.replaceAll(origJob + "\\s*,", clonedJobGroup.get(origJob) + ",");
		}

		for (String key : clonedJobParams.keySet()) {
			String replacement = clonedJobParams.get(key).replace("&", "&amp;");
			jobXml = jobXml.replaceAll("@@" + key + "@@", replacement);
		}

		// Remove property to not propagate e event loop
		jobXml = jobXml.replaceAll("<"+TemplateWorkflowProperty.class.getName()+">(?s)\\s(.*)</"+TemplateWorkflowProperty.class.getName()+">","");

		InputStream is = null;
		try {
			is = new ByteArrayInputStream(jobXml.getBytes("UTF-8"));
			Job clonedJob = (Job) Jenkins.getInstance().getItem(clonedJobName);
			if(clonedJob == null){
				clonedJob = (Job) Jenkins.getInstance().createProjectFromXML(clonedJobName, is);
				LOGGER.info(String.format("JOB %s created.", clonedJob.getName()));
			} else {
				clonedJob.updateByXml(new StreamSource(is));
				LOGGER.info(String.format("JOB %s updated.", clonedJob.getName()));
			}
			TemplateWorkflowProperty property = (TemplateWorkflowProperty) clonedJob.getProperty(TemplateWorkflowProperty.class);
			if(property == null){
				property = new TemplateWorkflowProperty();
				clonedJob.addProperty(property);
			}
			property.setTemplateName(templateName);
			property.setWorkflowCreatedJob(Boolean.TRUE);
			property.setWorkflowJobName(workflowJobName);
			property.setWorkflowName(workflowName);

			//TODO: Remove disabled property - put a checkbox in the screen, only freestyle is enough
			if(clonedJob instanceof FreeStyleProject){
				((FreeStyleProject)clonedJob).enable();
			}
			clonedJob.save();

		} finally {
			try {
				is.close();
			} catch (Exception e) {
				return false;
			}
		}

		return true;
	}

}
