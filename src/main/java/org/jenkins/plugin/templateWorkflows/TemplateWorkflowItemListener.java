package org.jenkins.plugin.templateWorkflows;

import static org.jenkins.plugin.templateWorkflows.TemplateWorkflowUtil.fillJobRelation;
import static org.jenkins.plugin.templateWorkflows.TemplateWorkflowUtil.findTemplateWorkflowInstanceRelatedWtihTemplateName;
import static org.jenkins.plugin.templateWorkflows.TemplateWorkflowUtil.getTemplateWorkflowProperty;
import static org.jenkins.plugin.templateWorkflows.TemplateWorkflowUtil.itIsATemplateJob;
import static org.jenkins.plugin.templateWorkflows.TemplateWorkflowUtil.mekeWorkflowJobsConsistent;
import static org.jenkins.plugin.templateWorkflows.TemplateWorkflowUtil.updateTemplateWorkflowInstance;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.listeners.ItemListener;

import java.util.List;
import java.util.Map;

import com.google.common.base.Throwables;

/**
 * React to changes being made on template projects
 */
@Extension
@SuppressWarnings("rawtypes")
public class TemplateWorkflowItemListener extends ItemListener {

	@Override
	public void onCreated(Item item) {
		updateTemplateInstanceMetadata(item);
	}

	@Override
	public void onUpdated(Item item) {
		updateTemplateInstanceMetadata(item);
	}

	@Override
	public void onDeleted(Item item) {
		updateTemplateInstanceMetadata(item);
	}

	@Override
	public void onRenamed(Item item, String oldName, String newName) {
		updateTemplateInstanceMetadata(item);
	}

	private void updateTemplateInstanceMetadata(Item item) {
		Job job = getProject(item);
		if(job == null){
			return;
		}

		if (itIsATemplateJob(job)){

			TemplateWorkflowProperty templateWorkflowProperty = getTemplateWorkflowProperty(job);
			List<TemplateWorkflowInstance> instances = findTemplateWorkflowInstanceRelatedWtihTemplateName(templateWorkflowProperty.getTemplateName());
			for (TemplateWorkflowInstance templateWorkflowInstance : instances) {
				Map<String, String> jobRealation = fillJobRelation(templateWorkflowInstance);
				templateWorkflowInstance.setRelatedJobs(jobRealation);
			}
			completeUpdate(instances);
		}
	}

	private void completeUpdate(List<TemplateWorkflowInstance> instances) {
		try {
			updateTemplateWorkflowInstance(instances);
		} catch (Exception e) {
			//TODO: check if this is right way
			throw Throwables.propagate(e);
		}
		try {
			mekeWorkflowJobsConsistent();
		} catch (Exception e) {
			//TODO: check if this is right way
			throw Throwables.propagate(e);
		}
	}

	private static Job getProject(Item item) {
		if (item instanceof Job) {
			return (Job) item;
		}
		return null;
	}

}
