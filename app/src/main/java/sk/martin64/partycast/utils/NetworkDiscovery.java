package sk.martin64.partycast.utils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

public class NetworkDiscovery {

    public static NetworkDiscovery create(int port, long timeout) {
        return new NetworkDiscovery(port, timeout);
    }

    private final int port;
    private final long timeout;

    private NetworkDiscovery(int port, long timeout) {
        this.port = port;
        this.timeout = timeout;
    }

    public void run(byte[] requestBuffer, int responseBufferLength, Listener listener) {
        Executors.newSingleThreadExecutor().submit(() -> {
            List<Endpoint> data = new ArrayList<>();
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket();
                socket.setSoTimeout((int) timeout);
                InetAddress group = InetAddress.getByName("224.1.1.1");

                DatagramPacket packet = new DatagramPacket(requestBuffer, requestBuffer.length, group, port);
                byte[] responseBuffer = new byte[responseBufferLength];
                DatagramPacket received = new DatagramPacket(responseBuffer, responseBufferLength);

                socket.send(packet);
                long start = System.currentTimeMillis();

                //noinspection InfiniteLoopStatement
                while (true) {
                    socket.receive(received);
                    InetAddress address = received.getAddress();
                    byte[] copiedArray = new byte[responseBufferLength];
                    System.arraycopy(responseBuffer, 0, copiedArray, 0, responseBufferLength);
                    Arrays.fill(responseBuffer, (byte) 0);
                    Endpoint endpoint = new Endpoint(address, copiedArray);

                    data.add(endpoint);
                    listener.onEndpointDiscovered(endpoint, System.currentTimeMillis() - start);
                }
            } catch (IOException e) {
                if (socket != null) socket.close();

                if (e instanceof SocketTimeoutException) {
                    listener.onTimeout(data);
                } else {
                    listener.onError(e);
                }
            }
        });
    }

    public static class Endpoint {
        public final InetAddress address;
        public final byte[] dataBuffer;

        private Endpoint(InetAddress address, byte[] dataBuffer) {
            this.address = address;
            this.dataBuffer = dataBuffer;
        }
    }

    public interface Listener {
        void onEndpointDiscovered(Endpoint endpoint, long time);
        void onError(Exception e);
        void onTimeout(List<Endpoint> discovered);
    }
}