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
package org.apache.qpid.framing;

import java.io.DataInput;
import java.io.IOException;

import org.apache.qpid.protocol.AMQConstant;

public class ContentHeaderPropertiesFactory
{
    private static final ContentHeaderPropertiesFactory _instance = new ContentHeaderPropertiesFactory();

    public static ContentHeaderPropertiesFactory getInstance()
    {
        return _instance;
    }

    private ContentHeaderPropertiesFactory()
    {
    }

    public BasicContentHeaderProperties createContentHeaderProperties(int classId, int propertyFlags,
                                                                 DataInput buffer, int size)
             throws AMQFrameDecodingException, IOException
    {
        BasicContentHeaderProperties properties;
        // AMQP version change: "Hardwired" version to major=8, minor=0
        // TODO: Change so that the actual version is obtained from
        // the ProtocolInitiation object for this session.
        if (classId == BasicConsumeBody.CLASS_ID)
        {
        	properties = new BasicContentHeaderProperties();
        }
        else
        {
        	throw new AMQFrameDecodingException(AMQConstant.FRAME_ERROR, "Unsupport content header class id: " + classId, null);
        }
        properties.populatePropertiesFromBuffer(buffer, propertyFlags, size);
        return properties;
    }
}
