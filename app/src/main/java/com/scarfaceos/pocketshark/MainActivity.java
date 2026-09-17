package com.scarfaceos.pocketshark;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.scarfaceos.pocketshark.capture.PacketCaptureService;
import com.scarfaceos.pocketshark.model.PacketEvent;
import com.scarfaceos.pocketshark.model.PacketRepository;
import com.scarfaceos.pocketshark.pcap.PcapIo;
import com.scarfaceos.pocketshark.ui.PacketAdapter;
import com.scarfaceos.pocketshark.ui.Ui;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MainActivity extends Activity implements PacketRepository.Listener {
    private static final int REQUEST_VPN = 410;
    private static final int REQUEST_IMPORT = 411;
    private static final int REQUEST_EXPORT = 412;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private FrameLayout content;
    private View capturePage;
    private View packetsPage;
    private View statsPage;
    private Button captureTab;
    private Button packetsTab;
    private Button statsTab;
    private TextView statusBadge;
    private TextView packetCounter;
    private TextView liveState;
    private TextView compatibilitySummary;
    private PacketAdapter packetAdapter;
    private StatsViews statsViews;
    private boolean refreshQueued;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            updateStatus(intent.getBooleanExtra(PacketCaptureService.EXTRA_RUNNING, false),
                    intent.getStringExtra(PacketCaptureService.EXTRA_MESSAGE));
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        View root = buildUi();
        setContentView(root);
        Ui.applySystemBarInsets(this, root);
        PacketRepository.get().addListener(this);
        registerStatusReceiver();
        requestNotificationPermission();
        refreshPackets();
        updateStatus(PacketCaptureService.isRunning(), PacketCaptureService.isRunning()
                ? "Capture active" : "Ready");
        showPage(0);
    }

    @Override protected void onDestroy() {
        PacketRepository.get().removeListener(this);
        try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BLACK);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 10));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.title(this, "PocketShark");
        TextView subtitle = Ui.text(this, "SCARFACE OS  •  MOBILE PACKET LAB", 10, Ui.MUTED);
        subtitle.setLetterSpacing(0.08f);
        names.addView(title);
        names.addView(subtitle);
        header.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        statusBadge = Ui.text(this, "READY", 11, Ui.GREEN);
        statusBadge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusBadge.setGravity(Gravity.CENTER);
        statusBadge.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 12), Ui.dp(this, 7));
        statusBadge.setBackground(Ui.rounded(0xff10251f, 16, 0xff245b46));
        header.addView(statusBadge);
        root.addView(header);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 8));
        captureTab = tabButton("CAPTURE");
        packetsTab = tabButton("PACKETS");
        statsTab = tabButton("STATISTICS");
        tabs.addView(captureTab, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1));
        tabs.addView(packetsTab, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1));
        tabs.addView(statsTab, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1));
        captureTab.setOnClickListener(v -> showPage(0));
        packetsTab.setOnClickListener(v -> showPage(1));
        statsTab.setOnClickListener(v -> showPage(2));
        root.addView(tabs);

        content = new FrameLayout(this);
        capturePage = buildCapturePage();
        packetsPage = buildPacketsPage();
        statsPage = buildStatsPage();
        content.addView(capturePage);
        content.addView(packetsPage);
        content.addView(statsPage);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private Button tabButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(11);
        button.setTextColor(Ui.MUTED);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        return button;
    }

    private View buildCapturePage() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 22));

        LinearLayout hero = Ui.card(this);
        TextView eyebrow = Ui.text(this, "LIVE INTERFACE", 10, Ui.CYAN);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        hero.addView(eyebrow);
        TextView heading = Ui.title(this, "Android VPN capture");
        hero.addView(heading, Ui.margins(this, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 5, 0, 6));
        liveState = Ui.text(this, "Ready to inspect IPv4 traffic", 13, Ui.TEXT);
        hero.addView(liveState);
        TextView detail = Ui.text(this,
                "No root required. TCP, UDP, DNS and QUIC traffic is forwarded through an "
                        + "on-device inspection engine. Encrypted payloads remain encrypted.", 12, Ui.MUTED);
        detail.setLineSpacing(0, 1.15f);
        hero.addView(detail, Ui.margins(this, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 14));

        LinearLayout controls = new LinearLayout(this);
        Button start = Ui.button(this, "▶  START CAPTURE", Ui.GREEN);
        Button stop = Ui.button(this, "■  STOP", Ui.RED);
        start.setOnClickListener(v -> confirmStartCapture());
        stop.setOnClickListener(v -> stopCapture());
        controls.addView(start, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 2));
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1);
        stopParams.setMargins(Ui.dp(this, 8), 0, 0, 0);
        controls.addView(stop, stopParams);
        hero.addView(controls);
        page.addView(hero);

        page.addView(buildCompatibilityCard(), Ui.margins(this,
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 12, 0, 0));

        LinearLayout files = Ui.card(this);
        TextView filesTitle = Ui.title(this, "Capture files");
        files.addView(filesTitle);
        TextView filesBody = Ui.text(this,
                "Open standard .pcap captures or export the current packet list for desktop Wireshark.",
                12, Ui.MUTED);
        files.addView(filesBody, Ui.margins(this, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 5, 0, 12));
        LinearLayout fileButtons = new LinearLayout(this);
        Button open = Ui.button(this, "OPEN PCAP", Ui.CYAN);
        Button export = Ui.button(this, "EXPORT PCAP", Ui.AMBER);
        open.setOnClickListener(v -> openPcapPicker());
        export.setOnClickListener(v -> openExportPicker());
        fileButtons.addView(open, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1));
        LinearLayout.LayoutParams exportParams = new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1);
        exportParams.setMargins(Ui.dp(this, 8), 0, 0, 0);
        fileButtons.addView(export, exportParams);
        files.addView(fileButtons);
        page.addView(files, Ui.margins(this, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 12, 0, 0));

        LinearLayout limits = Ui.card(this);
        TextView limitTitle = Ui.title(this, "Phone capture boundaries");
        limits.addView(limitTitle);
        addBullet(limits, "✓", Ui.GREEN, "Read-only observation and PCAP export");
        addBullet(limits, "✓", Ui.GREEN, "Internet forwarding while capture is active");
        addBullet(limits, "•", Ui.AMBER, "v0.2.0 live engine captures IPv4; IPv6 bypasses it safely");
        addBullet(limits, "•", Ui.AMBER, "No Wi-Fi monitor mode or other-device promiscuous capture");
        addBullet(limits, "×", Ui.RED, "No injection, spoofing, modification or decryption");
        page.addView(limits, Ui.margins(this, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 12, 0, 0));

        scroll.addView(page);
        return scroll;
    }

    private void addBullet(LinearLayout parent, String mark, int color, String body) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.TOP);
        TextView icon = Ui.text(this, mark, 14, color);
        icon.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 26), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView text = Ui.text(this, body, 12, Ui.TEXT);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        parent.addView(row, Ui.margins(this, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 10, 0, 0));
    }

    private View buildCompatibilityCard() {
        LinearLayout card = Ui.card(this);
        TextView eyebrow = Ui.text(this, "APP COMPATIBILITY", 10, Ui.AMBER);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(eyebrow);
        card.addView(Ui.title(this, "Keep problem apps connected"), Ui.margins(this,
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 5, 0, 5));
        TextView explanation = Ui.text(this,
                "Choose apps that should bypass PocketShark and use their normal internet "
                        + "connection. Bypassed apps stay online but are not captured.", 12, Ui.MUTED);
        explanation.setLineSpacing(0, 1.15f);
        card.addView(explanation);

        compatibilitySummary = Ui.text(this, bypassSummary(), 12, Ui.TEXT);
        compatibilitySummary.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(compatibilitySummary, Ui.margins(this,
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                0, 10, 0, 10));

        Button manage = Ui.button(this, "MANAGE APP BYPASS", Ui.AMBER);
        manage.setOnClickListener(view -> showAppBypassPicker());
        card.addView(manage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));
        return card;
    }

    private void showAppBypassPicker() {
        List<AppChoice> apps = loadLaunchableApps();
        if (apps.isEmpty()) {
            showError("No apps found", "Android did not return any launchable apps.");
            return;
        }

        Set<String> saved = AppBypassStore.get(this);
        String[] labels = new String[apps.size()];
        boolean[] checked = new boolean[apps.size()];
        for (int index = 0; index < apps.size(); index++) {
            AppChoice app = apps.get(index);
            labels[index] = app.label + "  •  " + app.packageName;
            checked[index] = saved.contains(app.packageName);
        }

        new AlertDialog.Builder(this)
                .setTitle("Apps using a direct connection")
                .setMultiChoiceItems(labels, checked, (dialog, which, enabled) -> checked[which] = enabled)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear all", (dialog, which) -> saveAppBypass(new HashSet<>()))
                .setPositiveButton("Save", (dialog, which) -> {
                    Set<String> selected = new HashSet<>();
                    for (int index = 0; index < apps.size(); index++) {
                        if (checked[index]) selected.add(apps.get(index).packageName);
                    }
                    saveAppBypass(selected);
                })
                .show();
    }

    private void saveAppBypass(Set<String> packages) {
        AppBypassStore.set(this, packages);
        if (compatibilitySummary != null) compatibilitySummary.setText(bypassSummary());
        toast(PacketCaptureService.isRunning()
                ? "Saved. Stop and start capture to apply the new bypass list."
                : "App compatibility settings saved.");
    }

    private String bypassSummary() {
        int count = AppBypassStore.get(this).size();
        return count == 0 ? "All apps currently use the capture engine."
                : count + " app" + (count == 1 ? "" : "s") + " use a direct connection.";
    }

    @SuppressWarnings("deprecation")
    private List<AppChoice> loadLaunchableApps() {
        PackageManager manager = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved;
        if (Build.VERSION.SDK_INT >= 33) {
            resolved = manager.queryIntentActivities(launcher,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL));
        } else {
            resolved = manager.queryIntentActivities(launcher, PackageManager.MATCH_ALL);
        }

        Map<String, AppChoice> unique = new LinkedHashMap<>();
        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null || info.activityInfo.packageName == null) continue;
            String packageName = info.activityInfo.packageName;
            if (packageName.equals(getPackageName())) continue;
            CharSequence loaded = info.loadLabel(manager);
            String label = loaded == null || loaded.toString().trim().isEmpty()
                    ? packageName : loaded.toString().trim();
            unique.putIfAbsent(packageName, new AppChoice(label, packageName));
        }
        ArrayList<AppChoice> apps = new ArrayList<>(unique.values());
        apps.sort((left, right) -> left.label.compareToIgnoreCase(right.label));
        return apps;
    }

    private View buildPacketsPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 8));

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText filter = new EditText(this);
        filter.setHint("Filter: tcp  port:443  src:10.0...");
        filter.setHintTextColor(0xff607687);
        filter.setTextColor(Ui.TEXT);
        filter.setSingleLine(true);
        filter.setTextSize(12);
        filter.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        filter.setBackground(Ui.rounded(Ui.PANEL, 10, 0xff243442));
        filter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                packetAdapter.setFilter(s.toString());
                packetCounter.setText(packetAdapter.getCount() + " shown");
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        filterRow.addView(filter, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
        packetCounter = Ui.text(this, "0 shown", 11, Ui.CYAN);
        packetCounter.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        filterRow.addView(packetCounter, new LinearLayout.LayoutParams(Ui.dp(this, 74), Ui.dp(this, 44)));
        page.addView(filterRow);

        TextView helper = Ui.text(this,
                "Tap a packet for protocol layers, payload preview and hex/ASCII.", 11, Ui.MUTED);
        page.addView(helper, Ui.margins(this, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 2, 7, 0, 7));

        packetAdapter = new PacketAdapter(this);
        ListView list = new ListView(this);
        list.setAdapter(packetAdapter);
        list.setDividerHeight(1);
        list.setDivider(new android.graphics.drawable.ColorDrawable(0xff1d2b36));
        list.setBackgroundColor(Ui.PANEL);
        list.setOnItemClickListener((parent, view, position, id) -> {
            PacketEvent packet = packetAdapter.getItem(position);
            Intent intent = new Intent(this, PacketDetailActivity.class);
            intent.putExtra("packet_number", packet.number);
            startActivity(intent);
        });
        page.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return page;
    }

    private View buildStatsPage() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 22));
        statsViews = new StatsViews();

        LinearLayout overview = Ui.card(this);
        overview.addView(Ui.title(this, "Capture overview"));
        statsViews.total = bigStat(overview, "TOTAL PACKETS", Ui.CYAN);
        statsViews.bytes = bigStat(overview, "CAPTURED BYTES", Ui.GREEN);
        statsViews.endpoints = bigStat(overview, "UNIQUE ENDPOINTS", Ui.AMBER);
        page.addView(overview);

        LinearLayout protocols = Ui.card(this);
        protocols.addView(Ui.title(this, "Protocol distribution"));
        statsViews.tcp = protocolRow(protocols, "TCP", 0xff8cb4ff);
        statsViews.udp = protocolRow(protocols, "UDP / DNS / QUIC", Ui.GREEN);
        statsViews.icmp = protocolRow(protocols, "ICMP", Ui.RED);
        statsViews.other = protocolRow(protocols, "OTHER", Ui.AMBER);
        page.addView(protocols, Ui.margins(this, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 12, 0, 0));
        scroll.addView(page);
        return scroll;
    }

    private TextView bigStat(LinearLayout parent, String label, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = Ui.text(this, label, 11, Ui.MUTED);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView value = Ui.text(this, "0", 22, color);
        value.setGravity(Gravity.END);
        value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(value, new LinearLayout.LayoutParams(Ui.dp(this, 160), ViewGroup.LayoutParams.WRAP_CONTENT));
        parent.addView(row, Ui.margins(this, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 12, 0, 0));
        return value;
    }

    private ProtocolStat protocolRow(LinearLayout parent, String label, int color) {
        ProtocolStat stat = new ProtocolStat();
        LinearLayout heading = new LinearLayout(this);
        TextView name = Ui.text(this, label, 11, color);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        stat.value = Ui.text(this, "0", 11, Ui.TEXT);
        stat.value.setGravity(Gravity.END);
        heading.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        heading.addView(stat.value, new LinearLayout.LayoutParams(Ui.dp(this, 90), ViewGroup.LayoutParams.WRAP_CONTENT));
        parent.addView(heading, Ui.margins(this, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 12, 0, 2));
        stat.bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        stat.bar.setMax(1000);
        stat.bar.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
        stat.bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff1a2833));
        parent.addView(stat.bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 8)));
        return stat;
    }

    private void showPage(int index) {
        Ui.setVisible(capturePage, index == 0);
        Ui.setVisible(packetsPage, index == 1);
        Ui.setVisible(statsPage, index == 2);
        styleTab(captureTab, index == 0);
        styleTab(packetsTab, index == 1);
        styleTab(statsTab, index == 2);
    }

    private void styleTab(Button button, boolean selected) {
        button.setTextColor(selected ? Ui.CYAN : Ui.MUTED);
        button.setBackground(selected ? Ui.rounded(Ui.PANEL_ALT, 9, 0xff2b7182)
                : new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
    }

    private void confirmStartCapture() {
        new AlertDialog.Builder(this)
                .setTitle("Local VPN inspection disclosure")
                .setMessage("PocketShark routes IPv4 TCP and UDP traffic through a local VPN so it can "
                        + "display packet headers and payload bytes while keeping apps connected. Inspection "
                        + "stays on this phone and is not uploaded or shared. Apps selected in App Compatibility "
                        + "bypass inspection. Android will show a VPN key icon, and any other VPN will disconnect. "
                        + "Use PocketShark only on your own phone and authorised networks.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("I AGREE & CONTINUE", (dialog, which) -> prepareVpn())
                .show();
    }

    private void prepareVpn() {
        Intent permission = VpnService.prepare(this);
        if (permission != null) startActivityForResult(permission, REQUEST_VPN);
        else startCapture();
    }

    private void startCapture() {
        PacketRepository.get().clear();
        Intent intent = new Intent(this, PacketCaptureService.class).setAction(PacketCaptureService.ACTION_START);
        startForegroundService(intent);
    }

    private void stopCapture() {
        Intent intent = new Intent(this, PacketCaptureService.class).setAction(PacketCaptureService.ACTION_STOP);
        startService(intent);
    }

    private void openPcapPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    private void openExportPicker() {
        if (PacketRepository.get().snapshot().isEmpty()) {
            toast("There are no packets to export yet.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.tcpdump.pcap");
        intent.putExtra(Intent.EXTRA_TITLE, "PocketShark-capture.pcap");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VPN && resultCode == RESULT_OK) {
            startCapture();
        } else if (requestCode == REQUEST_IMPORT && resultCode == RESULT_OK && data != null) {
            importPcap(data.getData());
        } else if (requestCode == REQUEST_EXPORT && resultCode == RESULT_OK && data != null) {
            exportPcap(data.getData());
        }
    }

    private void importPcap(Uri uri) {
        if (uri == null) return;
        new Thread(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException("Could not open the selected file");
                PcapIo.ImportResult result = PcapIo.read(input);
                PacketRepository.get().clear();
                PacketRepository.get().addImported(result.frames, result.timestampsMicros);
                uiHandler.post(() -> {
                    toast("Imported " + result.frames.size() + " packets"
                            + (result.skipped > 0 ? " (skipped " + result.skipped + ")" : ""));
                    showPage(1);
                });
            } catch (Exception error) {
                uiHandler.post(() -> showError("PCAP import failed", error.getMessage()));
            }
        }, "PcapImport").start();
    }

    private void exportPcap(Uri uri) {
        if (uri == null) return;
        List<PacketEvent> snapshot = PacketRepository.get().snapshot();
        new Thread(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new IllegalStateException("Could not create the file");
                PcapIo.write(output, snapshot);
                uiHandler.post(() -> toast("Exported " + snapshot.size() + " packets"));
            } catch (Exception error) {
                uiHandler.post(() -> showError("PCAP export failed", error.getMessage()));
            }
        }, "PcapExport").start();
    }

    private void updateStatus(boolean running, String message) {
        statusBadge.setText(running ? "CAPTURING" : "READY");
        statusBadge.setTextColor(running ? Ui.GREEN : Ui.CYAN);
        statusBadge.setBackground(Ui.rounded(running ? 0xff10251f : 0xff10202a, 16,
                running ? 0xff245b46 : 0xff28556b));
        liveState.setText(message == null || message.isEmpty()
                ? (running ? "Capture active" : "Ready to inspect IPv4 traffic") : message);
        liveState.setTextColor(running ? Ui.GREEN : Ui.TEXT);
    }

    @Override public void onPacketsChanged() {
        synchronized (this) {
            if (refreshQueued) return;
            refreshQueued = true;
        }
        uiHandler.postDelayed(() -> {
            synchronized (MainActivity.this) { refreshQueued = false; }
            refreshPackets();
        }, 180);
    }

    private void refreshPackets() {
        List<PacketEvent> packets = PacketRepository.get().snapshot();
        if (packetAdapter != null) {
            packetAdapter.setPackets(packets);
            packetCounter.setText(packetAdapter.getCount() + " shown");
        }
        refreshStats(packets);
    }

    private void refreshStats(List<PacketEvent> packets) {
        if (statsViews == null) return;
        long bytes = 0;
        int tcp = 0, udp = 0, icmp = 0, other = 0;
        java.util.HashSet<String> endpoints = new java.util.HashSet<>();
        for (PacketEvent packet : packets) {
            bytes += packet.length;
            endpoints.add(packet.source);
            endpoints.add(packet.destination);
            if (packet.protocol.equals("TCP")) tcp++;
            else if (packet.protocol.equals("UDP") || packet.protocol.equals("DNS") || packet.protocol.equals("QUIC")) udp++;
            else if (packet.protocol.startsWith("ICMP")) icmp++;
            else other++;
        }
        statsViews.total.setText(String.format(Locale.US, "%,d", packets.size()));
        statsViews.bytes.setText(formatBytes(bytes));
        statsViews.endpoints.setText(String.valueOf(endpoints.size()));
        setProtocolStat(statsViews.tcp, tcp, packets.size());
        setProtocolStat(statsViews.udp, udp, packets.size());
        setProtocolStat(statsViews.icmp, icmp, packets.size());
        setProtocolStat(statsViews.other, other, packets.size());
    }

    private void setProtocolStat(ProtocolStat stat, int count, int total) {
        int perMille = total == 0 ? 0 : (count * 1000 / total);
        stat.bar.setProgress(perMille, true);
        stat.value.setText(count + "  •  " + (perMille / 10.0f) + "%");
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1_048_576) return String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0);
        if (bytes >= 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return bytes + " B";
    }

    private void registerStatusReceiver() {
        IntentFilter filter = new IntentFilter(PacketCaptureService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED);
        else registerReceiver(statusReceiver, filter);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 900);
        }
    }

    private void showError(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message == null ? "Unknown error" : message)
                .setPositiveButton("OK", null).show();
    }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }

    private static final class ProtocolStat {
        ProgressBar bar;
        TextView value;
    }

    private static final class StatsViews {
        TextView total;
        TextView bytes;
        TextView endpoints;
        ProtocolStat tcp;
        ProtocolStat udp;
        ProtocolStat icmp;
        ProtocolStat other;
    }

    private static final class AppChoice {
        final String label;
        final String packageName;

        AppChoice(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
    }
}
