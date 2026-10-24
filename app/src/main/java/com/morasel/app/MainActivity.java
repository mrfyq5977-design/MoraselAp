package com.morasel.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import com.google.firebase.firestore.Query;

import java.util.*;

public class MainActivity extends Activity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private LinearLayout root, messages;
    private EditText emailInput, passwordInput;
    private EditText otherEmailInput, messageInput;
    private ScrollView scroll;

    private ListenerRegistration messageListener;
    private String chatId;

    private final int bg = Color.rgb(16, 27, 61);
    private final int card = Color.rgb(25, 48, 83);
    private final int accent = Color.rgb(56, 189, 248);
    private final int white = Color.WHITE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseApp.initializeApp(this);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() == null) {
            showLogin();
        } else {
            saveUserAndContinue(auth.getCurrentUser().getEmail());
        }
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private TextView label(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(10, 10, 10, 10);
        return t;
    }

    private EditText field(String hint, boolean password) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextColor(white);
        e.setHintTextColor(Color.LTGRAY);
        e.setTextSize(16);
        e.setSingleLine(true);
        e.setPadding(14, 8, 14, 8);
        e.setBackground(rounded(card, 16));
        e.setInputType(password ? 129 : 33);

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, 54);
        p.setMargins(0, 7, 0, 7);
        e.setLayoutParams(p);
        return e;
    }

    private Button button(String title) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextColor(bg);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(accent, 16));

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, 52);
        p.setMargins(0, 7, 0, 7);
        b.setLayoutParams(p);
        return b;
    }

    private void baseScreen() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(18, 16, 18, 16);
        root.setBackgroundColor(bg);
        setContentView(root);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && root != null) {
                imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
        }
    }

    private void showLogin() {
        baseScreen();

        root.addView(label("مراسل", 32, accent));
        root.addView(label("أنشئ حسابًا أو سجّل الدخول", 17, white));

        emailInput = field("البريد الإلكتروني", false);
        root.addView(emailInput);

        passwordInput = field("كلمة المرور (6 أحرف على الأقل)", true);
        root.addView(passwordInput);

        Button register = button("إنشاء حساب جديد");
        root.addView(register);

        Button login = button("تسجيل الدخول");
        root.addView(login);

        register.setOnClickListener(v -> authenticate(true));
        login.setOnClickListener(v -> authenticate(false));
    }

    private void authenticate(boolean createAccount) {
        String email = emailInput.getText().toString()
                .trim().toLowerCase(Locale.ROOT);
        String password = passwordInput.getText().toString();

        if (email.isEmpty() || !email.contains("@")) {
            emailInput.setError("أدخل بريدًا إلكترونيًا صحيحًا");
            return;
        }

        if (password.length() < 6) {
            passwordInput.setError("كلمة المرور يجب أن تكون 6 أحرف على الأقل");
            return;
        }

        hideKeyboard();

        if (createAccount) {
            auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener(result ->
                            saveUserAndContinue(email))
                    .addOnFailureListener(e ->
                            toast("تعذّر إنشاء الحساب: "
                                    + e.getLocalizedMessage()));
        } else {
            auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener(result ->
                            saveUserAndContinue(email))
                    .addOnFailureListener(e ->
                            toast("تعذّر تسجيل الدخول: "
                                    + e.getLocalizedMessage()));
        }
    }

    private void saveUserAndContinue(String email) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            showLogin();
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("email", email == null ? "" :
                email.trim().toLowerCase(Locale.ROOT));
        data.put("uid", user.getUid());

        db.collection("users").document(user.getUid())
                .set(data, SetOptions.merge())
                .addOnSuccessListener(v -> showContacts())
                .addOnFailureListener(e ->
                        toast("تعذّر حفظ بيانات الحساب: "
                                + e.getLocalizedMessage()));
    }

    private void showContacts() {
        removeMessageListener();
        chatId = null;

        baseScreen();

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            showLogin();
            return;
        }

        root.addView(label("مراسل", 30, accent));
        root.addView(label("حسابك: " + user.getEmail(), 14, white));
        root.addView(label("أدخل بريد الشخص الذي تريد مراسلته", 16, white));

        otherEmailInput = field("بريد الشخص الآخر", false);
        root.addView(otherEmailInput);

        Button open = button("بحث وفتح المحادثة");
        root.addView(open);

        Button logout = button("تسجيل الخروج");
        root.addView(logout);

        open.setOnClickListener(v -> findPerson());

        logout.setOnClickListener(v -> {
            removeMessageListener();
            auth.signOut();
            showLogin();
        });
    }

    private void findPerson() {
        FirebaseUser me = auth.getCurrentUser();

        if (me == null) {
            showLogin();
            return;
        }

        String email = otherEmailInput.getText().toString()
                .trim().toLowerCase(Locale.ROOT);

        if (email.isEmpty() || !email.contains("@")) {
            otherEmailInput.setError("أدخل بريدًا صحيحًا");
            return;
        }

        if (email.equalsIgnoreCase(me.getEmail())) {
            toast("أدخل بريد حساب آخر");
            return;
        }

        hideKeyboard();

        db.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        toast("لم نجد الحساب. اطلب من الشخص إنشاء حساب أولًا.");
                        return;
                    }

                    DocumentSnapshot person =
                            snapshot.getDocuments().get(0);

                    String theirUid = person.getString("uid");

                    if (theirUid == null || theirUid.isEmpty()) {
                        theirUid = person.getId();
                    }

                    openChat(me.getUid(), theirUid);
                })
                .addOnFailureListener(e ->
                        toast("فشل البحث: " + e.getLocalizedMessage()));
    }

    private void openChat(String myUid, String theirUid) {
        ArrayList<String> participants = new ArrayList<>();
        participants.add(myUid);
        participants.add(theirUid);
        Collections.sort(participants);

        chatId = participants.get(0) + "_" + participants.get(1);

        Map<String, Object> chat = new HashMap<>();
        chat.put("participants", participants);

        db.collection("chats").document(chatId)
                .set(chat, SetOptions.merge())
                .addOnSuccessListener(v -> showChat())
                .addOnFailureListener(e ->
                        toast("تعذّر فتح المحادثة: "
                                + e.getLocalizedMessage()));
    }

    private void showChat() {
        removeMessageListener();
        baseScreen();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        Button back = button("رجوع");
        header.addView(back, new LinearLayout.LayoutParams(100, 50));

        header.addView(label("المحادثة", 22, accent),
                new LinearLayout.LayoutParams(0, 55, 1));

        root.addView(header);
        back.setOnClickListener(v -> showContacts());

        scroll = new ScrollView(this);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(4, 10, 4, 10);
        scroll.addView(messages);

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);

        messageInput = new EditText(this);
        messageInput.setHint("اكتب رسالتك...");
        messageInput.setTextColor(white);
        messageInput.setHintTextColor(Color.LTGRAY);
        messageInput.setTextSize(16);
        messageInput.setPadding(12, 8, 12, 8);
        messageInput.setBackground(rounded(card, 18));

        composer.addView(messageInput,
                new LinearLayout.LayoutParams(0, 54, 1));

        Button send = button("إرسال");
        composer.addView(send,
                new LinearLayout.LayoutParams(95, 54));

        root.addView(composer);
        send.setOnClickListener(v -> sendMessage());

        listenForMessages();
    }

    private void listenForMessages() {
        if (chatId == null) return;

        messageListener = db.collection("chats").document(chatId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        toast("تعذّر استقبال الرسائل: "
                                + error.getLocalizedMessage());
                        return;
                    }

                    if (snapshot == null || messages == null) return;

                    messages.removeAllViews();

                    FirebaseUser user = auth.getCurrentUser();
                    String myUid = user == null ? "" : user.getUid();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String text = doc.getString("text");
                        String sender = doc.getString("senderId");

                        if (text != null) {
                            addBubble(text, myUid.equals(sender));
                        }
                    }

                    scroll.post(() ->
                            scroll.fullScroll(View.FOCUS_DOWN));
                });
    }

    private void sendMessage() {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null || chatId == null) {
            toast("افتح محادثة أولًا");
            return;
        }

        String value = messageInput.getText().toString().trim();

        if (value.isEmpty()) {
            messageInput.setError("اكتب رسالة أولًا");
            return;
        }

        if (value.length() > 2000) {
            messageInput.setError("الحد الأقصى 2000 حرف");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("text", value);
        data.put("senderId", user.getUid());
        data.put("createdAt", FieldValue.serverTimestamp());

        db.collection("chats").document(chatId)
                .collection("messages")
                .add(data)
                .addOnSuccessListener(ref -> {
                    messageInput.setText("");
                    hideKeyboard();
                })
                .addOnFailureListener(e ->
                        toast("فشل إرسال الرسالة: "
                                + e.getLocalizedMessage()));
    }

    private void addBubble(String value, boolean mine) {
        TextView bubble = new TextView(this);
        bubble.setText(value);
        bubble.setTextColor(white);
        bubble.setTextSize(16);
        bubble.setPadding(14, 12, 14, 12);
        bubble.setMaxWidth((int) (
                getResources().getDisplayMetrics().widthPixels * 0.75f));

        bubble.setBackground(rounded(
                mine ? Color.rgb(8, 105, 150) : card, 18));

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-2, -2);
        p.setMargins(0, 5, 0, 5);
        p.gravity = mine ? Gravity.LEFT : Gravity.RIGHT;

        messages.addView(bubble, p);
    }

    private void removeMessageListener() {
        if (messageListener != null) {
            messageListener.remove();
            messageListener = null;
        }
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        removeMessageListener();
        super.onDestroy();
    }
    }
