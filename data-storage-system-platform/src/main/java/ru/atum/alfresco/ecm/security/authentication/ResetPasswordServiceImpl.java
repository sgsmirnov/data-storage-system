/*
 * #%L
 * Alfresco Remote API
 * %%
 * Copyright (C) 2005 - 2017 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software.
 * If the software was purchased under a paid Alfresco license, the terms of
 * the paid license agreement will prevail.  Otherwise, the software is
 * provided under the following open source license terms:
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */

package ru.atum.alfresco.ecm.security.authentication;

import org.activiti.engine.HistoryService;
import org.activiti.engine.TaskService;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.admin.SysAdminParams;
import org.alfresco.repo.client.config.ClientAppConfig;
import org.alfresco.repo.client.config.ClientAppConfig.ClientApp;
import org.alfresco.repo.client.config.ClientAppNotFoundException;
import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.repo.workflow.WorkflowModelResetPassword;
import org.alfresco.service.cmr.action.ActionService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.MutableAuthenticationService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.cmr.workflow.*;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.surf.util.I18NUtil;
import org.springframework.extensions.webscripts.WebScriptException;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @deprecated from 7.1.0 * Reset password implementation based on workflow.
 *
 * @author Jamal Kaabi-Mofrad
 * @since 5.2.1
 */
@Deprecated
public class ResetPasswordServiceImpl implements ResetPasswordService
{
    private static final Log LOGGER = LogFactory.getLog(ResetPasswordServiceImpl.class);

    private static final String TIMER_END = "PT1H";
    private static final String WORKFLOW_DESCRIPTION_KEY = "resetpasswordwf_resetpassword.resetpassword.workflow.description";
    private static final String FTL_TEMPLATE_ASSETS_URL = "template_assets_url";
    private static final String FTL_RESET_PASSWORD_URL = "reset_password_url";
    private static final String FTL_USER_NAME = "userName";

    private WorkflowService workflowService;
    private HistoryService activitiHistoryService;
    private ActionService actionService;
    private PersonService personService;
    private NodeService nodeService;
    private SysAdminParams sysAdminParams;
    private MutableAuthenticationService authenticationService;
    private TaskService activitiTaskService;
    private EmailHelper emailHelper;
    private ClientAppConfig clientAppConfig;
    private String timerEnd = TIMER_END;
    private String defaultEmailSender;
    private boolean sendEmailAsynchronously = true;

    public void setWorkflowService(WorkflowService workflowService)
    {
        this.workflowService = workflowService;
    }

    public void setActivitiHistoryService(HistoryService activitiHistoryService)
    {
        this.activitiHistoryService = activitiHistoryService;
    }

    public void setActionService(ActionService actionService)
    {
        this.actionService = actionService;
    }

