package org.apache.sshd.client.channel;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.sshd.ClientSession;
import org.apache.sshd.client.future.OpenFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForwardLocalPort implements Closeable {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private ClientSession session;
    private final InetSocketAddress remoteSocketAddress;
    private final InetSocketAddress localSocketAddress;

    public ForwardLocalPort(ClientSession session,
            InetSocketAddress remoteSocketAddress,
            InetSocketAddress localSocketAddress) throws IOException {
        this.session = session;
        this.localSocketAddress = localSocketAddress;
        this.remoteSocketAddress = remoteSocketAddress;
    }

    public ForwardLocalPort(ClientSession session,
            InetSocketAddress remoteSocketAddress) throws IOException {
        this(session, remoteSocketAddress, null);
    }

    private ServerSocket localSocket;

    public InetSocketAddress getLocalSocketAddress() {
        return (InetSocketAddress) localSocket.getLocalSocketAddress();
    }

    volatile boolean stopListening;

    Thread acceptThread;

    int timeout = 5000;

    public void start() throws IOException {
        try {
            localSocket = new ServerSocket();
            localSocket.bind(localSocketAddress);

            acceptThread = new Thread(new Runnable() {
                public void run() {
                    while (!stopListening) {
                        try {
                            Socket socket = localSocket.accept();
                            Thread clientThread = new Thread(
                                    new LocalConnectionThread(socket));
                            clientThread.start();
                        } catch (Exception e) {
                            if (!stopListening) {
                                log.warn("Error in accept thread", e);
                            }
                        }
                    }
                }
            });

            acceptThread.start();
        } catch (Exception e) {
            throw new IOException("Error connecting channel", e);
        }
    }

    class LocalConnectionThread implements Runnable {
        final Socket socket;

        public LocalConnectionThread(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            ChannelDirectTcpip channel = null;

            try {
                channel = session.createDirectTcpipChannel(remoteSocketAddress,
                        getLocalSocketAddress());
                channel.setForwardOutputStream(socket.getOutputStream());

                OpenFuture openFuture = channel.open();
                if (timeout == 0) {
                    openFuture.await();
                } else {
                    openFuture.await(timeout);
                }

                InputStream socketIn = socket.getInputStream();
                OutputStream sshOut = channel.getOut();
                byte[] buffer = new byte[8192];
                while (socket.isConnected()) {
                    int len = socketIn.read(buffer);
                    if (len == -1) {
                        channel.close(false);
                        channel = null;
                        break;
                    }

                    sshOut.write(buffer, 0, len);
                    sshOut.flush();
                }
            } catch (Exception e) {
                log.warn("Error in TCP forward thread", e);
            }

            if (!socket.isClosed()) {
                try {
                    socket.close();
                } catch (Exception e) {
                    log.warn("Error closing TCP socket", e);
                }
            }

            if (channel != null) {
                try {
                    channel.close(false);
                } catch (Exception e) {
                    log.warn("Error closing SSH channel", e);
                }
            }
        }
    };

    public void close() throws IOException {
        stopListening = true;
        localSocket.close();
    }

}
