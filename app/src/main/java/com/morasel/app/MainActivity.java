package com.morasel.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.*;

public class MainActivity extends Activity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private LinearLayout root, list;
    private EditText messageInput;
    private ListenerRegistration chatsListener;
    private ListenerRegistration messagesListener;

    private String uid = "";
    private String username = "";
    private String activeChat = "";

    private final int BG = Color.rgb(17, 35, 28);
    private final int GREEN = Color.rgb(24, 104, 68);
    private final int GOLD = Color.rgb(239, 193, 77);
    private final int WHITE = Color.WHITE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 12, 16, 12);
        root.setBackgroundColor(BG);
        setContentView(root);

        login();
    }

    private void login() {
        if (auth.getCurrentUser() != null) {
            uid = auth.getCurrentUser().getUid();
            loadProfile();
            return;
        }

        showLoading("جارٍ فتح مراسل...");

        auth.signInAnonymously()
            .addOnSuccessListener(result -> {
                uid = result.getUser().getUid();
                loadProfile();
            })
            .addOnFailureListener(e -> {
                showWelcome();
                toast("تعذر الدخول: " + e.getLocalizedMessage());
            });
    }

    private void loadProfile() {
        db.collection("users").document(uid).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists() && doc.getString("username") != null) {
                    username = doc.getString("username");
                    showHome();
                } else {
                    showCreateProfile();
                }
            })
            .addOnFailureListener(e -> {
                showWelcome();
                toast("تعذر تحميل الحساب.");
            });
    }

    private void showLoading(String value) {
        root.removeAllViews();
        TextView t = makeText(value, 22, GOLD);
        t.setGravity(Gravity.CENTER);
        root.addView(t, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void showWelcome() {
        root.removeAllViews();
        addHeading("مراسل");
        addText("تواصل مع الآخرين بسهولة.");
        addButton("إعادة المحاولة", this::login);
    }

    private void showCreateProfile() {
        root.removeAllViews();
        addHeading("إنشاء حساب");

        EditText name = addInput(
            "اختر اسم المستخدم",
            InputType.TYPE_CLASS_TEXT
        );

        addButton("حفظ ومتابعة", () -> {
            String n = name.getText().toString().trim();

            if (n.length() < 3) {
                toast("اسم المستخدم يجب أن يكون 3 أحرف على الأقل.");
                return;
            }

            String lower = n.toLowerCase(Locale.ROOT);

            db.collection("users")
                .whereEqualTo("usernameLower", lower)
                .get()
                .addOnSuccessListener(found -> {
                    if (!found.isEmpty()) {
                        toast("اسم المستخدم مستخدم، اختر اسمًا آخر.");
                        return;
                    }

                    Map<String, Object> user = new HashMap<>();
                    user.put("uid", uid);
                    user.put("username", n);
                    user.put("usernameLower", lower);
                    user.put("createdAt", FieldValue.serverTimestamp());

                    db.collection("users").document(uid)
                        .set(user)
                        .addOnSuccessListener(v -> {
                            username = n;
                            showHome();
                        })
                        .addOnFailureListener(e ->
                            toast("تعذر حفظ الاسم."));
                })
                .addOnFailureListener(e ->
                    toast("تعذر التحقق من الاسم."));
        });
    }

    private void showHome() {
        stopChatListeners();

        if (chatsListener != null) {
            chatsListener.remove();
            chatsListener = null;
        }

        root.removeAllViews();
        addHeading("مراسل");
        addText("مرحبًا، " + username);

        EditText search = addInput(
            "🔍 ابحث باسم المستخدم",
            InputType.TYPE_CLASS_TEXT
        );

        addButton("بحث عن مستخدم", () -> {
            String name = search.getText().toString().trim()
                .toLowerCase(Locale.ROOT);

            if (name.isEmpty()) {
                toast("اكتب اسم المستخدم.");
                return;
            }

            db.collection("users")
                .whereEqualTo("usernameLower", name)
                .limit(1)
                .get()
                .addOnSuccessListener(result -> {
                    if (result.isEmpty()) {
                        toast("لم يتم العثور على المستخدم.");
                        return;
                    }

                    DocumentSnapshot user = result.getDocuments().get(0);
                    String partnerUid = user.getString("uid");
                    String partnerName = user.getString("username");

                    if (partnerUid == null || partnerUid.equals(uid)) {
                        toast("لا يمكنك مراسلة نفسك.");
                        return;
                    }

                    openOrCreateChat(partnerUid, partnerName);
                })
                .addOnFailureListener(e ->
                    toast("فشل البحث عن المستخدم."));
        });

        addText("💬 محادثاتك");

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);

        root.addView(scroll,
            new LinearLayout.LayoutParams(-1, 0, 1));

        listenChats();
    }

    private void listenChats() {
        chatsListener = db.collection("chats")
            .whereArrayContains("participants", uid)
            .addSnapshotListener((snapshot, error) -> {
                if (error != null) {
                    toast("تعذر تحميل المحادثات.");
                    return;
                }

                if (snapshot == null) return;

                list.removeAllViews();

                for (DocumentSnapshot chat : snapshot.getDocuments()) {
                    List<String> participants =
                        (List<String>) chat.get("participants");

                    if (participants == null) continue;

                    String partnerUid = null;

                    for (String p : participants) {
                        if (!uid.equals(p)) {
                            partnerUid = p;
                            break;
                        }
                    }

                    if (partnerUid == null) continue;

                    String other = partnerUid;

                    db.collection("users").document(other).get()
                        .addOnSuccessListener(user -> {
                            if (list == null) return;

                            String name = user.getString("username");
                            if (name == null) name = "مستخدم";

                            Button button = makeButton("💬  " + name);
                            String finalName = name;

                            button.setOnClickListener(v ->
                                openChat(chat.getId(), other, finalName));

                            list.addView(button);
                        });
                }

                if (snapshot.isEmpty()) {
                    TextView empty = makeText(
                        "لا توجد محادثات بعد.",
                        16,
                        WHITE
                    );
                    empty.setGravity(Gravity.CENTER);
                    list.addView(empty);
                }
            });
    }

    private void openOrCreateChat(
        String partnerUid,
        String partnerName
    ) {
        String first = uid.compareTo(partnerUid) < 0
            ? uid : partnerUid;

        String second = uid.compareTo(partnerUid) < 0
            ? partnerUid : uid;

        String chatId = first + "_" + second;

        Map<String, Object> chat = new HashMap<>();
        chat.put("participants", Arrays.asList(first, second));
        chat.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("chats").document(chatId)
            .set(chat, SetOptions.merge())
            .addOnSuccessListener(v ->
                openChat(chatId, partnerUid, partnerName))
            .addOnFailureListener(e ->
                toast("تعذر فتح المحادثة."));
    }

    private void openChat(
        String chatId,
        String partnerUid,
        String partnerName
    ) {
        stopChatListeners();
        activeChat = chatId;

        root.removeAllViews();
        addHeading("💬 " + partnerName);
        addButton("← رجوع", this::showHome);

        ScrollView scroll = new ScrollView(this);
        LinearLayout messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(4, 4, 4, 4);
        scroll.addView(messages);

        root.addView(scroll,
            new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);

        messageInput = new EditText(this);
        messageInput.setHint("اكتب رسالتك...");
        messageInput.setTextColor(WHITE);
        messageInput.setHintTextColor(Color.LTGRAY);
        messageInput.setInputType(
            InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );

        bottom.addView(messageInput,
            new LinearLayout.LayoutParams(0, -2, 1));

        Button send = makeButton("إرسال");
        send.setOnClickListener(v -> sendMessage());
        bottom.addView(send);
        root.addView(bottom);

        messagesListener = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener((snapshot, error) -> {
                if (error != null) {
                    toast("تعذر تحميل الرسائل.");
                    return;
                }

                if (snapshot == null) return;

                messages.removeAllViews();

                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    String body = doc.getString("text");
                    String sender = doc.getString("senderId");

                    if (body == null) continue;

                    TextView bubble = makeText(body, 16, WHITE);
                    bubble.setPadding(12, 10, 12, 10);

                    GradientDrawable shape = new GradientDrawable();
                    shape.setColor(uid.equals(sender)
                        ? GREEN : Color.rgb(52, 65, 59));
                    shape.setCornerRadius(16);
                    bubble.setBackground(shape);

                    LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(-2, -2);

                    params.setMargins(0, 5, 0, 5);
                    params.gravity = uid.equals(sender)
                        ? Gravity.END : Gravity.START;

                    messages.addView(bubble, params);
                }

                scroll.post(() ->
                    scroll.fullScroll(View.FOCUS_DOWN));
            });
    }

    private void sendMessage() {
        if (messageInput == null || activeChat.isEmpty()) return;

        String body = messageInput.getText().toString().trim();

        if (body.isEmpty()) return;

        if (body.length() > 2000) {
            toast("الرسالة طويلة جدًا.");
            return;
        }

        Map<String, Object> message = new HashMap<>();
        message.put("senderId", uid);
        message.put("text", body);
        message.put("createdAt", FieldValue.serverTimestamp());

        db.collection("chats").document(activeChat)
            .collection("messages")
            .add(message)
            .addOnSuccessListener(ref -> {
                if (messageInput != null) {
                    messageInput.setText("");
                }

                db.collection("chats").document(activeChat)
                    .update("updatedAt", FieldValue.serverTimestamp());
            })
            .addOnFailureListener(e ->
                toast("تعذر إرسال الرسالة."));
    }

    private void stopChatListeners() {
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }
    }

    private void addHeading(String value) {
        TextView t = makeText(value, 27, GOLD);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 12, 0, 12);
        root.addView(t);
    }

    private void addText(String value) {
        TextView t = makeText(value, 16, WHITE);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 8, 0, 10);
        root.addView(t);
    }

    private TextView makeText(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private EditText addInput(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextColor(WHITE);
        e.setHintTextColor(Color.LTGRAY);
        e.setInputType(type);
        root.addView(e);
        return e;
    }

    private Button makeButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(BG);

        GradientDrawable shape = new GradientDrawable();
        shape.setColor(GOLD);
        shape.setCornerRadius(18);
        b.setBackground(shape);

        return b;
    }

    private void addButton(String value, Runnable action) {
        Button b = makeButton(value);
        b.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(-1, -2);

        params.setMargins(0, 6, 0, 6);
        root.addView(b, params);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        stopChatListeners();

        if (chatsListener != null) {
            chatsListener.remove();
            chatsListener = null;
        }

        super.onDestroy();
    }
                              }
