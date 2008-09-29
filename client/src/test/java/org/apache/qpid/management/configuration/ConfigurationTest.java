/*
 *
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
 *
 */
package org.apache.qpid.management.configuration;

import java.util.Map;
import java.util.UUID;

import org.apache.qpid.management.TestConstants;
import org.apache.qpid.management.domain.handler.base.IMessageHandler;
import org.apache.qpid.management.domain.handler.impl.ConfigurationMessageHandler;
import org.apache.qpid.management.domain.handler.impl.InstrumentationMessageHandler;
import org.apache.qpid.management.domain.handler.impl.SchemaResponseMessageHandler;
import org.apache.qpid.management.domain.model.AccessMode;
import org.apache.qpid.management.domain.model.type.Type;
import org.apache.qpid.management.domain.model.type.Uint8;

import junit.framework.TestCase;

/**
 * Test case for Configuration singleton.
 * 
 * @author Andrea Gazzarini
 */
public class ConfigurationTest extends TestCase
{
    /**
     * Tests the singleton behaviour of the configuration object.
     */
    public void testSingleton()
    {
        assertSame(Configuration.getInstance(),Configuration.getInstance());
    }
    
    /**
     * Tests the execution of getType() method when a valid code is supplied.
     * 
     * <br>precondition : the requested type already exist on the configuration.
     * <br>postcondition : the requested type is returned and no exception is thrown.
     */
    public void testGetTypeOk() throws UnknownTypeCodeException 
    {
        TypeMapping mapping = new TypeMapping();
        mapping.setCode(String.valueOf(TestConstants.VALID_CODE));
        mapping.setType(Uint8.class.getName());
        Configuration.getInstance().addTypeMapping(mapping);
        
        Type type = Configuration.getInstance().getType(TestConstants.VALID_CODE);
        
        assertTrue(type instanceof Uint8);
    }
    
    /**
     * Tests the execution of getType() method when a unknown code is supplied.
     * 
     * <br>precondition : the requested type doesn't exist on the configuration.
     * <br>postcondition : an exception is thrown indicating the failure.
     */
    public void testGetTypeKO()
    {
        try
        {
            Configuration.getInstance().getType(TestConstants.VALID_CODE+1);
            fail("If an unknwon code is supplied an exception must be thrown.");
        } catch (UnknownTypeCodeException expected)
        {
            assertEquals(TestConstants.VALID_CODE+1,expected.getCode());
        }        
    }
    
    /**
     * Tests the execution of getAccessMode() method when a valid code is supplied.
     * 
     * <br>precondition : the requested access mode already exist on the configuration.
     * <br>postcondition : the requested access mode is returned and no exception is thrown.
     */
    public void testGetAccessModeOk() throws UnknownAccessCodeException
    {
        String accessModeAsString = "RW";
        
        AccessModeMapping mapping = new AccessModeMapping();
        mapping.setCode(String.valueOf(TestConstants.VALID_CODE));
        mapping.setAccessMode(accessModeAsString);
        Configuration.getInstance().addAccessModeMapping(mapping);
        
        AccessMode accessMode = Configuration.getInstance().getAccessMode(TestConstants.VALID_CODE);
        assertSame(AccessMode.RW,accessMode);
    }
    
    /**
     * Tests the execution of getAccessMode() method when a unknown code is supplied.
     * 
     * <br>precondition : the requested access mode doesn't exist on the configuration.
     * <br>postcondition : an exception is thrown indicating the failure.
     */
    public void testGetAccessModeKO()
    {
        try
        {
            Configuration.getInstance().getAccessMode(TestConstants.VALID_CODE+1);
            fail("If an unknwon code is supplied an exception must be thrown.");
        } catch (UnknownAccessCodeException expected)
        {
            assertEquals(TestConstants.VALID_CODE+1,expected.getCode());
        }        
    }    
    
