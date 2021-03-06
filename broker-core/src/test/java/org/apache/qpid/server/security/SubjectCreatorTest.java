/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.qpid.server.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.sasl.SaslServer;

import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.security.auth.SubjectAuthenticationResult;
import org.apache.qpid.test.utils.QpidTestCase;

public class SubjectCreatorTest extends QpidTestCase
{
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";

    private AuthenticationProvider<?> _authenticationProvider = mock(AuthenticationProvider.class);

    private GroupProvider<?> _groupManager1 = mock(GroupProvider.class);
    private GroupProvider<?> _groupManager2 = mock(GroupProvider.class);

    private Principal _userPrincipal = mock(Principal.class);
    private Principal _group1 = mock(Principal.class);
    private Principal _group2 = mock(Principal.class);

    private SubjectCreator _subjectCreator;
    private AuthenticationResult _authenticationResult;
    private SaslServer _testSaslServer = mock(SaslServer.class);
    private byte[] _saslResponseBytes = PASSWORD.getBytes();

    @Override
    public void setUp()
    {
        when(_groupManager1.getGroupPrincipalsForUser(USERNAME)).thenReturn(Collections.singleton(_group1));
        when(_groupManager2.getGroupPrincipalsForUser(USERNAME)).thenReturn(Collections.singleton(_group2));

        _subjectCreator = new SubjectCreator(_authenticationProvider, new HashSet<GroupProvider<?>>(Arrays.asList(_groupManager1, _groupManager2)),
                                             false);
        _authenticationResult = new AuthenticationResult(_userPrincipal);
        when(_authenticationProvider.authenticate(USERNAME, PASSWORD)).thenReturn(_authenticationResult);
    }

    public void testAuthenticateUsernameAndPasswordReturnsSubjectWithUserAndGroupPrincipals()
    {
        final SubjectAuthenticationResult actualResult = _subjectCreator.authenticate(USERNAME, PASSWORD);

        assertEquals(AuthenticationStatus.SUCCESS, actualResult.getStatus());

        final Subject actualSubject = actualResult.getSubject();

        assertEquals("Should contain one user principal and two groups ", 3, actualSubject.getPrincipals().size());

        assertTrue(actualSubject.getPrincipals().contains(new AuthenticatedPrincipal(_userPrincipal)));
        assertTrue(actualSubject.getPrincipals().contains(_group1));
        assertTrue(actualSubject.getPrincipals().contains(_group2));

        assertTrue(actualSubject.isReadOnly());
    }

    public void testSaslAuthenticationSuccessReturnsSubjectWithUserAndGroupPrincipals() throws Exception
    {
        when(_authenticationProvider.authenticate(_testSaslServer, _saslResponseBytes)).thenReturn(_authenticationResult);
        when(_testSaslServer.isComplete()).thenReturn(true);
        when(_testSaslServer.getAuthorizationID()).thenReturn(USERNAME);

        SubjectAuthenticationResult result = _subjectCreator.authenticate(_testSaslServer, _saslResponseBytes);

        final Subject actualSubject = result.getSubject();
        assertEquals("Should contain one user principal and two groups ", 3, actualSubject.getPrincipals().size());

        assertTrue(actualSubject.getPrincipals().contains(new AuthenticatedPrincipal(_userPrincipal)));
        assertTrue(actualSubject.getPrincipals().contains(_group1));
        assertTrue(actualSubject.getPrincipals().contains(_group2));

        assertTrue(actualSubject.isReadOnly());
    }

    public void testAuthenticateUnsuccessfulWithUsernameReturnsNullSubjectAndCorrectStatus()
    {
        testUnsuccessfulAuthentication(AuthenticationResult.AuthenticationStatus.CONTINUE);
        testUnsuccessfulAuthentication(AuthenticationResult.AuthenticationStatus.ERROR);
    }

    private void testUnsuccessfulAuthentication(AuthenticationStatus expectedStatus)
    {
        AuthenticationResult failedAuthenticationResult = new AuthenticationResult(expectedStatus);

        when(_authenticationProvider.authenticate(USERNAME, PASSWORD)).thenReturn(failedAuthenticationResult);

        SubjectAuthenticationResult subjectAuthenticationResult = _subjectCreator.authenticate(USERNAME, PASSWORD);

        assertSame(expectedStatus, subjectAuthenticationResult.getStatus());
        assertNull(subjectAuthenticationResult.getSubject());
    }

    public void testAuthenticateUnsuccessfulWithSaslServerReturnsNullSubjectAndCorrectStatus()
    {
        testUnsuccessfulAuthenticationWithSaslServer(AuthenticationResult.AuthenticationStatus.CONTINUE);
        testUnsuccessfulAuthenticationWithSaslServer(AuthenticationResult.AuthenticationStatus.ERROR);
    }

