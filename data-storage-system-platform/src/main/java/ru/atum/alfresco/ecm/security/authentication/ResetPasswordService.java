/*
 * #%L
 * Alfresco Remote API
 * %%
 * Copyright (C) 2005 - 2016 Alfresco Software Limited
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

import org.alfresco.repo.client.config.ClientAppConfig.ClientApp;
import org.alfresco.repo.client.config.ClientAppNotFoundException;
import ru.atum.alfresco.ecm.security.authentication.ResetPasswordServiceImpl.ResetPasswordDetails;

/**
 * @deprecated from 7.1.0 Reset password service.
 *
 * @author Jamal Kaabi-Mofrad
 * @since 5.2.1
 */
@Deprecated
public interface ResetPasswordService
{
    /**
     * Request password reset (starts the workflow).
     *
     * @param userId
     *            the user id
     * @param clientName
     *            the client app name (used to lookup the client that is registered to send emails so that client's specific configuration could be used.)
     */
    void requestReset(String userId, String clientName);

    /**
     * Validates the request reset password workflow and updates the workflow.
     *
     * @param resetDetails
     *            the {@code ResetPasswordDetails} object
     */
    void initiateResetPassword(ResetPasswordDetails resetDetails);


    /**
     * Gets the registered client.
     *
     * @param clientName
     *            the client name
     * @return {@code ClientApp} object
     * @throws ClientAppNotFoundException
     *             if no {@code ClientApp} is found with the given name
     */
    ClientApp getClientAppConfig(String clientName);
}
