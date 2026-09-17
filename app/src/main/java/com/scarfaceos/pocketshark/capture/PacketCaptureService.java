package com.scarfaceos.pocketshark.capture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;

import com.scarfaceos.pocketshark.AppBypassStore;
import com.scarfaceos.pocketshark.MainActivity;
import com.scarfaceos.pocketshark.R;
import com.scarfaceos.pocketshark.model.PacketEvent;
import com.scarfaceos.pocketshark.model.PacketRepository;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PacketCaptureService extends VpnService {
    public static final String ACTION_START = "com.scarfaceos.pocketshark.START_CAPTURE";
    public static final String ACTION_STOP = "com.scarfaceos.pocketshark.STOP_CAPTURE";
    public static final String ACTION_STATUS = "com.scarfaceos.pocketshark.CAPTURE_STATUS";
    public static final String EXTRA_RUNNING = "running";
    public static final String EXTRA_MESSAGE = "message";

    private static final int NOTIFICATION_ID = 7301;
    private static final String CHANNEL_ID = "pocketshark_capture";
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private final ExecutorService captureExecutor = Executors.newSingleThreadExecutor();
    private final Object tunWriteLock = new Object();
    private ParcelFileDescriptor tun;
    private FileInputStream tunInput;
    private FileOutputStream tunOutput;
    private TcpForwarder tcpForwarder;
    private UdpForwarder udpForwarder;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private volatile Network underlyingNetwork;
    private volatile long lastErrorNotice;

    public static boolean isRunning() { return RUNNING.get(); }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            shutdown("Capture stopped");
            return START_NOT_STICKY;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            broadcastStatus(true, "Capture active");
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, buildNotification());
        try {
            connectivityManager = getSystemService(ConnectivityManager.class);
            underlyingNetwork = findUnderlyingNetwork();
            Builder builder = new Builder()
                    .setSession("PocketShark IPv4 Capture")
                    .setMtu(1500)
                    .setBlocking(true)
                    .addAddress("10.88.0.1", 32)
                    .addRoute("0.0.0.0", 0);
            if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false);
            builder.allowFamily(OsConstants.AF_INET6); // v0.2.0 lets IPv6 use the underlying network.
            if (underlyingNetwork != null) {
                builder.setUnderlyingNetworks(new Network[]{underlyingNetwork});
            }
            int bypassedApps = applyAppBypass(builder);
            tun = builder.establish();
            if (tun == null) throw new IllegalStateException("Android VPN permission was not granted");
            tunInput = new FileInputStream(tun.getFileDescriptor());
            tunOutput = new FileOutputStream(tun.getFileDescriptor());
            tcpForwarder = new TcpForwarder(this);
            udpForwarder = new UdpForwarder(this);
            registerNetworkTracking();
            captureExecutor.execute(this::captureLoop);
            broadcastStatus(true, bypassedApps == 0
                    ? "VPN pass-through active • apps remain online"
                    : "VPN pass-through active • " + bypassedApps + " app"
                            + (bypassedApps == 1 ? "" : "s") + " bypassed");
        } catch (Exception error) {
            shutdown("Capture could not start: " + error.getMessage());
        }
        return START_NOT_STICKY;
    }

    private void captureLoop() {
        byte[] buffer = new byte[65535];
        try {
            while (RUNNING.get()) {
                int count = tunInput.read(buffer);
                if (count < 0) break;
                if (count == 0) continue;
                byte[] raw = Arrays.copyOf(buffer, count);
                PacketRepository.get().add(nowMicros(), PacketEvent.Direction.OUT, raw);
                Ipv4Packet packet = Ipv4Packet.parse(raw);
                if (packet == null) continue;
                if (packet.protocol == 6) tcpForwarder.handle(packet);
                else if (packet.protocol == 17) udpForwarder.handle(packet);
                // ICMP is decoded for imported captures but not forwarded in v0.2.0.
            }
        } catch (Exception error) {
            if (RUNNING.get()) reportForwardingError("Capture interface closed: " + error.getMessage());
        } finally {
            if (RUNNING.get()) shutdown("Capture ended");
        }
    }

    void injectIncoming(byte[] packet) {
        if (!RUNNING.get() || tunOutput == null || packet == null) return;
        try {
            synchronized (tunWriteLock) {
                tunOutput.write(packet);
                tunOutput.flush();
            }
            PacketRepository.get().add(nowMicros(), PacketEvent.Direction.IN, packet);
        } catch (Exception error) {
            if (RUNNING.get()) reportForwardingError("Packet return failed: " + error.getMessage());
        }
    }

    boolean protectAndBind(Socket socket) throws Exception {
        if (!protect(socket)) return false;
        Network network = underlyingNetwork;
        if (network != null) network.bindSocket(socket);
        return true;
    }

    boolean protectAndBind(DatagramSocket socket) throws Exception {
        if (!protect(socket)) return false;
        Network network = underlyingNetwork;
        if (network != null) network.bindSocket(socket);
        return true;
    }

    void reportForwardingError(String message) {
        long now = System.currentTimeMillis();
        if (now - lastErrorNotice > 5000L) {
            lastErrorNotice = now;
            broadcastStatus(RUNNING.get(), message);
        }
    }

    private void shutdown(String message) {
        boolean wasRunning = RUNNING.getAndSet(false);
        if (tcpForwarder != null) tcpForwarder.close();
        if (udpForwarder != null) udpForwarder.close();
        tcpForwarder = null;
        udpForwarder = null;
        try { if (tunInput != null) tunInput.close(); } catch (Exception ignored) {}
        try { if (tunOutput != null) tunOutput.close(); } catch (Exception ignored) {}
        try { if (tun != null) tun.close(); } catch (Exception ignored) {}
        tunInput = null;
        tunOutput = null;
        tun = null;
        if (connectivityManager != null && networkCallback != null) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback); } catch (Exception ignored) {}
        }
        networkCallback = null;
        connectivityManager = null;
        underlyingNetwork = null;
        if (wasRunning) broadcastStatus(false, message);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onRevoke() {
        shutdown("VPN permission revoked");
        super.onRevoke();
    }

    @Override public void onDestroy() {
        shutdown("Capture stopped");
        captureExecutor.shutdownNow();
        super.onDestroy();
    }

    private Notification buildNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Visible while PocketShark inspects local phone traffic");
            channel.setLightColor(Color.CYAN);
            manager.createNotificationChannel(channel);
        }
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, PacketCaptureService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pocket_shark)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(open)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(R.drawable.ic_pocket_shark, "Stop capture", stop).build())
                .build();
    }

    private int applyAppBypass(Builder builder) {
        try { builder.addDisallowedApplication(getPackageName()); } catch (Exception ignored) {}
        int applied = 0;
        Set<String> packages = AppBypassStore.get(this);
        for (String packageName : packages) {
            if (packageName == null || packageName.equals(getPackageName())) continue;
            try {
                builder.addDisallowedApplication(packageName);
                applied++;
            } catch (Exception ignored) {
                // An app may have been removed since the preference was saved.
            }
        }
        return applied;
    }

    private void registerNetworkTracking() {
        if (connectivityManager == null || networkCallback != null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) { refreshUnderlyingNetwork(); }
            @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                refreshUnderlyingNetwork();
            }
            @Override public void onLost(Network network) { refreshUnderlyingNetwork(); }
        };
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build();
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback);
        } catch (Exception error) {
            networkCallback = null;
            reportForwardingError("Network monitoring unavailable: " + error.getMessage());
        }
    }

    private void refreshUnderlyingNetwork() {
        Network selected = findUnderlyingNetwork();
        if (Objects.equals(selected, underlyingNetwork)) return;
        underlyingNetwork = selected;
        if (tun != null) {
            try {
                setUnderlyingNetworks(selected == null ? new Network[0] : new Network[]{selected});
            } catch (Exception ignored) {}
        }
        if (RUNNING.get()) {
            broadcastStatus(true, selected == null
                    ? "Waiting for Wi-Fi or mobile data"
                    : "Network changed • VPN pass-through restored");
        }
    }

    private Network findUnderlyingNetwork() {
        ConnectivityManager manager = connectivityManager;
        if (manager == null) return null;
        try {
            Network active = manager.getActiveNetwork();
            if (isUsableUnderlying(manager, active)) return active;
            for (Network candidate : manager.getAllNetworks()) {
                if (isUsableUnderlying(manager, candidate)) return candidate;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean isUsableUnderlying(ConnectivityManager manager, Network network) {
        if (network == null) return false;
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
    }

    private void broadcastStatus(boolean running, String message) {
        Intent status = new Intent(ACTION_STATUS).setPackage(getPackageName());
        status.putExtra(EXTRA_RUNNING, running);
        status.putExtra(EXTRA_MESSAGE, message == null ? "" : message);
        sendBroadcast(status);
    }

    private static long nowMicros() { return System.currentTimeMillis() * 1000L; }
}
