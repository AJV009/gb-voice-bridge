package com.ajv009.gbvoice;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

/** One screen: model status/download, the values to paste into Gadgetbridge, the last transcript. */
public class MainActivity extends Activity {
    private TextView status;
    private TextView last;
    private Button download;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final int pad = (int) (24 * getResources().getDisplayMetrics().density);
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad * 2, pad, pad);

        final TextView title = new TextView(this);
        title.setText("GB Voice Bridge");
        title.setTextSize(22);

        status = new TextView(this);
        status.setPadding(0, pad / 2, 0, 0);

        download = new Button(this);
        download.setText("Download Whisper small.en (~375 MB)");
        download.setOnClickListener(v -> startDownload());

        final TextView help = new TextView(this);
        help.setTextIsSelectable(true);
        help.setPadding(0, pad, 0, 0);
        help.setText("In Gadgetbridge: device settings → Voice assistant → Voice Service\n\n"
                + "Voice service package:\n" + getPackageName() + "\n\n"
                + "Voice service class:\n" + VoiceService.class.getName());

        last = new TextView(this);
        last.setTextIsSelectable(true);
        last.setPadding(0, pad, 0, 0);

        root.addView(title);
        root.addView(status);
        root.addView(download);
        root.addView(help);
        root.addView(last);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        final boolean ready = new ModelStore(this).isReady();
        status.setText(ready ? "Model ready, transcription runs on this phone." : "Model not downloaded yet.");
        download.setEnabled(!ready);
        final android.content.SharedPreferences p = getSharedPreferences(VoiceService.PREFS, MODE_PRIVATE);
        final String text = p.getString("last", null);
        last.setText(text == null ? "" : "Last (" + DateFormat.getTimeInstance().format(new Date(p.getLong("at", 0)))
                + "):\n" + text);
    }

    private void startDownload() {
        download.setEnabled(false);
        new Thread(() -> {
            try {
                new ModelStore(this).download((file, done, total) -> runOnUiThread(() ->
                        status.setText(String.format("Downloading %s: %d / %d MB",
                                file, done >> 20, total >> 20))));
                runOnUiThread(this::refresh);
            } catch (final Exception e) {
                runOnUiThread(() -> {
                    status.setText("Download failed: " + e.getMessage());
                    download.setEnabled(true);
                });
            }
        }).start();
    }
}
