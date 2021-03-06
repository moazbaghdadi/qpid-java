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

package org.apache.qpid.amqp_1_0.framing;

import java.nio.ByteBuffer;

import org.apache.qpid.amqp_1_0.codec.ProtocolHandler;
import org.apache.qpid.amqp_1_0.transport.ConnectionEndpoint;
import org.apache.qpid.bytebuffer.QpidByteBuffer;

public class SASLProtocolHeaderHandler implements ProtocolHandler
{
    private ConnectionEndpoint _connection;
    private static final byte MAJOR_VERSION = (byte) 1;
    private static final byte MINOR_VERSION = (byte) 0;
    private boolean _done;

    enum State {
        AWAITING_MAJOR,
        AWAITING_MINOR,
        AWAITING_REVISION,
        ERROR
    }

    private State _state = State.AWAITING_MAJOR;



    public SASLProtocolHeaderHandler(final ConnectionEndpoint connection)
    {
        _connection = connection;
    }

    public ProtocolHandler parse(final QpidByteBuffer in)
    {
        while(in.hasRemaining() && _state != State.ERROR)
        {
            switch(_state)
            {
                case AWAITING_MAJOR:
                    _state = in.get() == MAJOR_VERSION ? State.AWAITING_MINOR : State.ERROR;
                    if(_state == State.ERROR || !in.hasRemaining())
                    {
                        _done = true;
                        break;
                    }
                case AWAITING_MINOR:
                    _state = in.get() == MINOR_VERSION ? State.AWAITING_MINOR : State.ERROR;
                    if(_state == State.ERROR || !in.hasRemaining())
                    {
                        _done = true;
                        break;
                    }
                case AWAITING_REVISION:
                    byte revision = in.get();
                    _connection.protocolHeaderReceived(MAJOR_VERSION, MINOR_VERSION, revision);
                    ProtocolHandler handler = new SASLFrameHandler(_connection);
                    _done = true;
                    return handler.parse(in);
            }
        }
        if(_state == State.ERROR)
        {
            _connection.invalidHeaderReceived();

        }
        return this;

    }

    public boolean isDone()
    {
        return _done || _connection.closedForInput();
    }
}
