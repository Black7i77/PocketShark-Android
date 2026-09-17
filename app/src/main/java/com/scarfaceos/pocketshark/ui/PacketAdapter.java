package com.scarfaceos.pocketshark.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.scarfaceos.pocketshark.model.PacketEvent;

import java.util.ArrayList;
import java.util.List;

public final class PacketAdapter extends BaseAdapter {
    private final Context context;
    private final ArrayList<PacketEvent> all = new ArrayList<>();
    private final ArrayList<PacketEvent> visible = new ArrayList<>();
    private String filter = "";

    public PacketAdapter(Context context) { this.context = context; }

    public void setPackets(List<PacketEvent> packets) {
        all.clear();
        all.addAll(packets);
        apply();
    }

    public void setFilter(String filter) {
        this.filter = filter == null ? "" : filter;
        apply();
    }

    private void apply() {
        visible.clear();
        for (PacketEvent event : all) if (event.matches(filter)) visible.add(event);
        notifyDataSetChanged();
    }

    @Override public int getCount() { return visible.size(); }
    @Override public PacketEvent getItem(int position) { return visible.get(position); }
    @Override public long getItemId(int position) { return visible.get(position).number; }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        Row row;
        if (convertView instanceof LinearLayout && convertView.getTag() instanceof Row) {
            row = (Row) convertView.getTag();
        } else {
            row = createRow();
            convertView = row.root;
            convertView.setTag(row);
        }

        PacketEvent event = getItem(position);
        row.number.setText("#" + event.number);
        row.time.setText(event.timeLabel());
        row.protocol.setText(event.protocol);
        row.direction.setText(event.direction == PacketEvent.Direction.OUT ? "↑ OUT"
                : event.direction == PacketEvent.Direction.IN ? "↓ IN" : "FILE");
        row.endpoint.setText(event.endpointSource() + "  →  " + event.endpointDestination());
        row.info.setText(event.info + "   •   " + event.length + " B");

        int accent = protocolColor(event.protocol);
        row.protocol.setTextColor(accent);
        row.direction.setTextColor(event.direction == PacketEvent.Direction.IN ? Ui.GREEN
                : event.direction == PacketEvent.Direction.OUT ? Ui.CYAN : Ui.AMBER);
        row.root.setBackgroundColor(position % 2 == 0 ? Ui.PANEL : Ui.PANEL_ALT);
        return convertView;
    }

    private Row createRow() {
        Row row = new Row();
        row.root = new LinearLayout(context);
        row.root.setOrientation(LinearLayout.VERTICAL);
        row.root.setPadding(Ui.dp(context, 12), Ui.dp(context, 9), Ui.dp(context, 12), Ui.dp(context, 9));

        LinearLayout top = new LinearLayout(context);
        top.setGravity(Gravity.CENTER_VERTICAL);
        row.number = Ui.text(context, "#0", 11, Ui.MUTED);
        row.number.setTypeface(Typeface.MONOSPACE);
        top.addView(row.number, new LinearLayout.LayoutParams(Ui.dp(context, 54), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.time = Ui.text(context, "00:00:00.000", 11, Ui.MUTED);
        row.time.setTypeface(Typeface.MONOSPACE);
        top.addView(row.time, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.protocol = Ui.text(context, "TCP", 12, Ui.CYAN);
        row.protocol.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.protocol.setGravity(Gravity.END);
        top.addView(row.protocol, new LinearLayout.LayoutParams(Ui.dp(context, 64), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.root.addView(top);

        LinearLayout middle = new LinearLayout(context);
        middle.setGravity(Gravity.CENTER_VERTICAL);
        row.direction = Ui.text(context, "↑ OUT", 11, Ui.CYAN);
        row.direction.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        middle.addView(row.direction, new LinearLayout.LayoutParams(Ui.dp(context, 54), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.endpoint = Ui.text(context, "source → destination", 12, Ui.TEXT);
        row.endpoint.setTypeface(Typeface.MONOSPACE);
        row.endpoint.setSingleLine(true);
        middle.addView(row.endpoint, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.root.addView(middle);

        row.info = Ui.text(context, "Packet information", 11, Ui.MUTED);
        row.info.setSingleLine(true);
        row.root.addView(row.info, Ui.margins(context, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 54, 3, 0, 0));
        return row;
    }

    private static int protocolColor(String protocol) {
        if (protocol.equals("TCP")) return 0xff8cb4ff;
        if (protocol.equals("DNS")) return 0xffc58cff;
        if (protocol.equals("UDP")) return Ui.GREEN;
        if (protocol.equals("QUIC")) return Ui.AMBER;
        if (protocol.startsWith("ICMP")) return Ui.RED;
        return Ui.CYAN;
    }

    private static final class Row {
        LinearLayout root;
        TextView number;
        TextView time;
        TextView protocol;
        TextView direction;
        TextView endpoint;
        TextView info;
    }
}