    public void setPersonService(PersonService personService)
    {
        this.personService = personService;
    }

    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }

    public void setSysAdminParams(SysAdminParams sysAdminParams)
    {
        this.sysAdminParams = sysAdminParams;
    }

    public void setAuthenticationService(MutableAuthenticationService authenticationService)
    {
        this.authenticationService = authenticationService;
    }

    public void setActivitiTaskService(TaskService activitiTaskService)
    {
        this.activitiTaskService = activitiTaskService;
    }

    public void setEmailHelper(EmailHelper emailHelper)
    {
        this.emailHelper = emailHelper;
    }

    public void setClientAppConfig(ClientAppConfig clientAppConfig)
    {
        this.clientAppConfig = clientAppConfig;
    }

    public void setTimerEnd(String timerEnd)
    {
        if (StringUtils.isNotEmpty(timerEnd))
        {
            this.timerEnd = timerEnd;
        }
    }

    public void setDefaultEmailSender(String defaultEmailSender)
    {
        this.defaultEmailSender = defaultEmailSender;
    }

    public void setSendEmailAsynchronously(boolean sendEmailAsynchronously)
    {
        this.sendEmailAsynchronously = sendEmailAsynchronously;
    }

    public void init()
    {
        PropertyCheck.mandatory(this, "workflowService", workflowService);
        PropertyCheck.mandatory(this, "activitiHistoryService", activitiHistoryService);
        PropertyCheck.mandatory(this, "actionService", actionService);
        PropertyCheck.mandatory(this, "personService", personService);
        PropertyCheck.mandatory(this, "nodeService", nodeService);
        PropertyCheck.mandatory(this, "sysAdminParams", sysAdminParams);
        PropertyCheck.mandatory(this, "authenticationService", authenticationService);
        PropertyCheck.mandatory(this, "activitiTaskService", activitiTaskService);
        PropertyCheck.mandatory(this, "emailHelper", emailHelper);
        PropertyCheck.mandatory(this, "clientAppConfig", clientAppConfig);
        PropertyCheck.mandatory(this, "defaultEmailSender", defaultEmailSender);
    }

    @Override
    public void requestReset(String userId, String clientName)
    {
        ParameterCheck.mandatoryString("userId", userId);
        ParameterCheck.mandatoryString("clientName", clientName);

        String userEmail = validateUserAndGetEmail(userId);

        // Get the (latest) workflow definition for reset-password.
        WorkflowDefinition wfDefinition = workflowService.getDefinitionByName(WorkflowModelResetPassword.WORKFLOW_DEFINITION_NAME);

        // create workflow properties
        Map<QName, Serializable> props = new HashMap<>(7);
        props.put(WorkflowModel.PROP_WORKFLOW_DESCRIPTION, I18NUtil.getMessage(WORKFLOW_DESCRIPTION_KEY));
        props.put(WorkflowModelResetPassword.WF_PROP_USERNAME, userId);
        props.put(WorkflowModelResetPassword.WF_PROP_USER_EMAIL, userEmail);
        props.put(WorkflowModelResetPassword.WF_PROP_CLIENT_NAME, clientName);
        props.put(WorkflowModel.ASSOC_PACKAGE, workflowService.createPackage(null));

        String guid = GUID.generate();
        props.put(WorkflowModelResetPassword.WF_PROP_KEY, guid);
        props.put(WorkflowModelResetPassword.WF_PROP_TIMER_END, timerEnd);

        // start the workflow
        WorkflowPath path = workflowService.startWorkflow(wfDefinition.getId(), props);
        if (path.isActive())
        {
            WorkflowTask startTask = workflowService.getStartTask(path.getInstance().getId());
            workflowService.endTask(startTask.getId(), null);
        }
    }

    protected String validateUserAndGetEmail(String userId)
    {
        if (!personService.personExists(userId))
        {
            throw new ResetPasswordWorkflowInvalidUserException("User does not exist: " + userId);
        }
        else if (!personService.isEnabled(userId))
        {
            throw new ResetPasswordWorkflowInvalidUserException("User is disabled: " + userId);
        }

        NodeRef personNode = personService.getPerson(userId, false);
        return (String) nodeService.getProperty(personNode, ContentModel.PROP_EMAIL);
    }

    @Override
    public void initiateResetPassword(ResetPasswordDetails resetDetails)
    {
        ParameterCheck.mandatory("resetDetails", resetDetails);

        validateIdAndKey(resetDetails.getWorkflowId(), resetDetails.getWorkflowKey(), resetDetails.getUserId());
        if (StringUtils.isBlank(resetDetails.getPassword()))
        {
            throw new IllegalArgumentException("Invalid password value [" + resetDetails.getPassword() + ']');
        }

        // So now we know that the workflow instance exists, is active and has the correct key. We can proceed.
        WorkflowTaskQuery processTaskQuery = new WorkflowTaskQuery();
        processTaskQuery.setProcessId(resetDetails.getWorkflowId());
        List<WorkflowTask> tasks = workflowService.queryTasks(processTaskQuery, false);

        if (tasks.isEmpty())
        {
            throw new InvalidResetPasswordWorkflowException(
                    "Invalid workflow identifier: " + resetDetails.getWorkflowId() + ", " + resetDetails.getWorkflowKey());
        }
        WorkflowTask task = tasks.get(0);

        // Set the provided password into the task. We will remove this after we have updated the user's authentication details.
        Map<QName, Serializable> props = Collections.singletonMap(WorkflowModelResetPassword.WF_PROP_PASSWORD, resetDetails.getPassword());

        // Note the taskId as taken from the WorkflowService will include a "activiti$" prefix.
        final String taskId = task.getId();
        workflowService.updateTask(taskId, props, null, null);
        workflowService.endTask(taskId, null);

        // Remove the previous task from Activiti's history - so that the password will not be in the database.
        // See http://www.activiti.org/userguide/index.html#history for a description of how Activiti stores historical records of
        // processes, tasks and properties.
        // The activitiHistoryService does not expect the activiti$ prefix.
        final String activitiTaskId = taskId.replace("activiti$", "");
        activitiHistoryService.deleteHistoricTaskInstance(activitiTaskId);

        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Deleting historical task for security reasons " + activitiTaskId);
        }
    }

    /**
     * This method ensures that the id refers to an in-progress workflow and that the key matches that stored in the workflow.
     *
     * @throws WebScriptException
     *             a 404 if any of the above is not true.
     */
    private void validateIdAndKey(String id, String key, String userId)
    {
        ParameterCheck.mandatory("id", id);
        ParameterCheck.mandatory("key", key);
        ParameterCheck.mandatory("userId", userId);

        WorkflowInstance workflowInstance = null;
        try
        {
            workflowInstance = workflowService.getWorkflowById(id);
        }
        catch (WorkflowException ignored)
        {
            // Intentionally empty.
        }

        if (workflowInstance == null)
        {
            throw new ResetPasswordWorkflowNotFoundException("The reset password workflow instance with the id [" + id + "] is not found.");
        }

        String recoveredKey;
        String username;
        if (workflowInstance.isActive())
        {
            // If the workflow is active we will be able to read the path properties.
            Map<QName, Serializable> pathProps = workflowService.getPathProperties(id);

            username = (String) pathProps.get(WorkflowModelResetPassword.WF_PROP_USERNAME);
            recoveredKey = (String) pathProps.get(WorkflowModelResetPassword.WF_PROP_KEY);
        }
        else
        {
            throw new InvalidResetPasswordWorkflowException("The reset password workflow instance with the id [" + id + "] is not active (it might be expired or has already been used).");
        }
        if (username == null || recoveredKey == null || !recoveredKey.equals(key))
        {
            String msg;
            if (username == null)
            {
                msg = "The recovered user name is null for the reset password workflow instance with the id [" + id + "]";
            }
            else if (recoveredKey == null)
            {
                msg = "The recovered key is null for the reset password workflow instance with the id [" + id + "]";
            }
            else
            {
                msg = "The recovered key [" + recoveredKey + "] does not match the given workflow key [" + key
                        + "] for the reset password workflow instance with the id [" + id + "]";
            }

            throw new InvalidResetPasswordWorkflowException(msg);
        }
        else if (!username.equals(userId))
        {
            throw new InvalidResetPasswordWorkflowException("The given user id [" + userId + "] does not match the person's user id [" + username
                    + "] who requested the password reset.");
        }
    }

    @Override
    public ClientApp getClientAppConfig(String clientName)
    {
        ParameterCheck.mandatoryString("clientName", clientName);

        ClientApp clientApp = clientAppConfig.getClient(clientName);
        if (clientApp == null)
        {
            throw new ClientAppNotFoundException("Client was not found [" + clientName + "]");
        }
        return clientApp;
    }


    private String getUrl(String url, String propName)
    {
        if (url == null)
        {
            LOGGER.warn("The url for the property [" + propName + "] is not configured.");
            return "";
        }

        if (url.endsWith("/"))
        {
            url = url.substring(0, url.length() - 1);
        }
        return UrlUtil.replaceShareUrlPlaceholder(url, sysAdminParams);
    }

    protected String getResetPasswordEmailTemplate(ClientApp clientApp)
    {
        return clientApp.getProperty("requestResetPasswordTemplatePath");
    }

    protected String getConfirmResetPasswordEmailTemplate(ClientApp clientApp)
    {
        return clientApp.getProperty("confirmResetPasswordTemplatePath");
    }


    /**
     * @author Jamal Kaabi-Mofrad
     */
    public static class ResetPasswordDetails
    {
        private String userId;
        private String password;
        private String workflowId;
        private String workflowKey;

        public String getUserId()
        {
            return userId;
        }

        public ResetPasswordDetails setUserId(String userId)
        {
            this.userId = userId;
            return this;
        }

        public String getPassword()
        {
            return password;
        }

        public ResetPasswordDetails setPassword(String password)
        {
            this.password = password;
            return this;
        }

        public String getWorkflowId()
        {
            return workflowId;
        }

        public ResetPasswordDetails setWorkflowId(String workflowId)
        {
            this.workflowId = workflowId;
            return this;
        }

        public String getWorkflowKey()
        {
            return workflowKey;
        }

        public ResetPasswordDetails setWorkflowKey(String workflowKey)
        {
            this.workflowKey = workflowKey;
            return this;
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder(100);
            sb.append("ResetPasswordDetails [userId=").append(userId)
                    .append(", workflowId=").append(workflowId)
                    .append(", workflowKey=").append(workflowKey)
                    .append(']');
            return sb.toString();
        }
    }

    /**
     * @author Jamal Kaabi-Mofrad
     */
    public static class ResetPasswordEmailDetails
    {
        private String userName;
        private String userEmail;
        private String fromEmail;
        private String templatePath;
        private String templateAssetsUrl;
        private Map<String, Serializable> templateModel;
        private String emailSubject;
        private boolean ignoreSendFailure = true;

        public String getUserName()
        {
            return userName;
        }

        public ResetPasswordEmailDetails setUserName(String userName)
        {
            this.userName = userName;
            return this;
        }

        public String getUserEmail()
        {
            return userEmail;
        }

        public ResetPasswordEmailDetails setUserEmail(String userEmail)
        {
            this.userEmail = userEmail;
            return this;
        }

        public String getFromEmail()
        {
            return fromEmail;
        }

        public ResetPasswordEmailDetails setFromEmail(String fromEmail)
        {
            this.fromEmail = fromEmail;
            return this;
        }

        public String getTemplatePath()
        {
            return templatePath;
        }

        public ResetPasswordEmailDetails setTemplatePath(String templatePath)
        {
            this.templatePath = templatePath;
            return this;
        }

        public String getTemplateAssetsUrl()
        {
            return templateAssetsUrl;
        }

        public ResetPasswordEmailDetails setTemplateAssetsUrl(String templateAssetsUrl)
        {
            this.templateAssetsUrl = templateAssetsUrl;
            return this;
        }

        public Map<String, Serializable> getTemplateModel()
        {
            return templateModel;
        }

        public ResetPasswordEmailDetails setTemplateModel(Map<String, Serializable> templateModel)
        {
            this.templateModel = templateModel;
            return this;
        }

        public String getEmailSubject()
        {
            return emailSubject;
        }

        public ResetPasswordEmailDetails setEmailSubject(String emailSubject)
        {
            this.emailSubject = emailSubject;
            return this;
        }

        public boolean isIgnoreSendFailure()
        {
            return ignoreSendFailure;
        }

        public ResetPasswordEmailDetails setIgnoreSendFailure(boolean ignoreSendFailure)
        {
            this.ignoreSendFailure = ignoreSendFailure;
            return this;
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder(250);
            sb.append("ResetPasswordEmailDetails [userName=").append(userName)
                    .append(", userEmail=").append(userEmail)
                    .append(", fromEmail=").append(fromEmail)
                    .append(", templatePath=").append(templatePath)
                    .append(", templateAssetsUrl=").append(templateAssetsUrl)
                    .append(", templateModel=").append(templateModel)
                    .append(", emailSubject=").append(emailSubject)
                    .append(", ignoreSendFailure=").append(ignoreSendFailure)
                    .append(']');
            return sb.toString();
        }
    }

    /**
     * @author Jamal Kaabi-Mofrad
     * @since 5.2.1
     */
    public static class ResetPasswordWorkflowException extends AlfrescoRuntimeException
    {
        private static final long serialVersionUID = -694208478609278943L;

        public ResetPasswordWorkflowException(String msgId)
        {
            super(msgId);
        }
    }

    /**
     * @author Jamal Kaabi-Mofrad
     * @since 5.2.1
     */
    public static class ResetPasswordWorkflowNotFoundException extends ResetPasswordWorkflowException
    {
        private static final long serialVersionUID = -7492264073778098895L;

        public ResetPasswordWorkflowNotFoundException(String msgId)
        {
            super(msgId);
        }
    }

    /**
     * @author Jamal Kaabi-Mofrad
     * @since 5.2.1
     */
    public static class InvalidResetPasswordWorkflowException extends ResetPasswordWorkflowException
    {
        private static final long serialVersionUID = -4685359036247580984L;

        public InvalidResetPasswordWorkflowException(String msgId)
        {
            super(msgId);
        }
    }

    /**
     * @author Jamal Kaabi-Mofrad
     * @since 5.2.1
     */
    public static class ResetPasswordWorkflowInvalidUserException extends ResetPasswordWorkflowException
    {
        private static final long serialVersionUID = -6524046975575636256L;

        public ResetPasswordWorkflowInvalidUserException(String msgId)
        {
            super(msgId);
        }
    }
}
