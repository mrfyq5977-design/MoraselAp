
package com.morasel.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

public class PhoneAuthActivity extends Activity {

    private final int green = Color.rgb(12, 75, 48);
    private final int gold = Color.rgb(242, 197, 76);
    private final int dark = Color.rgb(18, 37, 30);

    private FirebaseAuth auth;
    private EditText phoneInput;
    private EditText codeInput;
    private Button sendButton;
    private Button verifyButton;
    private TextView statusText;

    private String verificationId;
    private PhoneAuthProvider.ForceResendingToken resendToken;

    private final PhoneAuthProvider.OnVerificationStateChangedCallbacks callbacks =
            new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        @Override
        public void onVerificationCompleted(PhoneAuthCredential credential) {
            signInWithCredential(credential);
        }

        @Override
        public void onVerificationFailed(FirebaseException e) {
            sendButton.setEnabled(true);
            statusText.setText("تعذر إرسال أو التحقق من الرمز.");
            Toast.makeText(
                    PhoneAuthActivity.this,
                    e.getLocalizedMessage() == null
                            ? "تحقق من رقم الهاتف وإعدادات Firebase"
                            : e.getLocalizedMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }

        @Override
        public void onCodeSent(
                String id,
                PhoneAuthProvider.ForceResendingToken token) {
            verificationId = id;
            resendToken = token;

            codeInput.setVisibility(View.VISIBLE);
            verifyButton.setVisibility(View.VISIBLE);
            sendButton.setText("إعادة إرسال الرمز");
            sendButton.setEnabled(true);

            statusText.setText("أدخل رمز التحقق الذي وصلك برسالة SMS.");
            Toast.makeText(
                    PhoneAuthActivity.this,
                    "تم طلب رمز التحقق",
                    Toast.LENGTH_SHORT
            ).show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();

        getWindow().setStatusBarColor(dark);
        getWindow().setNavigationBarColor(dark);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22), dp(35), dp(22), dp(20));
        root.setBackgroundColor(green);

        TextView title = new TextView(this);
        title.setText("مراسل");
        title.setTextColor(gold);
        title.setTextSize(32);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);

        TextView subtitle = new TextView(this);
        subtitle.setText("تسجيل الدخول برقم الهاتف");
        subtitle.setTextColor(Color.WHITE);
        subtitle.setTextSize(19);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(12), 0, dp(28));

        phoneInput = new EditText(this);
        phoneInput.setSingleLine(true);
        phoneInput.setHint("رقم الهاتف مع مفتاح الدولة");
        phoneInput.setTextColor(Color.WHITE);
        phoneInput.setHintTextColor(0xFFCFD8D3);
        phoneInput.setInputType(InputType.TYPE_CLASS_PHONE);
        phoneInput.setTextDirection(View.TEXT_DIRECTION_LTR);
        phoneInput.setGravity(Gravity.CENTER);
        styleInput(phoneInput);

        codeInput = new EditText(this);
        codeInput.setSingleLine(true);
        codeInput.setHint("رمز التحقق SMS");
        codeInput.setTextColor(Color.WHITE);
        codeInput.setHintTextColor(0xFFCFD8D3);
        codeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        codeInput.setTextDirection(View.TEXT_DIRECTION_LTR);
        codeInput.setGravity(Gravity.CENTER);
        styleInput(codeInput);
        codeInput.setVisibility(View.GONE);

        sendButton = makeButton("إرسال رمز التحقق");
        verifyButton = makeButton("تأكيد الرمز والدخول");
        verifyButton.setVisibility(View.GONE);

        statusText = new TextView(this);
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(15);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(18), 0, dp(10));

        TextView oldLoginText = new TextView(this);
        oldLoginText.setText("يمكنك الرجوع إلى شاشة مراسل لاستخدام طريقة الدخول القديمة.");
        oldLoginText.setTextColor(0xFFE7E7E7);
        oldLoginText.setTextSize(14);
        oldLoginText.setGravity(Gravity.CENTER);
        oldLoginText.setPadding(0, dp(24), 0, 0);

        root.addView(title, matchWrap());
        root.addView(subtitle, matchWrap());
        root.addView(phoneInput, matchWrap());
        addSpace(root, 12);
        root.addView(sendButton, matchWrap());
        addSpace(root, 12);
        root.addView(codeInput, matchWrap());
        addSpace(root, 12);
        root.addView(verifyButton, matchWrap());
        root.addView(statusText, matchWrap());
        root.addView(oldLoginText, matchWrap());

        setContentView(root);

        sendButton.setOnClickListener(v -> sendCode());
        verifyButton.setOnClickListener(v -> verifyCode());
    }

    private void sendCode() {
        String phone = phoneInput.getText().toString().trim();

        if (phone.isEmpty() || !phone.startsWith("+")
                || phone.length() < 8) {
            phoneInput.setError(
                    "اكتب الرقم مع مفتاح الدولة، مثل +967XXXXXXXXX"
            );
            return;
        }

        sendButton.setEnabled(false);
        statusText.setText("جارٍ طلب رمز التحقق...");

        PhoneAuthOptions options =
                PhoneAuthOptions.newBuilder(auth)
                        .setPhoneNumber(phone)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(this)
                        .setCallbacks(callbacks)
                        .build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void verifyCode() {
        String code = codeInput.getText().toString().trim();

        if (verificationId == null) {
            Toast.makeText(
                    this,
                    "اطلب رمز التحقق أولًا",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (code.isEmpty()) {
            codeInput.setError("أدخل رمز التحقق");
            return;
        }

        PhoneAuthCredential credential =
                PhoneAuthProvider.getCredential(verificationId, code);

        signInWithCredential(credential);
    }

    private void signInWithCredential(PhoneAuthCredential credential) {
        verifyButton.setEnabled(false);
        statusText.setText("جارٍ التحقق من رقم الهاتف...");

        auth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    verifyButton.setEnabled(true);

                    if (task.isSuccessful()) {
                        Toast.makeText(
                                this,
                                "تم التحقق من رقم الهاتف",
                                Toast.LENGTH_LONG
                        ).show();

                        setResult(Activity.RESULT_OK);
                        finish();
                    } else {
                        statusText.setText("فشل التحقق. حاول مجددًا.");
                        Toast.makeText(
                                this,
                                task.getException() == null
                                        ? "تعذر تسجيل الدخول"
                                        : task.getException()
                                                .getLocalizedMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(dark);
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(gold);
        bg.setCornerRadius(dp(14));
        button.setBackground(bg);

        return button;
    }

    private void styleInput(EditText input) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1A4434);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), gold);
        input.setBackground(bg);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private void addSpace(LinearLayout root, int heightDp) {
        View space = new View(this);
        root.addView(
                space,
                new LinearLayout.LayoutParams(
                        1,
                        dp(heightDp)
                )
        );
    }

    private int dp(int value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density + 0.5f);
    }
}
