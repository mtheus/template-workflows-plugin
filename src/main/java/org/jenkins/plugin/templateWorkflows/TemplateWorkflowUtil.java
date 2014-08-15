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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.stream.StreamSource;

import jenkins.model.Jenkins;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class TemplateWorkflowUtil {

	public static Set<String> getAllWorkflowTemplateNames() {
		
		Set<String> allWorkflowTemplateNames = new LinkedHashSet<String>();

		List<Item> allItems = Jenkins.getInstance().getAllItems();
		for (Item i : allItems) {
			Collection<? extends Job> allJobs = i.getAllJobs();
			for (Job j : allJobs) {
				TemplateWorkflowProperty t = (TemplateWorkflowProperty) j.getProperty(TemplateWorkflowProperty.class);
				if (t != null && t.getTemplateName() != null) {
					for (String tName : t.getTemplateName().split(",")) {
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
		for (Item i : allItems) {
			Collection<? extends Job> allJobs = i.getAllJobs();
			for (Job j : allJobs) {
				TemplateWorkflowProperty t = (TemplateWorkflowProperty) j.getProperty(TemplateWorkflowProperty.class);
				if (t != null && !t.isWorkflowCreatedJob()) {
					for (String tName : t.getTemplateName().split(",")) {
						if (templateName != null && templateName.equalsIgnoreCase(tName.trim())) {
							relatedJobs.add(j);
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
	
	public static TemplateWorkflowProperty getTemplateWorkflowProperty(final String jobName) {
	
		Job job = (Job) Jenkins.getInstance().getItem(jobName);
		TemplateWorkflowProperty templateWorkflowProperty = (TemplateWorkflowProperty) job.getProperty(TemplateWorkflowProperty.class);
		return templateWorkflowProperty;
	}
	
	public static void deleteJobProperty(final String jobName) throws Exception {

		String jobStatus = getJobStatus(jobName);
		if(jobStatus != null){
			throw new Exception(String.format("The Job %s is %s.", jobName, jobStatus));
		}
		
		Job job = (Job) Jenkins.getInstance().getItem(jobName);
		if (job != null) {
			job.removeProperty(TemplateWorkflowProperty.class);
		}		
	}
	
	public static void deleteJob(final String jobName) throws Exception {
		
		String jobStatus = getJobStatus(jobName);
		if(jobStatus != null){
			throw new Exception(String.format("The Job %s is %s.", jobName, jobStatus));
		}
		
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
			TemplateWorkflowProperty templateWorkflowProperty = (TemplateWorkflowProperty) job.getProperty(TemplateWorkflowProperty.class);
			if(templateWorkflowProperty == null){
				used = true;
			} else {
				if( !templateWorkflowProperty.isWorkflowCreatedJob() ){
					used = true;
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
	
	public static boolean createOrUpdate(final String workflowJobName, final String workflowName, final String templateJobName, final String clonedJobName, final Map<String, String> clonedJobParams, final Map<String, String> clonedJobGroup) throws Exception {

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

		InputStream is = null;
		try {
			is = new ByteArrayInputStream(jobXml.getBytes("UTF-8"));
			Job clonedJob = (Job) Jenkins.getInstance().getItem(clonedJobName);			
			if(clonedJob == null){
				clonedJob = (Job) Jenkins.getInstance().createProjectFromXML(clonedJobName, is);
			} else {
				clonedJob.updateByXml(new StreamSource(is));
			}
			TemplateWorkflowProperty property = (TemplateWorkflowProperty) clonedJob.getProperty(TemplateWorkflowProperty.class);
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
				e.printStackTrace();
				return false;
			}
		}
		
		return true;
	}

}
