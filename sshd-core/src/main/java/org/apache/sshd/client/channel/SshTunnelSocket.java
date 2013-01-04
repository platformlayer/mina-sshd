package org.apache.sshd.client.channel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.net.UnknownHostException;

import org.apache.sshd.ClientSession;
import org.apache.sshd.client.future.OpenFuture;
import org.apache.sshd.common.future.CloseFuture;

/**
 * A Socket implementation that uses SSH 'direct-tcpip' forwarding.
 * 
 * By implementing Socket, we avoid opening a local port for forwarding.
 * 
 */
public class SshTunnelSocket extends Socket {
    public SshTunnelSocket(ClientSession session,
            InetSocketAddress localSocketAddress) throws IOException {
        super(new SshTunnelSocketImpl(session, localSocketAddress));
    }

    public SshTunnelSocket(ClientSession session) throws IOException {
        this(session, generateLocalSocketAddress());
    }

    private static InetSocketAddress generateLocalSocketAddress()
            throws UnknownHostException {
        int port = 30000;
        InetAddress localAddress = InetAddress.getLocalHost();
        return new InetSocketAddress(localAddress, port);
    }

    static class SshTunnelSocketImpl extends SocketImpl {
        private final ClientSession session;
        private final InetSocketAddress localSocketAddress;

        private ChannelDirectTcpip channel;

        public SshTunnelSocketImpl(ClientSession session,
                InetSocketAddress localSocketAddress) {
            this.session = session;
            this.localSocketAddress = localSocketAddress;
        }

        public void setOption(int optID, Object value) throws SocketException {
            throw new UnsupportedOperationException();
        }

        public Object getOption(int optID) throws SocketException {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void create(boolean stream) throws IOException {
        }

        @Override
        protected void connect(String host, int port) throws IOException {
            InetAddress remoteAddr = InetAddress.getByName(host);
            connect(remoteAddr, port);
        }

        @Override
        protected void connect(InetAddress address, int port)
                throws IOException {
            InetSocketAddress remote = new InetSocketAddress(address, port);
            connect(remote, 0);
        }

        @Override
        protected void connect(SocketAddress address, int timeout)
                throws IOException {
            InetSocketAddress remote = (InetSocketAddress) address;

            ChannelDirectTcpip channel;
            OpenFuture openFuture;
            try {
                channel = session.createDirectTcpipChannel(remote,
                        localSocketAddress);
                openFuture = channel.open();
                if (timeout == 0) {
                    openFuture.await();
                } else {
                    openFuture.await(timeout);
                }
            } catch (Exception e) {
                throw new IOException("Error connecting channel", e);
            }

            if (openFuture.getException() != null) {
                throw new IOException("Error connecting channel",
                        openFuture.getException());
            }

            this.channel = channel;
        }

        @Override
        protected void bind(InetAddress host, int port) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void listen(int backlog) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void accept(SocketImpl s) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        protected InputStream getInputStream() throws IOException {
            return channel.getIn();
        }

        @Override
        protected OutputStream getOutputStream() throws IOException {
            return channel.getOut();
        }

        @Override
        protected int available() throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void close() throws IOException {
            boolean immediately = false;
            CloseFuture closeFuture = channel.close(immediately);
            try {
                closeFuture.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Error waiting for close", e);
            }
        }

        @Override
        protected void sendUrgentData(int data) throws IOException {
            throw new UnsupportedOperationException();
        }
    }

}
