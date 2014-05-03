package org.jenkins.plugin.templateWorkflows;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.AbstractProject;
import hudson.model.listeners.ItemListener;

import com.google.common.base.Throwables;

/**
 * React to changes being made on template projects
 */
@Extension
public class TemplateWorkflowItemListener extends ItemListener {

	@Override
	public void onCreated(Item item) {

	}

	@Override
	public void onUpdated(Item item) {

	}

	@Override
	public void onDeleted(Item item) {
//		AbstractProject property = getTemplateProperty(item);
//		if (property != null) {
//			try {
//				// TemplateUtils.handleTemplateDeleted((AbstractProject) item,
//				// property);
//			} catch (Exception e) {
//				throw Throwables.propagate(e);
//			}
//		}
	}

	@Override
	public void onRenamed(Item item, String oldName, String newName) {

	}

	// TODO:
	// https://raw.githubusercontent.com/JoelJ/ez-templates/master/src/main/java/com/joelj/jenkins/eztemplates/TemplateProjectListener.java
	/**
	 * @param item
	 *            A changed project
	 * @return null if this is not a template project
	 */
	private static AbstractProject getTemplateProperty(Item item) {
		// if (item instanceof AbstractProject) {
		// return (TemplateProperty) ((AbstractProject)
		// item).getProperty(TemplateProperty.class);
		// }
		System.out.println(item);
		return (AbstractProject) item;
	}

}
