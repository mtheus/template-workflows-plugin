package org.jenkins.plugin.templateWorkflows;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.AbstractProject;
import hudson.model.listeners.ItemListener;

import com.google.common.base.Throwables;

/**
 * React to changes being made on template projects
 */
@Extension
@SuppressWarnings("rawtypes")
public class TemplateWorkflowItemListener extends ItemListener {

	@Override
	public void onCreated(Item item) {
		
		if(true)return; /*teste*/

		AbstractProject createdProject = getProject(item);
		if(createdProject == null){
			return;
		}
		
		// It is a template?
		if (itIsATemplate(createdProject)){
			
			TemplateWorkflowProperty newJobTemplateWorkflowProperty = TemplateWorkflowUtil.getTemplateWorkflowProperty(createdProject.getName());
			String newJobTemplateName = newJobTemplateWorkflowProperty.getTemplateName();
			
			// Lookup for all workflows job
			List<TemplatesWorkflowJob> allTemplateWorkflowJob = TemplateWorkflowUtil.findAllTemplatesWorkflowJobJobs();
			for (TemplatesWorkflowJob templatesWorkflowJob : allTemplateWorkflowJob) {
				
				//Lookup for all workflow instances
				Collection<TemplateWorkflowInstance> templateInstances = templatesWorkflowJob.getTemplateInstances();
				for (TemplateWorkflowInstance templateWorkflowInstance : templateInstances) {
					
					// Load dependent fields
					templatesWorkflowJob.selectInstance(templateWorkflowInstance.getInstanceName());
					
					// There are associated workflow?
					if( newJobTemplateName.equals( templatesWorkflowJob.getTemplateName() ) ){					
						
						// It is marked to use default name? 
						if( templatesWorkflowJob.getUseTemplatePrefix() ){							
							
							// Create new cloned job
							for (Entry<String, String> relatedJobEntry : templatesWorkflowJob.getJobRelation().entrySet()) {
								try {
									TemplateWorkflowUtil.createOrUpdate(templatesWorkflowJob.getName(), templateWorkflowInstance.getInstanceName(), 
											relatedJobEntry.getKey(), relatedJobEntry.getValue(), 
											templatesWorkflowJob.getJobParameters(), templatesWorkflowJob.getJobRelation());
								} catch (Exception e) {
									//TODO: check if this is right way
									throw Throwables.propagate(e);
								}								
							}
							
							try {
								templatesWorkflowJob.save();
							} catch (IOException e) {
								//TODO: check if this is right way
								throw Throwables.propagate(e);
							}							
							
						}					
					} 
				}
			}
		}
		
	}

	@Override
	public void onUpdated(Item item) {
		
		//////////
		
		//List<TemplatesWorkflowJob> allTemplateWorkflowJob = TemplateWorkflowUtil.findAllTemplatesWorkflowJobJobs();
		
		//ver como ficar perfistido da variavel templateInstances dentro do TemplatesWorkflowJob
		
		//////////
		
		if(true)return; /*teste*/
		
		// quando não remover o teplate parameto deve deletar os job relacionados.
		
		AbstractProject updatedProject = getProject(item);
		if(updatedProject == null){
			return;
		}
		
		// It is a template?
		if (itIsATemplate(updatedProject)){
			
			TemplateWorkflowProperty updatedJobTemplateWorkflowProperty = TemplateWorkflowUtil.getTemplateWorkflowProperty(updatedProject.getName());
			String updatedJobTemplateName = updatedJobTemplateWorkflowProperty.getTemplateName();
			
			TemplateWorkflowProperty jobProperty = TemplateWorkflowUtil.getTemplateWorkflowProperty(updatedProject.getName());
			jobProperty.getTemplateName();
			
			// TODO: Update all based template instances
			
			// Lookup for all workflows job
			List<TemplatesWorkflowJob> allTemplateWorkflowJob = TemplateWorkflowUtil.findAllTemplatesWorkflowJobJobs();
			for (TemplatesWorkflowJob templatesWorkflowJob : allTemplateWorkflowJob) {
				
				//Lookup for all workflow instances
				Collection<TemplateWorkflowInstance> templateInstances = templatesWorkflowJob.getTemplateInstances();
				for (TemplateWorkflowInstance templateWorkflowInstance : templateInstances) {
					
					// Load dependent fields
					templatesWorkflowJob.selectInstance(templateWorkflowInstance.getInstanceName());
					
					// There are associated workflow?
					if( updatedJobTemplateName.equals( templatesWorkflowJob.getTemplateName() ) ){					
							
						// Create new cloned job
						for (Entry<String, String> relatedJobEntry : templatesWorkflowJob.getJobRelation().entrySet()) {
							//TODO: Filter the job equals?
							try {
								TemplateWorkflowUtil.createOrUpdate(templatesWorkflowJob.getName(), templateWorkflowInstance.getInstanceName(), 
										relatedJobEntry.getKey(), relatedJobEntry.getValue(), 
										templatesWorkflowJob.getJobParameters(), templatesWorkflowJob.getJobRelation());
							} catch (Exception e) {
								//TODO: check if this is right way
								throw Throwables.propagate(e);
							}								
						}
					} 
				}
			}
		}
			
	}

	@Override
	public void onDeleted(Item item) {
		
		if(true)return; /*teste*/
		
		AbstractProject deletedProject = getProject(item);
		if(deletedProject == null){
			return;
		}
		
		// It is a template?
		if (itIsATemplate(deletedProject)){		
			
		}
		
			// Delete all based template instances
			// If it is marked to use existing job we will keep detached.  

	}

	@Override
	public void onRenamed(Item item, String oldName, String newName) {
		
		if(true)return; /*teste*/
		
		AbstractProject renamedProject = getProject(item);
		if(renamedProject == null){
			return;
		}

		// It is a template?
		if (itIsATemplate(renamedProject)){		
			
		}
			// Change the reference in the workflow

	}
	
	private boolean itIsATemplate(AbstractProject project) {
		
		TemplateWorkflowProperty jobProperty = TemplateWorkflowUtil.getTemplateWorkflowProperty(project.getName());
		if( jobProperty != null && !jobProperty.isWorkflowCreatedJob() 
				&& StringUtils.isNotBlank(jobProperty.getTemplateName()) ){
			return true;
		}
		
		return false;
	}

	private static AbstractProject getProject(Item item) {
		if (item instanceof AbstractProject) {
			return (AbstractProject) item;			
		}
		return null;		
	}

}
