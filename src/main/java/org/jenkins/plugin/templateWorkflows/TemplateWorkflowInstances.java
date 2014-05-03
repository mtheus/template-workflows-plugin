package org.jenkins.plugin.templateWorkflows;

import hudson.Extension;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class TemplateWorkflowInstances extends JobProperty<TemplatesWorkflowJob> {

	private Map<String, TemplateWorkflowInstance> instances;

	@Exported
	public Map<String, TemplateWorkflowInstance> getInstances() {
		return instances;
	}

	@DataBoundConstructor
	public TemplateWorkflowInstances(Map<String, TemplateWorkflowInstance> instances) {
		this.instances = instances;
	}

	public TemplateWorkflowInstances() {
		instances = new HashMap<String, TemplateWorkflowInstance>();
	}

	public TemplateWorkflowInstance get(String instanceName) {
		return instances.get(instanceName);
	}

	public void put(String instanceName, TemplateWorkflowInstance instance) {
		instances.put(instanceName, instance);
	}

	public void remove(String instanceName) {
		instances.remove(instanceName);
	}

	public Set<String> keySet() {
		return instances.keySet();
	}

	public Collection<TemplateWorkflowInstance> values() {
		return instances.values();
	}

	@Extension
	public static class DescriptorImpl extends JobPropertyDescriptor {
		@Override
		public String getDisplayName() {
			return "TemplateInstances";
		}

		// TODO: its necessary? https://github.com/jenkinsci/dev-mode-plugin/blob/723b0a2441385e839fff02d1fde4b8faa405b708/src/main/java/org/jenkinsci/plugins/devmode/JobPropertyGenerator.java
		@Override
		public TemplateWorkflowInstances newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			if(formData.has("com.exlibris.template.TemplateInstances")){
				return req.bindJSON(TemplateWorkflowInstances.class, formData.getJSONObject("com.exlibris.template.TemplateInstances"));
			}
//			if(formData.has(TemplateWorkflowInstances.class.getName())){
//				return req.bindJSON(TemplateWorkflowInstances.class, formData.getJSONObject(TemplateWorkflowInstances.class.getName()));
//			}
			return null;
		}
	}
}


