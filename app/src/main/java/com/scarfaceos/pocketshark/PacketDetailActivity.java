package com.scarfaceos.pocketshark;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.scarfaceos.pocketshark.model.PacketEvent;
import com.scarfaceos.pocketshark.model.PacketRepository;
import com.scarfaceos.pocketshark.ui.Ui;

public final class PacketDetailActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        long number = getIntent().getLongExtra("packet_number", -1);
        PacketEvent event = PacketRepository.get().find(number);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 18));
        root.setBackgroundColor(Ui.BLACK);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.text(this, "‹", 34, Ui.CYAN);
        back.setContentDescription("Back");
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 48)));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.title(this, event == null ? "Packet unavailable" : "Packet #" + event.number);
        names.addView(title);
        TextView subtitle = Ui.text(this, event == null ? "The capture is no longer in memory."
                : event.protocol + "  •  " + event.endpointSource() + " → " + event.endpointDestination(),
                12, Ui.CYAN);
        names.addView(subtitle);
        header.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        if (event != null) {
            addSection(content, "PACKET LAYERS", event.layerDescription());
            String preview = event.asciiPayloadPreview();
            if (!preview.isEmpty()) addSection(content, "PAYLOAD PREVIEW", preview);
            addSection(content, "HEX + ASCII", event.hexDump());
        }
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        Ui.applySystemBarInsets(this, root);
    }

    private void addSection(LinearLayout parent, String heading, String body) {
        TextView label = Ui.text(this, heading, 12, Ui.CYAN);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        parent.addView(label, Ui.margins(this, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 12, 0, 6));
        TextView value = Ui.text(this, body, 13, Ui.TEXT);
        value.setTypeface(Typeface.MONOSPACE);
        value.setTextIsSelectable(true);
        value.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        value.setBackground(Ui.rounded(Ui.PANEL, 10, 0xff243442));
        parent.addView(value);
    }
}
