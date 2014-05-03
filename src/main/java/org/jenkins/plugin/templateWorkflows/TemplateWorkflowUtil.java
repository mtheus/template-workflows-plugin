package org.jenkins.plugin.templateWorkflows;

public class TemplateWorkflowUtil {

	/*
	
	private void createOrUpdate(final String operation, final Map<String, String> replacementsParams, final Map<String, String> replacementsJobs) throws IOException {
		boolean isNew = false;
		if (operation.equals("create")) {
			isNew = true;
		}
		Map<String, Boolean> isNewJobMap = new HashMap<String, Boolean>();
		for (Job job : relatedJobs) {
			String jobXml = FileUtils.readFileToString(job.getConfigFile().getFile());

			for (String origJob : replacementsJobs.keySet()) {
				jobXml = jobXml.replaceAll(">\\s*" + origJob + "\\s*</", ">" + replacementsJobs.get(origJob) + "</");
				jobXml = jobXml.replaceAll(",\\s*" + origJob, "," + replacementsJobs.get(origJob));
				jobXml = jobXml.replaceAll(origJob + "\\s*,", replacementsJobs.get(origJob) + ",");
			}

			for (String key : replacementsParams.keySet()) {
				String replacement = replacementsParams.get(key).replace("&", "&amp;");
				jobXml = jobXml.replaceAll("@@" + key + "@@", replacement);
			}

			Boolean wasCreated = this.createOrUpdateJob(job.getName(), replacementsJobs.get(job.getName()), jobXml, isNew);
			isNewJobMap.put(replacementsJobs.get(job.getName()), wasCreated);
		}

		this.addTemplateInfo(this.templateInstanceName, replacementsParams, replacementsJobs, isNewJobMap);
	}

	private Boolean createOrUpdateJob(final String jobOrigName, final String jobReplacedName, final String jobXml, final boolean isNew) throws IOException {
		InputStream is = null;

		try {
			is = new ByteArrayInputStream(jobXml.getBytes("UTF-8"));

			Job replacedJob = null;

			if (isNew) {

				// check if job already exist
				Job job = (Job) Jenkins.getInstance().getItem(jobReplacedName);
				if (job != null) {
					return false;
				}

				replacedJob = (Job) Jenkins.getInstance().createProjectFromXML(jobReplacedName, is);
				replacedJob.removeProperty(TemplateWorkflowProperty.class);
				replacedJob.save();
				return true;

			} else {
				replacedJob = (Job) Jenkins.getInstance().getItem(jobReplacedName);
				replacedJob.updateByXml(new StreamSource(is));
				replacedJob.removeProperty(TemplateWorkflowProperty.class);
				replacedJob.save();
				return null;
			}

		} finally {
			try {
				is.close();
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}
	}

	public Set<String> getTemplateNames() {
		Set<String> ret = new LinkedHashSet<String>();

		List<Item> allItems = Jenkins.getInstance().getAllItems();
		for (Item i : allItems) {
			Collection<? extends Job> allJobs = i.getAllJobs();
			for (Job j : allJobs) {
				TemplateWorkflowProperty t = (TemplateWorkflowProperty) j.getProperty(TemplateWorkflowProperty.class);
				if (t != null && t.getTemplateName() != null) {
					for (String tName : t.getTemplateName().split(",")) {
						ret.add(tName.trim());
					}
				}
			}
		}
		return ret;
	}

	@JavaScriptMethod
	public JSONObject updateAll() {
		StringBuilder sb = new StringBuilder();
		JSONObject ret = new JSONObject();

		Collection<TemplateWorkflowInstance> instances = this.getTemplateInstances();

		List<String> updated = new ArrayList<String>();
		List<String> notUpdated = new ArrayList<String>();

		for (TemplateWorkflowInstance instance : instances) {
			String iname = instance.getInstanceName();
			this.setTemplateInstanceName(iname);
			relatedJobs = this.getRelatedJobs(instance.getTemplateName());
			try {
				this.createOrUpdate("update", instance.getJobParameters(), instance.getRelatedJobs());
				updated.add(iname);
			} catch (IOException e) {
				notUpdated.add(iname);
			}
		}

		if (!notUpdated.isEmpty()) {
			sb.append(notUpdated.size() + " workflows have not been updated: <ul>");
			for (String s : notUpdated) {
				sb.append("<li>" + s + "</li>");
			}
			sb.append("</ul>");
		}

		if (!updated.isEmpty()) {
			sb.append(updated.size() + " workflows have been updated: <ul>");
			for (String s : updated) {
				sb.append("<li>" + s + "</li>");
			}
			sb.append("</ul>");
		}

		ret.put("result", true);
		ret.put("msg", sb.toString());
		return ret;
	}

	@JavaScriptMethod
	public JSONObject setTemplateInstanceName(final String instanceName) {
		this.templateInstanceName = instanceName;
		JSONObject ret = new JSONObject();
		ret.put("result", true);
		return ret;
	}

	@JavaScriptMethod
	public JSONObject executeWorkflow(final String workflowName) throws IOException, InterruptedException {
		boolean result = false;
		String msg = "Starting Job/s not Defined for Workflow '" + workflowName + "'!";
		String jobs = "";

		try {
			TemplateWorkflowInstance templateInstance = this.templateInstances.get(workflowName);
			for (String jobTenplateName : templateInstance.getRelatedJobs().keySet()) {

				Job job = (Job) Jenkins.getInstance().getItem(jobTenplateName);
				TemplateWorkflowProperty t = (TemplateWorkflowProperty) job.getProperty(TemplateWorkflowProperty.class);
				if (t.getIsStartingWorkflowJob()) {
					String jobName = templateInstance.getRelatedJobs().get(jobTenplateName);
					job = (Job) Jenkins.getInstance().getItem(jobName);
					Jenkins.getInstance().getQueue().schedule((AbstractProject) job);
					jobs += ",'" + jobName + "' ";
					result = true;
				}
			}

		} catch (Exception e) {
			result = false;
			msg = "Error - Workflow '" + workflowName + "' was not Executed!";
		}

		if (result) {
			msg = jobs.replaceFirst(",", "") + "Scheduled";
		}

		JSONObject ret = new JSONObject();
		ret.put("result", result);
		ret.put("msg", msg);
		return ret;
	}

	@JavaScriptMethod
	public JSONObject deleteInstance(final String instanceName) throws IOException, InterruptedException {

		boolean result = true;
		String msg = "";
		TemplateWorkflowInstance templateInstance = this.templateInstances.get(instanceName);

		for (String jobName : templateInstance.getRelatedJobs().values()) {

			Job job = (Job) Jenkins.getInstance().getItem(jobName);
			if (job != null && job.isBuilding()) {
				result = false;
				msg = "Job " + job.getName() + " is Currently Building";
				break;
			} else if (job != null && job.isInQueue()) {
				result = false;
				msg = "Job " + job.getName() + " is in the Build Queue";
				break;
			}

		}

		if (result) {
			try {
				for (String jobName : templateInstance.getRelatedJobs().values()) {

					if (!templateInstance.isJobWasCreateByWorkflow(jobName)) {
						continue;
					}

					Job job = (Job) Jenkins.getInstance().getItem(jobName);
					if (job != null) {
						job.delete();
					}
				}
				this.templateInstances.remove(instanceName);
				this.save();
			} catch (Exception e) {
				result = false;
				msg = "Failed to Delete " + instanceName + ", Please Delete it Manually";
			}
		}

		JSONObject ret = new JSONObject();
		ret.put("result", result);
		ret.put("msg", msg);
		return ret;
	}

	@JavaScriptMethod
	public JSONObject validateJobName(final String newJobName, final boolean allowUseOfExistingJob) {

		String cssClass = "info";
		String msg = "Valid name";
		boolean result = true;

		if (StringUtils.isBlank(newJobName)) {
			cssClass = "error";
			msg = "Job name can't be empty!";
			result = false;
		}

		List<Item> allItems = Jenkins.getInstance().getAllItems();
		for (Item i : allItems) {
			Collection<? extends Job> allJobs = i.getAllJobs();
			for (Job j : allJobs) {
				if (j.getName().equalsIgnoreCase(newJobName)) {
					if (allowUseOfExistingJob) {

						TemplateWorkflowProperty t = (TemplateWorkflowProperty) j.getProperty(TemplateWorkflowProperty.class);
						if (t == null) {
							cssClass = "warning";
							msg = "Using existing job defenition";
						} else {
							cssClass = "error";
							msg = "You can't use a job that is a bulding block for a template workflow";
							result = false;
						}

					} else {
						cssClass = "error";
						msg = "Job already defined with name: '" + newJobName + "'";
						result = false;
					}

				}
			}
		}

		JSONObject ret = new JSONObject();
		ret.put("cssClass", cssClass);
		ret.put("msg", msg);
		ret.put("result", result);
		return ret;
	}

	@JavaScriptMethod
	public JSONObject validateJobIsNotRunning(final String jobName) {

		String msg = "";
		boolean result = true;
		AbstractProject job = (AbstractProject) Jenkins.getInstance().getItem(jobName);

		if (job != null && job.isBuilding()) {
			result = false;
			msg = "Job " + job.getName() + " is Currently Building";

		} else if (job != null && job.isInQueue()) {
			result = false;
			msg = "Job " + job.getName() + " is in the Build Queue";
		}

		JSONObject ret = new JSONObject();
		ret.put("result", result);
		ret.put("msg", msg);
		return ret;
	}

	@JavaScriptMethod
	public JSONObject validateTemplateName(final String instanceNewName) {

		boolean result = true;
		String msg = "Valid name";

		if (StringUtils.isBlank(instanceNewName)) {
			result = false;
			msg = "Workflow name can't be empty!";
		}

		if (this.templateInstances != null) {
			for (String instanceName : this.templateInstances.keySet()) {
				if (instanceName.equalsIgnoreCase(instanceNewName)) {
					result = false;
					msg = "Workflow already defined with name: '" + instanceNewName + "'";
				}
			}
		}

		JSONObject ret = new JSONObject();
		ret.put("result", result);
		ret.put("msg", msg);
		return ret;
	}

	@JavaScriptMethod
	public JSONObject refresh(final String templateName) {

		JSONObject ret = new JSONObject();

		// on create
		if (this.templateInstanceName == null) {
			ret.put("result", true);
			ret.put("msg", "<div>Click the 'Create Workflow' Link to define workflows</div>");
			return ret;
			// after delete
		} else if (!this.templateInstanceName.equals("template.createNewTemplate")) {
			if (this.templateInstances.get(this.templateInstanceName) == null) {
				ret.put("result", true);
				ret.put("msg", "<div>Click the 'Create Workflow' Link to Define Workflows</div>");
				return ret;
			}
		}

		boolean isNew = this.templateInstanceName.equals("template.createNewTemplate") ? true : false;
		TemplateWorkflowInstance templateInstance = null;

		try {
			if (isNew) {
				relatedJobs = this.getRelatedJobs(templateName);
				jobParameters = this.getTemplateParamaters(relatedJobs);
			} else {
				// if (!isNew) {
				templateInstance = this.templateInstances.get(this.templateInstanceName);
				String tname = templateInstance.getTemplateName();
				relatedJobs = this.getRelatedJobs(tname);
				jobParameters = this.getTemplateParamaters(relatedJobs);

				relatedJobs = this.getRelatedJobs(tname);
				Map<String, String> previousParams = templateInstance.getJobParameters();
				for (String p : jobParameters.keySet()) {
					jobParameters.put(p, previousParams.get(p));
				}
			}
			StringBuilder build = new StringBuilder();
			build.append("<div>&nbsp;</div>");

			if (isNew) {
				build.append("<div style=\"font-weight:bold;\">Please Select a Workflow Name: </div>");
				build.append("<input name=\"template.templateInstanceName\" ").append("id=\"template.templateInstanceName\"  ").append("onChange=\"validateTemplateName()\"")
						.append("onkeydown=\"validateTemplateName()\"").append("onkeyup=\"validateTemplateName()\"").append("class=\"setting-input\" value=\"\" type=\"text\"/>");
				build.append("<tr><td></td><td><div id =\"template.templateInstanceName.validation\" style=\"visibility: hidden;\"></div></td></tr>");
			} else {
				build.append("<div>");
				build.append("<span style=\"font-weight:bold;\">Workflow Name: '").append(this.templateInstanceName).append("'</span>");
				build.append("<span> (Created From Template: '").append(templateInstance.getTemplateName()).append("')</span>");
				build.append("</div>");
				build.append("<input type=\"hidden\" id=\"template.templateInstanceName\" name=\"template.templateInstanceName\" value=\"" + this.templateInstanceName + "\">");
			}

			build.append("<div>&nbsp;</div>");
			build.append("<div style=\"font-weight:bold;\">Workflow is Defined out of ").append(relatedJobs.size()).append(" Jobs:</div>");
			build.append("<table border=\"0\" cellpadding=\"1\" cellspacing=\"1\">");
			for (Job j : relatedJobs) {
				if (isNew) {
					build.append("<tr>").append("<td>").append(j.getName()).append(":&nbsp;</td>").append("<td style=\"width:300px;\">").append("<input name=\"template.")
							.append(j.getName()).append("\" ").append("id=\"template.").append(j.getName()).append("\"  ").append("onChange=\"validateJobName('")
							.append(j.getName()).append("', document.getElementById('template.").append(j.getName()).append("').value)\"").append("onkeydown=\"validateJobName('")
							.append(j.getName()).append("', document.getElementById('template.").append(j.getName()).append("').value)\"").append("onkeyup=\"validateJobName('")
							.append(j.getName()).append("', document.getElementById('template.").append(j.getName()).append("').value)\"")
							.append("class=\"setting-input\" value=\"\" type=\"text\"/>").append("</td>").append("</tr>");
					build.append("<tr><td></td><td><div id =\"").append(j.getName()).append(".validation\" style=\"visibility: hidden;\"></div></td></tr>");
				} else {
					String jobReplacedName = templateInstance.getRelatedJobs().get(j.getName());
					String href = Jenkins.getInstance().getRootUrl() + "job/" + jobReplacedName;
					build.append("<tr>").append("<td>").append(j.getName()).append(":&nbsp;</td>").append("<td style=\"width:300px;\">").append("<a class=\"tip\" id=\"")
							.append("template_job.").append(j.getName()).append("\" href=\"").append(href).append("\">").append(jobReplacedName).append("</a>")
							.append("<input type=\"hidden\" id=\"template.").append(j.getName()).append("\" name=\"template.").append(j.getName()).append("\" value=\"")
							.append(jobReplacedName).append("\">").append("</td>").append("</tr>");
				}
			}

			build.append("</table>");
			if (isNew) {
				build.append("<div><input type=\"checkbox\" onchange=\"return validateAllNames();\" id=\"allow_exist_name\" name=\"allow_exist_name\"/>Allow the Use of Existing Jobs</div>");
			}

			build.append("<div>&nbsp;</div>");
			build.append("<div>&nbsp;</div>");

			build.append("<div style=\"font-weight:bold;\">Workflow Parameters:</div>");
			build.append("<table border=\"0\" cellpadding=\"1\" cellspacing=\"1\">");
			for (String p : jobParameters.keySet()) {
				String value = jobParameters.get(p) != null ? jobParameters.get(p) : "";
				build.append("<tr><td>" + p + ":&nbsp;</td><td style=\"width:300px;\"><input name=\"template.").append(p).append("\"  class=\"setting-input\" value=\"")
						.append(value).append("\" type=\"text\"/></td></tr>");
			}
			build.append("</table>");
			build.append("<div>&nbsp;</div>");

			if (isNew) {
				build.append("<input type=\"hidden\" name=\"template.operation\" value=\"create\">");
				build.append("<input class=\"yui-button,yui-submit-button\" onclick=\"return validateCreate();\" type=\"submit\"  value=\"Create\">");
			} else {
				build.append("<input type=\"hidden\" name=\"template.operation\" value=\"update\">");
				build.append("<input class=\"yui-button,yui-submit-button\" onclick=\"return validateUpdate();\" type=\"submit\" name=\"template.operation\" value=\"Update\">");
			}

			ret.put("result", true);
			ret.put("msg", build.toString());
			return ret;

		} catch (Exception e) {
			e.printStackTrace();
			ret.put("result", false);
			ret.put("msg", "<div>Opps.. an Error Occur (" + e.getMessage() + ")</div>");
			return ret;
		}
	}

	private List<Job> getRelatedJobs(final String templateName) {
		List<Job> relatedJobs = new ArrayList<Job>();
		List<Item> allItems = Jenkins.getInstance().getAllItems();
		for (Item i : allItems) {
			Collection<? extends Job> allJobs = i.getAllJobs();
			for (Job j : allJobs) {
				TemplateWorkflowProperty t = (TemplateWorkflowProperty) j.getProperty(TemplateWorkflowProperty.class);
				if (t != null) {
					for (String tName : t.getTemplateName().split(",")) {
						if (templateName.equalsIgnoreCase(tName.trim())) {
							relatedJobs.add(j);
						}
					}
				}
			}
		}

		return relatedJobs;
	}

	private Map<String, String> getTemplateParamaters(final List<Job> relatedJobs) throws IOException {
		Pattern pattern = Pattern.compile("@@(.*?)@@");

		Map<String, String> jobParameters = new HashMap<String, String>();
		for (Job job : relatedJobs) {
			List<String> lines = FileUtils.readLines(job.getConfigFile().getFile());
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
	}

	private void addTemplateInfo(final String instanceName, final Map<String, String> replacementsParams, final Map<String, String> replacementsJobs,
			final Map<String, Boolean> isNewJobMap) throws IOException {

		if (this.templateInstances == null) {
			this.templateInstances = new TemplateWorkflowInstances();
		}

		TemplateWorkflowInstance instance = this.templateInstances.get(instanceName);
		if (instance == null) {
			instance = new TemplateWorkflowInstance(this.templateName, instanceName, isNewJobMap);
		}

		instance.setJobParameters(replacementsParams);
		instance.setRelatedJobs(replacementsJobs);
		this.templateInstances.put(instanceName, instance);

		this.addProperty(this.templateInstances);
		this.save();
	}
	
	*/

}