    /**
     * Tests the execution of the getBrokerConnectionData when a valid broker id is supplied.
     * 
     * <br>precondition : on configuration a connection data is stored and associated with the supplied id.
     * <br>postcondition : the requested connection data is returned and no exception is thrown.
     */
    public void testGetBrokerConnectionDataOK() throws Exception 
    {
        BrokerConnectionData connectionData = new BrokerConnectionData();
        connectionData.setHost("host");
        connectionData.setPort("7001");
        connectionData.setInitialPoolCapacity("0");
        connectionData.setMaxPoolCapacity("10");
        connectionData.setMaxWaitTimeout("1");
        Configuration.getInstance().addBrokerConnectionData(TestConstants.BROKER_ID, connectionData);
        
        BrokerConnectionData result = Configuration.getInstance().getBrokerConnectionData(TestConstants.BROKER_ID);
        assertSame(connectionData, result);
    }
    
    /**
     * Tests the execution of the getBrokerConnectionData when a unknown broker id is supplied.
     * 
     * <br>precondition : on configuration there's no connection data associated with the supplied id.
     * <br>postcondition : an exception is thrown indicating the failure.
     */
    public void testGetBrokerConnectionDataKO_withUnknownBrokerId() 
    {
        UUID brokerId = UUID.randomUUID();
        try 
        {
            Configuration.getInstance().getBrokerConnectionData(brokerId);
            fail("If an unknown broker id is supplied then an exception must be thrown.");
        } catch(UnknownBrokerException expected) 
        {
            assertEquals(brokerId.toString(),expected.getMessage());
        }
    }    
    
    /**
     * Tests the execution of the getBrokerConnectionData when a null id is supplied.
     * 
     * <br>precondition : a null broker is given.
     * <br>postcondition : an exception is thrown indicating the failure.
     */
    public void testGetBrokerConnectionDataKO_withNullBrokerId() 
    {
        try 
        {
            Configuration.getInstance().getBrokerConnectionData(null);
            fail("If a null broker id is supplied then an exception must be thrown.");
        } catch(UnknownBrokerException expected) 
        {
        }
    }       

    /**
     * Tests the behaviour of the getManagementQueueHandlers() method.
     * 
     * <br>precondition: 2 management handlers are in stored configuration
     * <br>postcondition : 2 management handlers are returned.
     */
    public void testGetManagementQueueHandlersOk() 
    {
        String i = "i";
        String c = "c";
        
        String instrMessageHandlerClassName = InstrumentationMessageHandler.class.getName();
        String configMessageHandlerClassName = ConfigurationMessageHandler.class.getName();
        
        MessageHandlerMapping instrMapping = new MessageHandlerMapping();
        MessageHandlerMapping configMapping = new MessageHandlerMapping();
        
        instrMapping.setOpcode(i);
        instrMapping.setMessageHandlerClass(instrMessageHandlerClassName);

        configMapping.setOpcode(c);
        configMapping.setMessageHandlerClass(configMessageHandlerClassName);
        
        Configuration.getInstance().addManagementMessageHandlerMapping(instrMapping);
        Configuration.getInstance().addManagementMessageHandlerMapping(configMapping);
        
        Map<Character, IMessageHandler> handlerMappings = Configuration.getInstance().getManagementQueueHandlers();
        
        assertEquals(2,handlerMappings.size());
        assertEquals(instrMessageHandlerClassName,handlerMappings.get(instrMapping.getOpcode()).getClass().getName());
        assertEquals(configMessageHandlerClassName,handlerMappings.get(configMapping.getOpcode()).getClass().getName());        
    }
    
    /**
     * Tests the behaviour of the getManagementQueueHandlers() method.
     * 
     * <br>precondition: 2 management handlers are in stored configuration
     * <br>postcondition : 2 management handlers are returned.
     */
    public void testGetMethodReplyQueueHandlersOk() 
    {
        String s = "s";
        
        String schemaMessageHandlerClassName = SchemaResponseMessageHandler.class.getName();
        
        MessageHandlerMapping schemaMapping = new MessageHandlerMapping();
        
        schemaMapping.setOpcode(s);
        schemaMapping.setMessageHandlerClass(schemaMessageHandlerClassName);

        Configuration.getInstance().addMethodReplyMessageHandlerMapping(schemaMapping);
        
        Map<Character, IMessageHandler> handlerMappings = Configuration.getInstance().getMethodReplyQueueHandlers();
        
        assertEquals(schemaMessageHandlerClassName,handlerMappings.get(schemaMapping.getOpcode()).getClass().getName());
    }    
}