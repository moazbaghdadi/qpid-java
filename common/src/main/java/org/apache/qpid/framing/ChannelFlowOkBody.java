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

/*
 * This file is auto-generated by Qpid Gentools v.0.1 - do not modify.
 * Supported AMQP version:
 *   8-0
 */

package org.apache.qpid.framing;

import java.io.DataOutput;
import java.io.IOException;

import org.apache.qpid.QpidException;
import org.apache.qpid.codec.MarkableDataInput;

public class ChannelFlowOkBody extends AMQMethodBodyImpl implements EncodableAMQDataBlock, AMQMethodBody
{

    public static final int CLASS_ID =  20;
    public static final int METHOD_ID = 21;

    // Fields declared in specification
    private final boolean _active; // [active]

    // Constructor
    public ChannelFlowOkBody(MarkableDataInput buffer) throws AMQFrameDecodingException, IOException
    {
        _active = (buffer.readByte() & 0x01) == 0x01;
    }

    public ChannelFlowOkBody(boolean active)
    {
        _active = active;
    }

    public int getClazz()
    {
        return CLASS_ID;
    }

    public int getMethod()
    {
        return METHOD_ID;
    }

    public final boolean getActive()
    {
        return _active;
    }

    protected int getBodySize()
    {
        int size = 1;
        return size;
    }

    public void writeMethodPayload(DataOutput buffer) throws IOException
    {
        writeBitfield( buffer, _active ? (byte)1 : (byte)0 );
    }

    public boolean execute(MethodDispatcher dispatcher, int channelId) throws QpidException
	{
        return dispatcher.dispatchChannelFlowOk(this, channelId);
	}

    public String toString()
    {
        StringBuilder buf = new StringBuilder("[ChannelFlowOkBodyImpl: ");
        buf.append( "active=" );
        buf.append(  getActive() );
        buf.append("]");
        return buf.toString();
    }

    public static void process(final MarkableDataInput buffer,
                               final ChannelMethodProcessor dispatcher)
            throws IOException
    {
        boolean active = (buffer.readByte() & 0x01) == 0x01;
        if(!dispatcher.ignoreAllButCloseOk())
        {
            dispatcher.receiveChannelFlowOk(active);
        }
    }
}
