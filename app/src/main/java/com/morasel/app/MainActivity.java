package com.morasel.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private LinearLayout root;
    private LinearLayout chatList;
    private EditText messageInput;

    private ListenerRegistration chatsListener;
    private ListenerRegistration messagesListener;

    private String uid = "";
    private String username = "";
    private String activeChat = "";

    private int screenVersion = 0;

    private boolean firstMessagesSnapshot = true;
    private boolean chatSoundEnabled = true;

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

    // =========================
    // الأصوات
    // =========================

    private void playSound(int resourceId) {
        MediaPlayer player = null;

        try {
            player = MediaPlayer.create(this, resourceId);

            if (player == null) {
                return;
            }

            MediaPlayer finalPlayer = player;

            player.setOnCompletionListener(mp -> {
                try {
                    mp.release();
                } catch (Exception ignored) {
                }
            });

            player.setOnErrorListener((mp, what, extra) -> {
                try {
                    mp.release();
                } catch (Exception ignored) {
                }
                return true;
            });

            player.start();

        } catch (Exception e) {
            if (player != null) {
                try {
                    player.release();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void playSendSound() {
        playSound(R.raw.send_message);
    }

    private void playReceiveSound() {
        playSound(R.raw.receive_message);
    }

    private void playChatSound() {
        if (chatSoundEnabled) {
            playSound(R.raw.chat_sound);
        }
    }

    // =========================
    // تسجيل الدخول
    // =========================

    private void login() {
        showLoading("جارٍ فتح مراسل...");

        if (auth.getCurrentUser() != null) {
            uid = auth.getCurrentUser().getUid();
            loadProfile();
            return;
        }

        auth.signInAnonymously()
            .addOnSuccessListener(result -> {
                if (isFinishing() || isDestroyed()) return;

                if (result.getUser() == null) {
                    showWelcome();
                    toast("تعذر إنشاء جلسة الدخول.");
                    return;
                }

                uid = result.getUser().getUid();
                loadProfile();
            })
            .addOnFailureListener(e -> {
                showWelcome();
                toast("تعذر الدخول. تحقق من الإنترنت وإعدادات Firebase.");
            });
    }

    private void loadProfile() {
        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener(doc -> {
                if (isFinishing() || isDestroyed()) return;

                String savedName = doc.getString("username");

                if (doc.exists() && savedName != null
                        && !savedName.trim().isEmpty()) {
                    username = savedName;
                    showHome();
                } else {
                    showCreateProfile();
                }
            })
            .addOnFailureListener(e -> {
                showWelcome();
                toast("تعذر تحميل الحساب. حاول مرة أخرى.");
            });
    }

    // =========================
    // شاشة التحميل والترحيب
    // =========================

    private void showLoading(String value) {
        root.removeAllViews();

        TextView text = makeText(value, 22, GOLD);
        text.setGravity(Gravity.CENTER);

        root.addView(text,
            new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void showWelcome() {
        closeAllListeners();
        root.removeAllViews();

        addHeading("مراسل");
        addText("تواصل مع الآخرين بسهولة.");

        addButton("إعادة المحاولة", this::login);
    }

    // =========================
    // إنشاء الحساب
    // =========================

    private void showCreateProfile() {
        closeAllListeners();
        root.removeAllViews();

        addHeading("إنشاء حساب");
        addText("اختر اسمًا ليتمكن الآخرون من العثور عليك.");

        EditText nameInput = addInput(
            "اسم المستخدم",
            InputType.TYPE_CLASS_TEXT
        );

        addButton("حفظ ومتابعة", () -> {
            String name = nameInput.getText()
                .toString().trim();

            if (name.length() < 3) {
                toast("اسم المستخدم يجب أن يكون 3 أحرف على الأقل.");
                return;
            }

            String lower = name.toLowerCase(Locale.ROOT);

            showLoading("جارٍ التحقق من الاسم...");

            db.collection("users")
                .whereEqualTo("usernameLower", lower)
                .get()
                .addOnSuccessListener(found -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (!found.isEmpty()) {
                        showCreateProfile();
                        toast("الاسم مستخدم، اختر اسمًا آخر.");
                        return;
                    }

                    Map<String, Object> user = new HashMap<>();
                    user.put("uid", uid);
                    user.put("username", name);
                    user.put("usernameLower", lower);
                    user.put("createdAt",
                        FieldValue.serverTimestamp());

                    db.collection("users")
                        .document(uid)
                        .set(user)
                        .addOnSuccessListener(v -> {
                            username = name;
                            showHome();
                        })
                        .addOnFailureListener(e -> {
                            showCreateProfile();
                            toast("تعذر حفظ الحساب.");
                        });
                })
                .addOnFailureListener(e -> {
                    showCreateProfile();
                    toast("تعذر التحقق من الاسم.");
                });
        });
    }

    // =========================
    // الشاشة الرئيسية
    // =========================

    private void showHome() {
        closeAllListeners();

        activeChat = "";
        messageInput = null;
        chatList = null;
        screenVersion++;

        root.removeAllViews();

        addHeading("مراسل");
        addText("مرحبًا، " + username);

        EditText search = addInput(
            "🔍 ابحث باسم المستخدم",
            InputType.TYPE_CLASS_TEXT
        );

        addButton("بحث عن مستخدم", () -> {
            String name = search.getText()
                .toString().trim()
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
                    if (isFinishing() || isDestroyed()) return;

                    if (result.isEmpty()) {
                        toast("لم يتم العثور على المستخدم.");
                        return;
                    }

                    DocumentSnapshot user =
                        result.getDocuments().get(0);

                    String partnerUid = user.getString("uid");
                    String partnerName = user.getString("username");

                    if (partnerUid == null || partnerUid.equals(uid)) {
                        toast("لا يمكنك مراسلة نفسك.");
                        return;
                    }

                    if (partnerName == null) {
                        partnerName = "مستخدم";
                    }

                    openOrCreateChat(partnerUid, partnerName);
                })
                .addOnFailureListener(e ->
                    toast("فشل البحث. تحقق من اتصال الإنترنت."));
        });

        addText("💬 محادثاتك");

        ScrollView scroll = new ScrollView(this);

        chatList = new LinearLayout(this);
        chatList.setOrientation(LinearLayout.VERTICAL);

        scroll.addView(chatList);

        root.addView(scroll,
            new LinearLayout.LayoutParams(-1, 0, 1));

        listenChats();
    }

    // =========================
    // تحميل المحادثات
    // =========================

    private void listenChats() {
        if (chatsListener != null) {
            chatsListener.remove();
            chatsListener = null;
        }

        final int version = screenVersion;

        chatsListener = db.collection("chats")
            .whereArrayContains("participants", uid)
            .addSnapshotListener((snapshot, error) -> {

                if (version != screenVersion
                        || chatList == null
                        || isFinishing()
                        || isDestroyed()) {
                    return;
                }

                if (error != null) {
                    toast("تعذر تحميل المحادثات.");
                    return;
                }

                if (snapshot == null) return;

                chatList.removeAllViews();

                if (snapshot.isEmpty()) {
                    TextView empty = makeText(
                        "لا توجد محادثات بعد.",
                        16,
                        WHITE
                    );

                    empty.setGravity(Gravity.CENTER);
                    empty.setPadding(8, 20, 8, 20);
                    chatList.addView(empty);
                    return;
                }

                for (DocumentSnapshot chat : snapshot.getDocuments()) {

                    List<String> participants =
                        (List<String>) chat.get("participants");

                    if (participants == null) continue;

                    String partnerUid = null;

                    for (String participant : participants) {
                        if (!uid.equals(participant)) {
                            partnerUid = participant;
                            break;
                        }
                    }

                    if (partnerUid == null) continue;

                    String otherUid = partnerUid;
                    String chatId = chat.getId();

                    db.collection("users")
                        .document(otherUid)
                        .get()
                        .addOnSuccessListener(user -> {

                            if (version != screenVersion
                                    || chatList == null
                                    || isFinishing()
                                    || isDestroyed()) {
                                return;
                            }

                            String otherName =
                                user.getString("username");

                            if (otherName == null
                                    || otherName.trim().isEmpty()) {
                                otherName = "مستخدم";
                            }

                            Button button =
                                makeButton("💬  " + otherName);

                            String finalName = otherName;

                            button.setOnClickListener(v ->
                                openChat(chatId, otherUid, finalName)
                            );

                            chatList.addView(button);
                        });
                }
            });
    }

    // =========================
    // إنشاء أو فتح محادثة
    // =========================

    private void openOrCreateChat(
            String partnerUid,
            String partnerName) {

        String first;
        String second;

        if (uid.compareTo(partnerUid) < 0) {
            first = uid;
            second = partnerUid;
        } else {
            first = partnerUid;
            second = uid;
        }

        String chatId = first + "_" + second;

        Map<String, Object> chat = new HashMap<>();
        chat.put("participants", Arrays.asList(first, second));
        chat.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("chats")
            .document(chatId)
            .set(chat, SetOptions.merge())
            .addOnSuccessListener(v ->
                openChat(chatId, partnerUid, partnerName))
            .addOnFailureListener(e ->
                toast("تعذر فتح المحادثة. تحقق من صلاحيات Firestore."));
    }

    // =========================
    // شاشة المحادثة
    // =========================

    private void openChat(
            String chatId,
            String partnerUid,
            String partnerName) {

        closeAllListeners();

        activeChat = chatId;
        messageInput = null;
        firstMessagesSnapshot = true;

        final int version = ++screenVersion;

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

        // صف كتابة الرسالة
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);

        messageInput = new EditText(this);
        messageInput.setHint("اكتب رسالتك...");
        messageInput.setTextColor(WHITE);
        messageInput.setHintTextColor(Color.LTGRAY);
        messageInput.setSingleLine(false);
        messageInput.setMaxLines(4);
        messageInput.setInputType(
            InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );

        bottom.addView(messageInput,
            new LinearLayout.LayoutParams(0, -2, 1));

        Button send = makeButton("إرسال");
        bottom.addView(send);

        send.setOnClickListener(v -> sendMessage());

        // رفع صف الكتابة وزر الإرسال 20 بكسل تقريبًا
        LinearLayout.LayoutParams bottomParams =
            new LinearLayout.LayoutParams(-1, -2);

        bottomParams.bottomMargin =
            (int) (20 * getResources().getDisplayMetrics().density);

        root.addView(bottom, bottomParams);

        // نغمة عند فتح المحادثة
        playChatSound();

        // متابعة الرسائل
        messagesListener = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener((snapshot, error) -> {

                if (version != screenVersion
                        || !chatId.equals(activeChat)
                        || isFinishing()
                        || isDestroyed()) {
                    return;
                }

                if (error != null) {
                    toast("تعذر تحميل الرسائل. تحقق من صلاحيات Firestore.");
                    return;
                }

                if (snapshot == null) return;

                // لا نشغّل صوتًا عند تحميل الرسائل القديمة أول مرة
                boolean incomingMessage = false;

                if (!firstMessagesSnapshot) {
                    for (DocumentChange change :
                            snapshot.getDocumentChanges()) {

                        if (change.getType() == DocumentChange.Type.ADDED) {
                            String sender =
                                change.getDocument().getString("senderId");

                            if (sender != null && !uid.equals(sender)) {
                                incomingMessage = true;
                                break;
                            }
                        }
                    }
                }

                firstMessagesSnapshot = false;

                if (incomingMessage && chatSoundEnabled) {
                    playReceiveSound();
                }

                messages.removeAllViews();

                for (DocumentSnapshot doc : snapshot.getDocuments()) {

                    String body = doc.getString("text");
                    String sender = doc.getString("senderId");

                    if (body == null) continue;

                    TextView bubble = makeText(body, 16, WHITE);
                    bubble.setPadding(12, 10, 12, 10);

                    GradientDrawable shape = new GradientDrawable();

                    shape.setColor(uid.equals(sender)
                        ? GREEN
                        : Color.rgb(52, 65, 59));

                    shape.setCornerRadius(16);
                    bubble.setBackground(shape);

                    LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(-2, -2);

                    params.setMargins(0, 5, 0, 5);

                    params.gravity = uid.equals(sender)
                        ? Gravity.END
                        : Gravity.START;

                    messages.addView(bubble, params);
                }

                scroll.post(() ->
                    scroll.fullScroll(View.FOCUS_DOWN));
            });
    }

    // =========================
    // إرسال رسالة
    // =========================

    private void sendMessage() {
        if (messageInput == null || activeChat.isEmpty()) {
            return;
        }

        final String chatId = activeChat;

        String body = messageInput.getText()
            .toString().trim();

        if (body.isEmpty()) return;

        if (body.length() > 2000) {
            toast("الرسالة طويلة جدًا.");
            return;
        }

        Map<String, Object> message = new HashMap<>();
        message.put("senderId", uid);
        message.put("text", body);
        message.put("createdAt", FieldValue.serverTimestamp());

        db.collection("chats")
            .document(chatId)
            .collection("messages")
            .add(message)
            .addOnSuccessListener(ref -> {

                if (!chatId.equals(activeChat)
                        || messageInput == null) {
                    return;
                }

                messageInput.setText("");

                // صوت بعد نجاح الإرسال
                playSendSound();

                db.collection("chats")
                    .document(chatId)
                    .update(
                        "updatedAt",
                        FieldValue.serverTimestamp()
                    );
            })
            .addOnFailureListener(e ->
                toast("تعذر إرسال الرسالة. تحقق من صلاحيات Firestore."));
    }

    // =========================
    // إغلاق مستمعي Firebase
    // =========================

    private void stopChatListeners() {
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }
    }

    private void closeAllListeners() {
        stopChatListeners();

        if (chatsListener != null) {
            chatsListener.remove();
            chatsListener = null;
        }
    }

    // =========================
    // عناصر الواجهة
    // =========================

    private void addHeading(String value) {
        TextView text = makeText(value, 27, GOLD);
        text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, 12, 0, 12);
        root.addView(text);
    }

    private void addText(String value) {
        TextView text = makeText(value, 16, WHITE);
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, 8, 0, 10);
        root.addView(text);
    }

    private TextView makeText(
            String value,
            int size,
            int color) {

        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);

        return text;
    }

    private EditText addInput(String hint, int type) {
        EditText input = new EditText(this);

        input.setHint(hint);
        input.setTextColor(WHITE);
        input.setHintTextColor(Color.LTGRAY);
        input.setInputType(type);

        root.addView(input);

        return input;
    }

    private Button makeButton(String value) {
        Button button = new Button(this);

        button.setText(value);
        button.setTextColor(BG);

        GradientDrawable shape = new GradientDrawable();
        shape.setColor(GOLD);
        shape.setCornerRadius(18);

        button.setBackground(shape);

        return button;
    }

    private void addButton(
            String value,
            Runnable action) {

        Button button = makeButton(value);

        button.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(-1, -2);

        params.setMargins(0, 6, 0, 6);

        root.addView(button, params);
    }

    private void toast(String value) {
        Toast.makeText(
            this,
            value,
            Toast.LENGTH_LONG
        ).show();
    }

    @Override
    protected void onDestroy() {
        screenVersion++;
        closeAllListeners();

        chatList = null;
        messageInput = null;

        super.onDestroy();
    }
                                           }
