
package com.morasel.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private LinearLayout root;
    private LinearLayout messages;
    private EditText input;
    private ScrollView scroll;

    private final int backgroundColor = Color.rgb(16, 27, 61);
    private final int cardColor = Color.rgb(25, 48, 83);
    private final int accentColor = Color.rgb(56, 189, 248);
    private final int whiteColor = Color.WHITE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showChatScreen();
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(12, 10, 12, 10);
        return view;
    }

    private void showChatScreen() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(16, 12, 16, 12);
        root.setBackgroundColor(backgroundColor);

        setContentView(root);

        TextView title = text("مراسل", 28, accentColor);
        root.addView(title, new LinearLayout.LayoutParams(
                -1, 65));

        TextView subtitle = text(
                "تواصل بسهولة مع أصدقائك", 15, whiteColor);
        root.addView(subtitle);

        View line = new View(this);
        line.setBackgroundColor(accentColor);
        root.addView(line, new LinearLayout.LayoutParams(-1, 2));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(4, 12, 4, 12);

        scroll.addView(messages);

        root.addView(scroll, new LinearLayout.LayoutParams(
                -1, 0, 1));

        addMessage("مرحبًا بك في تطبيق مراسل!", false);
        addMessage(
                "هذه نسخة أولية من واجهة المحادثة. الرسائل هنا تظهر على هذا الجهاز فقط.",
                false);

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(0, 10, 0, 0);

        input = new EditText(this);
        input.setSingleLine(false);
        input.setTextColor(whiteColor);
        input.setHintTextColor(Color.LTGRAY);
        input.setHint("اكتب رسالتك...");
        input.setTextSize(16);
        input.setPadding(12, 8, 12, 8);
        input.setBackground(rounded(cardColor, 22));

        composer.addView(input, new LinearLayout.LayoutParams(
                0, 54, 1));

        Button send = new Button(this);
        send.setText("إرسال");
        send.setTextColor(backgroundColor);
        send.setBackground(rounded(accentColor, 22));

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(-2, 54);
        buttonParams.setMargins(8, 0, 0, 0);
        composer.addView(send, buttonParams);

        root.addView(composer);

        send.setOnClickListener(v -> sendMessage());
    }

    private void addMessage(String value, boolean mine) {
        TextView bubble = new TextView(this);
        bubble.setText(value);
        bubble.setTextColor(whiteColor);
        bubble.setTextSize(16);
        bubble.setPadding(14, 12, 14, 12);
        bubble.setBackground(rounded(
                mine ? Color.rgb(8, 105, 150) : cardColor, 18));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -2, -2);
        params.setMargins(0, 5, 0, 5);
        params.gravity = mine ? Gravity.LEFT : Gravity.RIGHT;

        messages.addView(bubble, params);
    }

    private void sendMessage() {
        String value = input.getText().toString().trim();

        if (value.isEmpty()) {
            input.setError("اكتب رسالة أولًا");
            return;
        }

        addMessage(value, true);
        input.setText("");

        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }
}
