package sk.martin64.partycast.utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public final class NetworkScanner {

    private final int port;
    private String network;

    /**
     * @param port host port to be tried on each device
     * @param network subnet address in format X.X.X.X/YY where YY is subnet prefix length
     */
    public NetworkScanner(int port, String network) {
        this.port = port;
        this.network = network;
    }

    /**
     * Runs scan based on parameters passed to class constructor + specific test parameters in arguments
     * @param nThreads number of threads to spawn for test (or 0 to use cached thread pool)
     *                 cached pool is faster however might use more device resources
     * @param timeout how long to wait for socket to open (in milliseconds)
     * @param synchronizedOutput whether to call handler in synchronized mode (IP addresses in order, which is slower)
     * @param handler test results listener
     */
    public NetworkScanController run(int nThreads, int timeout, boolean synchronizedOutput, ScannerHandler handler) {
        if (handler == null) throw new IllegalArgumentException("ScannerHandler cannot be null");

        final List<String> output = new ArrayList<>();
        final List<Future<?>> pendingTasks = new ArrayList<>();
        final List<Long> finishedTasks = new ArrayList<>();
        final AtomicInteger counter = new AtomicInteger(0);
        ExecutorService service;
        if (nThreads > 0) service = Executors.newFixedThreadPool(nThreads);
        else service = Executors.newCachedThreadPool();
        ExecutorService finalizer = Executors.newSingleThreadExecutor();

        String[] a = network.split("/");
        NetworkIterator iterator = new NetworkIterator(a[0], Integer.parseInt(a[1]));

        long start = System.nanoTime();
        long taskPool = 0;
        while (iterator.hasNext()) {
            String ip = iterator.next();
            long id = ++taskPool;
            pendingTasks.add(service.submit(() -> {
                try (Socket s = new Socket()) {
                    s.setReuseAddress(true);
                    SocketAddress sa = new InetSocketAddress(ip, port);
                    long st = System.nanoTime();
                    s.connect(sa, timeout);
                    long et = System.nanoTime() - st;

                    if (synchronizedOutput && id != 1) { // wait for previous task to be finished
                        while (true) {
                            synchronized (finishedTasks) {
                                if (finishedTasks.contains(id-1))
                                    break;
                                try {
                                    Thread.sleep(1);
                                } catch (InterruptedException e) {
                                    return;
                                }
                            }
                        }
                    }

                    handler.onDiscoverActive(ip, et / 1000000f);
                    synchronized (output) {
                        output.add(ip);
                    }
                } catch (IOException e) {
                    handler.onDiscarded(ip, e);
                }

                handler.onStatusChange(counter.getAndIncrement(), iterator.getMaxHosts());
                synchronized (finishedTasks) {
                    finishedTasks.add(id);
                }
            }));
        }

        finalizer.submit(() -> {
            for (Future<?> f : pendingTasks) f.get();
            long time = System.nanoTime() - start;
            handler.onScanComplete(output, iterator.getMaxHosts(), time / 1000000f);
            service.shutdownNow();
            finalizer.shutdownNow();
            return null;
        });

        return new NetworkScanController(service, finalizer);
    }

    public static final class NetworkScanController {
        private ExecutorService main, finalizer;

        private NetworkScanController(ExecutorService main, ExecutorService finalizer) {
            this.main = main;
            this.finalizer = finalizer;
        }

        public void cancel() {
            main.shutdownNow();
            finalizer.shutdownNow();
        }
    }

    public interface ScannerHandler {
        void onDiscoverActive(String address, float ping);
        default void onDiscarded(String address, IOException e) {}
        default void onStatusChange(long processed, long max) {}
        void onScanComplete(List<String> addresses, long iteratedLength, float time);
    }

    public static class NetworkIterator implements Iterator<String> {

        private final short[] current;
        private int i;
        private final int maxHosts;

        public NetworkIterator(String networkStart, int prefixLength) {
            this.current = new short[4];
            String[] parts = networkStart.split("\\.");
            if (parts.length == 4) {
                this.current[0] = Short.parseShort(parts[0]);
                this.current[1] = Short.parseShort(parts[1]);
                this.current[2] = Short.parseShort(parts[2]);
                this.current[3] = Short.parseShort(parts[3]);
            } else throw new IllegalArgumentException("Not a IPv4 address: " + networkStart);

            this.maxHosts = (int) Math.pow(256, (int) ((32-prefixLength) / 8f));
        }

        private void increase() {
            current[3]++;
            if (current[3] > 255) {
                current[3] = 0;
                current[2]++;

                if (current[2] > 255) {
                    current[2] = 0;
                    current[1]++;

                    if (current[1] > 255) {
                        current[1] = 0;
                        current[0]++;
                    }
                }
            }
        }

        public int getMaxHosts() {
            return maxHosts;
        }

        @Override
        public boolean hasNext() {
            return i < maxHosts;
        }

        @Override
        public String next() {
            if (!hasNext()) return null;
            String c = current[0] + "." + current[1] + "." + current[2] + "." + current[3];
            i++; increase();

            return c;
        }
    }
}