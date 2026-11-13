package io.github.zegamingx.isqu.compatibility;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import kotlin.Unit;

/**
 * Packaging smoke test for Kotlin/JVM artifacts consumed by an Android API 33 app.
 */
public final class CompatibilityActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView status = new TextView(this);
        status.setText("ISQU Android API 33 compatibility: " + Unit.INSTANCE);
        setContentView(status);
    }
}
