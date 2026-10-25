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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private LinearLayout root;
    private EditText usernameInput, searchInput, messageInput;
    private ScrollView messagesScroll;

    private String currentUid = "";
    private String currentName = "";
    private String chatId = "";
    private String otherUid = "";
    private String otherName = "";

    private ListenerRegistration messagesListener;

    private final int dark = Color.rgb(18, 35, 29);
    private final int green = Color.rgb(20, 100, 65);
    private final int gold = Color.rgb(239, 193, 77);
    private final int white = Color.WHITE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        setupScreen();
        signInAnonymously();
    }

    private void setupScreen() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 16, 20, 16);
        root.setBackgroundColor(dark);
        setContentView(root);
    }

    private void signInAnonymously() {
        if (auth.getCurrentUser() != null) {
            currentUid = auth.getCurrentUser().getUid();
            loadMyProfile();
            return;
        }

        showLoading("جارٍ الدخول إلى مراسل...");

        auth.signInAnonymously()
                .addOnSuccessListener(result -> {
                    if (auth.getCurrentUser() == null) {
                        showWelcome();
                        return;
                    }

                    currentUid = auth.getCurrentUser().getUid();
                    loadMyProfile();
                })
                .addOnFailureListener(e -> {
                    showWelcome();
                    toast("فشل الدخول: " + e.getLocalizedMessage());
                });
    }

    private void showLoading(String text) {
        root.removeAllViews();

        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(gold);
        label.setTextSize(22);
        label.setGravity(Gravity.CENTER);

        root.addView(label, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void showWelcome() {
        root.removeAllViews();

        addTitle("مراسل");
        addText("تطبيق المراسلة والمحادثة");
        addText("دخول بدون رقم هاتف أو بريد إلكتروني.");

        addButton("الدخول إلى التطبيق", this::signInAnonymously);
    }

    private void loadMyProfile() {
        if (auth.getCurrentUser() == null) {
            showWelcome();
            return;
        }

        currentUid = auth.getCurrentUser().getUid();

        db.collection("users").document(currentUid).get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) {
                        showCreateProfile();
                        return;
                    }

                    String name = document.getString("username");

                    if (name == null || name.trim().isEmpty()) {
                        showCreateProfile();
                    } else {
                        currentName = name;
                        showHome();
                    }
                })
                .addOnFailureListener(e -> {
                    showWelcome();
                    toast("تعذر تحميل الحساب: " + e.getLocalizedMessage());
                });
    }

    private void showCreateProfile() {
        root.removeAllViews();

        addTitle("إنشاء حساب مراسل");
        addText("اختر اسم المستخدم الذي سيظهر للآخرين.");

        usernameInput = addInput(
                "اسم المستخدم",
                InputType.TYPE_CLASS_TEXT
        );

        addButton("حفظ ومتابعة", () -> {
            String name = usernameInput.getText().toString().trim();

            if (name.length() < 3) {
                toast("اكتب اسمًا من 3 أحرف على الأقل.");
                return;
            }

            String normalized = name.toLowerCase(Locale.ROOT);

            Map<String, Object> user = new HashMap<>();
            user.put("uid", currentUid);
            user.put("username", name);
            user.put("usernameLower", normalized);
            user.put("online", true);
            user.put("createdAt", FieldValue.serverTimestamp());

            db.collection("users").document(currentUid)
                    .set(user)
                    .addOnSuccessListener(unused -> {
                        currentName = name;
                        showHome();
                    })
                    .addOnFailureListener(e ->
                            toast("تعذر حفظ الحساب: " + e.getLocalizedMessage()));
        });
    }

    private void showHome() {
        removeMessagesListener();
        root.removeAllViews();

        addTitle("مراسل");
        addText("مرحبًا، " + currentName);

        searchInput = addInput(
                "ابحث باسم المستخدم",
                InputType.TYPE_CLASS_TEXT
        );

        addButton("بحث عن مستخدم", this::findUser);
        addButton("تحديث الصفحة", this::showHome);

        addText("اكتب اسم المستخدم لبدء محادثة.");
    }

    private void findUser() {
        String name = searchInput.getText().toString().trim();

        if (name.isEmpty()) {
            toast("اكتب اسم المستخدم أولًا.");
            return;
        }

        db.collection("users")
                .whereEqualTo("usernameLower", name.toLowerCase(Locale.ROOT))
                .limit(1)
                .get()
                .addOnSuccessListener(result -> {
                    if (result.isEmpty()) {
                        toast("لم يتم العثور على المستخدم.");
                        return;
                    }

                    DocumentSnapshot user = result.getDocuments().get(0);
                    String uid = user.getString("uid");
                    String username = user.getString("username");

                    if (uid == null || uid.equals(currentUid)) {
                        toast("هذا حسابك أنت.");
                        return;
                    }

                    otherUid = uid;
                    otherName = username == null ? "مستخدم" : username;

                    openOrCreateChat();
                })
                .addOnFailureListener(e ->
                        toast("فشل البحث: " + e.getLocalizedMessage()));
    }

    private void openOrCreateChat() {
        String first = currentUid.compareTo(otherUid) < 0
                ? currentUid : otherUid;

        String second = currentUid.compareTo(otherUid) < 0
                ? otherUid : currentUid;

        chatId = first + "_" + second;

        Map<String, Object> chat = new HashMap<>();
        chat.put("participants", Arrays.asList(first, second));
        chat.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("chats").document(chatId)
                .set(chat, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(unused -> openChat())
                .addOnFailureListener(e ->
                        toast("تعذر فتح المحادثة: " + e.getLocalizedMessage()));
    }

    private void openChat() {
        removeMessagesListener();
        root.removeAllViews();

        addTitle("محادثة مع " + otherName);
        addButton("رجوع", this::showHome);

        messagesScroll = new ScrollView(this);

        LinearLayout messagesLayout = new LinearLayout(this);
        messagesLayout.setOrientation(LinearLayout.VERTICAL);
        messagesLayout.setPadding(4, 4, 4, 4);

        messagesScroll.addView(messagesLayout);

        LinearLayout.LayoutParams scrollParams =
                new LinearLayout.LayoutParams(-1, 0, 1);

        scrollParams.setMargins(0, 8, 0, 8);
        root.addView(messagesScroll, scrollParams);

        LinearLayout sendRow = new LinearLayout(this);
        sendRow.setOrientation(LinearLayout.HORIZONTAL);

        messageInput = new EditText(this);
        messageInput.setHint("اكتب رسالتك...");
        messageInput.setTextColor(white);
        messageInput.setHintTextColor(Color.LTGRAY);
        messageInput.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );

        sendRow.addView(messageInput,
                new LinearLayout.LayoutParams(0, -2, 1));

        Button sendButton = makeButton("إرسال");
        sendButton.setOnClickListener(v -> sendMessage());
        sendRow.addView(sendButton);

        root.addView(sendRow);

        messagesListener = db.collection("chats")
                .document(chatId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        toast("تعذر تحميل الرسائل: " + error.getLocalizedMessage());
                        return;
                    }

                    if (snapshots == null) return;

                    messagesLayout.removeAllViews();

                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        String sender = doc.getString("senderId");
                        String text = doc.getString("text");

                        if (text == null) continue;

                        TextView bubble = new TextView(this);
                        bubble.setText(text);
                        bubble.setTextColor(white);
                        bubble.setTextSize(16);
                        bubble.setPadding(14, 10, 14, 10);

                        GradientDrawable shape = new GradientDrawable();
                        shape.setColor(currentUid.equals(sender)
                                ? green : Color.rgb(52, 65, 59));
                        shape.setCornerRadius(18);
                        bubble.setBackground(shape);

                        LinearLayout.LayoutParams params =
                                new LinearLayout.LayoutParams(-2, -2);

                        params.setMargins(0, 5, 0, 5);
                        params.gravity = currentUid.equals(sender)
                                ? Gravity.END : Gravity.START;

                        messagesLayout.addView(bubble, params);
                    }

                    messagesScroll.post(() ->
                            messagesScroll.fullScroll(View.FOCUS_DOWN));
                });
    }

    private void sendMessage() {
        if (messageInput == null || chatId.isEmpty()) return;

        String text = messageInput.getText().toString().trim();

        if (text.isEmpty()) return;

        if (text.length() > 2000) {
            toast("الرسالة طويلة جدًا.");
            return;
        }

        Map<String, Object> message = new HashMap<>();
        message.put("senderId", currentUid);
        message.put("text", text);
        message.put("createdAt", FieldValue.serverTimestamp());

        db.collection("chats")
                .document(chatId)
                .collection("messages")
                .add(message)
                .addOnSuccessListener(ref -> {
                    if (messageInput != null) {
                        messageInput.setText("");
                    }

                    db.collection("chats").document(chatId)
                            .update("updatedAt", FieldValue.serverTimestamp());
                })
                .addOnFailureListener(e ->
                        toast("تعذر إرسال الرسالة: " + e.getLocalizedMessage()));
    }

    private void removeMessagesListener() {
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }
    }

    private void addTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(gold);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 12, 0, 12);

        root.addView(title);
    }

    private void addText(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(white);
        label.setTextSize(16);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, 8, 0, 12);

        root.addView(label);
    }

    private EditText addInput(String hint, int inputType) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setTextColor(white);
        edit.setHintTextColor(Color.LTGRAY);
        edit.setInputType(inputType);
        edit.setSingleLine(true);
        edit.setPadding(14, 8, 14, 8);

        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.rgb(35, 52, 44));
        shape.setCornerRadius(14);
        shape.setStroke(1, gold);

        edit.setBackground(shape);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);

        params.setMargins(0, 8, 0, 8);
        root.addView(edit, params);

        return edit;
    }

    private void addButton(String text, Runnable action) {
        Button button = makeButton(text);
        button.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);

        params.setMargins(0, 6, 0, 6);
        root.addView(button, params);
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(dark);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(gold)
        );

        return button;
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        removeMessagesListener();
        super.onDestroy();
    }
            }
