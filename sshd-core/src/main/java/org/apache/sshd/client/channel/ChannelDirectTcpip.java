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
package org.apache.sshd.client.channel;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetSocketAddress;
import org.apache.sshd.client.future.DefaultOpenFuture;
import org.apache.sshd.client.future.OpenFuture;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.channel.ChannelOutputStream;
import org.apache.sshd.common.util.Buffer;

/**
 * TODO Add javadoc
 * 
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class ChannelDirectTcpip extends AbstractClientChannel {

    private final InetSocketAddress remote;
    private final InetSocketAddress local;
    private OutputStream forwardOutput;

    public ChannelDirectTcpip(InetSocketAddress remote, InetSocketAddress local) {
        super("direct-tcpip");

        if (remote == null) {
            throw new IllegalArgumentException("remote must not be null");
        }
        if (local == null) {
            throw new IllegalArgumentException("local must not be null");
        }
        this.remote = remote;
        this.local = local;
    }

    void setForwardOutputStream(OutputStream forwardOutputStream) {
        this.forwardOutput = forwardOutputStream;
    }

    @Override
    protected OpenFuture internalOpen() throws Exception {
        if (closeFuture.isClosed()) {
            throw new SshException("Session has been closed");
        }
        openFuture = new DefaultOpenFuture(lock);
        log.info("Send SSH_MSG_CHANNEL_OPEN on channel {}", id);
        Buffer buffer = session.createBuffer(
                SshConstants.Message.SSH_MSG_CHANNEL_OPEN, 0);
        buffer.putString(type);
        buffer.putInt(id);
        buffer.putInt(localWindow.getSize());
        buffer.putInt(localWindow.getPacketSize());
        buffer.putString(remote.getAddress().getHostAddress());
        buffer.putInt(remote.getPort());
        buffer.putString(local.getAddress().getHostAddress());
        buffer.putInt(local.getPort());
        session.writePacket(buffer);
        return openFuture;
    }

    protected void doOpen() throws Exception {
        out = new ChannelOutputStream(this, remoteWindow, log,
                SshConstants.Message.SSH_MSG_CHANNEL_DATA);

        if (forwardOutput == null) {
            PipedInputStream pipedIn = new PipedInputStream();
            this.forwardOutput = new PipedOutputStream(pipedIn);
            in = pipedIn;
        }
    }

    public void write(byte[] data, int off, int len) throws IOException {
        out.write(data, off, len);
        out.flush();
    }

    public synchronized OpenFuture open() throws Exception {
        return internalOpen();
    }

    protected synchronized void doWriteData(byte[] data, int off, int len)
            throws IOException {
        localWindow.consumeAndCheck(len);

        forwardOutput.write(data, off, len);
        forwardOutput.flush();
    }
}