    private void testUnsuccessfulAuthenticationWithSaslServer(AuthenticationStatus expectedStatus)
    {
        AuthenticationResult failedAuthenticationResult = new AuthenticationResult(expectedStatus);

        when(_authenticationProvider.authenticate(_testSaslServer, _saslResponseBytes)).thenReturn(
                failedAuthenticationResult);
        when(_testSaslServer.isComplete()).thenReturn(false);

        SubjectAuthenticationResult subjectAuthenticationResult = _subjectCreator.authenticate(_testSaslServer, _saslResponseBytes);

        assertSame(expectedStatus, subjectAuthenticationResult.getStatus());
        assertNull(subjectAuthenticationResult.getSubject());
    }

    public void testGetGroupPrincipals()
    {
        getAndAssertGroupPrincipals(_group1, _group2);
    }

    public void testGetGroupPrincipalsWhenAGroupManagerReturnsNull()
    {
        when(_groupManager1.getGroupPrincipalsForUser(USERNAME)).thenReturn(null);

        getAndAssertGroupPrincipals(_group2);
    }

    public void testGetGroupPrincipalsWhenAGroupManagerReturnsEmptySet()
    {
        when(_groupManager2.getGroupPrincipalsForUser(USERNAME)).thenReturn(new HashSet<Principal>());

        getAndAssertGroupPrincipals(_group1);
    }

    private void getAndAssertGroupPrincipals(Principal... expectedGroups)
    {
        Set<Principal> actualGroupPrincipals = _subjectCreator.getGroupPrincipals(USERNAME);
        Set<Principal> expectedGroupPrincipals = new HashSet<Principal>(Arrays.asList(expectedGroups));
        assertEquals(expectedGroupPrincipals, actualGroupPrincipals);
    }

    public void testDisabledMechanisms()
    {
        AuthenticationProvider<?> authenticationProvider = mock(AuthenticationProvider.class);
        SubjectCreator subjectCreator = new SubjectCreator(authenticationProvider,
                                                           new HashSet<GroupProvider<?>>(Arrays.asList(_groupManager1,
                                                                                                       _groupManager2)),
                                                           false);
        when(authenticationProvider.getMechanisms()).thenReturn(Arrays.asList("PLAIN", "SCRAM-SHA-1"));
        assertTrue("Should contain SCRAM-SHA-1 mechanism.", subjectCreator.getMechanisms().contains("SCRAM-SHA-1"));
        assertTrue("Should contain PLAIN mechanism.", subjectCreator.getMechanisms().contains("PLAIN"));
        when(authenticationProvider.getDisabledMechanisms()).thenReturn(Arrays.asList("SCRAM-SHA-1"));
        assertFalse("SCRAM-SHA-1 should have been filtered out.", subjectCreator.getMechanisms().contains("SCRAM-SHA-1"));
        assertTrue("PLAIN should not have been filtered out.", subjectCreator.getMechanisms().contains("PLAIN"));
    }

    public void testSecureOnlyMechanisms()
    {
        AuthenticationProvider<?> authenticationProvider = mock(AuthenticationProvider.class);
        SubjectCreator subjectCreator;
        subjectCreator = new SubjectCreator(authenticationProvider,
                                            new HashSet<GroupProvider<?>>(Arrays.asList(_groupManager1, _groupManager2)),
                                            false);
        when(authenticationProvider.getMechanisms()).thenReturn(Arrays.asList("PLAIN", "SCRAM-SHA-1"));
        assertTrue("Should contain SCRAM-SHA-1 mechanism", subjectCreator.getMechanisms().contains("SCRAM-SHA-1"));
        assertTrue("Should contain PLAIN mechanism", subjectCreator.getMechanisms().contains("PLAIN"));
        when(authenticationProvider.getSecureOnlyMechanisms()).thenReturn(Arrays.asList("PLAIN"));
        assertTrue("SCRAM-SHA-1 should not have been filtered out.", subjectCreator.getMechanisms().contains("SCRAM-SHA-1"));
        assertFalse("PLAIN should have been filtered out on insecure connection.", subjectCreator.getMechanisms().contains("PLAIN"));

        subjectCreator = new SubjectCreator(authenticationProvider,
                                            new HashSet<GroupProvider<?>>(Arrays.asList(_groupManager1, _groupManager2)),
                                            true);
        assertTrue("SCRAM-SHA-1 should not have been filtered out.", subjectCreator.getMechanisms().contains("SCRAM-SHA-1"));
        assertTrue("PLAIN should not have been filtered out on secure connection.", subjectCreator.getMechanisms().contains("PLAIN"));}
}
