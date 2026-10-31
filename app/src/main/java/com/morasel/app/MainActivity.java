
package com.morasel.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;

import java.io.File;
import java.util.*;

public class MainActivity extends Activity {

    // Firebase
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    // الواجهة
    private LinearLayout root;
    private LinearLayout chatList;
    private LinearLayout messagesContainer;

    private EditText messageInput;
    private EditText emailInput;
    private EditText passwordInput;

    private ScrollView messagesScroll;
    private Button voiceButton;

    // مستمعو Firestore
    private ListenerRegistration chatsListener;
    private ListenerRegistration messagesListener;

    // بيانات المستخدم
    private String uid = "";
    private String username = "";
    private String activeChat = "";

    // التحكم بالشاشات
    private int screenVersion = 0;
    private boolean isRecording = false;

    // التسجيل الصوتي
    private MediaRecorder recorder;
    private File audioFile;

    private static final int REQUEST_RECORD_AUDIO = 501;

    // الألوان
    private static final int BG =
            Color.rgb(17, 35, 28);

    private static final int GREEN =
            Color.rgb(24, 104, 68);

    private static final int GOLD =
            Color.rgb(239, 193, 77);

    private static final int WHITE =
            Color.WHITE;

    // ==============================
    // بداية التطبيق
    // ==============================

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

    // ==============================
    // تسجيل الدخول
    // ==============================

    private void login() {

        FirebaseUser user = auth.getCurrentUser();

        if (user != null) {

            uid = user.getUid();

            loadProfile();

        } else {

            showEmailLogin();
        }
    }

    // ==============================
    // شاشة البريد الإلكتروني
    // ==============================

    private void showEmailLogin() {

        closeAllListeners();

        root.removeAllViews();

        addHeading("مراسل");

        addText("مرحباً بك في تطبيق مراسل");

        addText("سجل الدخول أو أنشئ حساباً جديداً");

        emailInput = addInput(
                "البريد الإلكتروني",
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        );

        emailInput.setSingleLine(true);

        passwordInput = addInput(
                "كلمة المرور",
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        passwordInput.setSingleLine(true);

        addButton(
                "إنشاء حساب جديد",
                this::registerEmail
        );

        addButton(
                "تسجيل الدخول",
                this::loginEmail
        );

        addButton(
                "نسيت كلمة المرور؟",
                this::resetPassword
        );

        addText("مراسل - تواصل مع أصدقائك");
    }

    // ==============================
    // إنشاء حساب جديد
    // ==============================

    private void registerEmail() {

        if (emailInput == null ||
                passwordInput == null) {
            return;
        }

        String email = emailInput.getText()
                .toString()
                .trim();

        String password = passwordInput.getText()
                .toString();

        if (email.isEmpty() ||
                !android.util.Patterns.EMAIL_ADDRESS
                        .matcher(email).matches()) {

            toast("أدخل بريداً إلكترونياً صحيحاً.");

            return;
        }

        if (password.length() < 6) {

            toast("كلمة المرور يجب أن تكون 6 أحرف على الأقل.");

            return;
        }

        showLoading("جارٍ إنشاء الحساب...");

        auth.createUserWithEmailAndPassword(
                email,
                password
        )

        .addOnSuccessListener(result -> {

            FirebaseUser user = result.getUser();

            if (user == null) {

                showEmailLogin();

                toast("تعذر إنشاء الحساب.");

                return;
            }

            saveAuthenticatedUser(user);

        })

        .addOnFailureListener(e -> {

            showEmailLogin();

            toast("فشل إنشاء الحساب: " + errorText(e));

        });
    }

    // ==============================
    // تسجيل الدخول
    // ==============================

    private void loginEmail() {

        if (emailInput == null ||
                passwordInput == null) {
            return;
        }

        String email = emailInput.getText()
                .toString()
                .trim();

        String password = passwordInput.getText()
                .toString();

        if (email.isEmpty() ||
                !android.util.Patterns.EMAIL_ADDRESS
                        .matcher(email).matches()) {

            toast("أدخل بريداً إلكترونياً صحيحاً.");

            return;
        }

        if (password.isEmpty()) {

            toast("أدخل كلمة المرور.");

            return;
        }

        showLoading("جارٍ تسجيل الدخول...");

        auth.signInWithEmailAndPassword(
                email,
                password
        )

        .addOnSuccessListener(result -> {

            FirebaseUser user = result.getUser();

            if (user == null) {

                showEmailLogin();

                toast("تعذر تسجيل الدخول.");

                return;
            }

            saveAuthenticatedUser(user);

        })

        .addOnFailureListener(e -> {

            showEmailLogin();

            toast("فشل تسجيل الدخول: " + errorText(e));

        });
    }

    // ==============================
    // استعادة كلمة المرور
    // ==============================

    private void resetPassword() {

        if (emailInput == null) {
            return;
        }

        String email = emailInput.getText()
                .toString()
                .trim();

        if (email.isEmpty() ||
                !android.util.Patterns.EMAIL_ADDRESS
                        .matcher(email).matches()) {

            toast("أدخل بريدك الإلكتروني أولاً.");

            return;
        }

        showLoading("جارٍ إرسال رابط الاستعادة...");

        auth.sendPasswordResetEmail(email)

        .addOnSuccessListener(v -> {

            showEmailLogin();

            toast(
                    "تم إرسال رابط استعادة كلمة المرور إلى بريدك."
            );

        })

        .addOnFailureListener(e -> {

            showEmailLogin();

            toast(
                    "تعذر إرسال الرابط: " + errorText(e)
            );

        });
    }

    // ==============================
    // حفظ بيانات الحساب
    // ==============================

    private void saveAuthenticatedUser(FirebaseUser user) {

        uid = user.getUid();

        Map<String, Object> data = new HashMap<>();

        data.put("uid", uid);

        data.put("email", user.getEmail());

        data.put(
                "lastLoginAt",
                FieldValue.serverTimestamp()
        );

        db.collection("users")
                .document(uid)

                .set(data, SetOptions.merge())

                .addOnSuccessListener(v -> {

                    loadProfile();

                })

                .addOnFailureListener(e -> {

                    showEmailLogin();

                    toast(
                            "تم تسجيل الدخول لكن تعذر حفظ بيانات الحساب."
                    );

                });
    }

    // ==============================
    // تحميل الملف الشخصي
    // ==============================

    private void loadProfile() {

        if (uid.isEmpty()) {

            showEmailLogin();

            return;
        }

        db.collection("users")
                .document(uid)

                .get()

                .addOnSuccessListener(doc -> {

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    String name = doc.getString("username");

                    if (name != null &&
                            !name.trim().isEmpty()) {

                        username = name.trim();

                        showHome();

                    } else {

                        showCreateProfile();
                    }

                })

                .addOnFailureListener(e -> {

                    showEmailLogin();

                    toast(
                            "فشل تحميل الحساب: " + errorText(e)
                    );

                });
    }

    // ==============================
    // إنشاء اسم المستخدم
    // ==============================

    private void showCreateProfile() {

        closeAllListeners();

        root.removeAllViews();

        addHeading("إنشاء حساب");

        addText(
                "اختر اسم مستخدم يستطيع الآخرون البحث عنه."
        );

        EditText nameInput = addInput(
                "اسم المستخدم",
                InputType.TYPE_CLASS_TEXT
        );

        addButton("حفظ ومتابعة", () -> {

            String name = nameInput.getText()
                    .toString()
                    .trim();

            if (name.length() < 3) {

                toast(
                        "اسم المستخدم يجب أن يكون 3 أحرف على الأقل."
                );

                return;
            }

            String lower = name.toLowerCase(Locale.ROOT);

            showLoading("جارٍ التحقق من الاسم...");

            db.collection("users")

                    .whereEqualTo("usernameLower", lower)

                    .get()

                    .addOnSuccessListener(found -> {

                        boolean taken = false;

                        for (DocumentSnapshot d :
                                found.getDocuments()) {

                            if (!uid.equals(d.getId())) {

                                taken = true;

                                break;
                            }
                        }

                        if (taken) {

                            showCreateProfile();

                            toast(
                                    "الاسم مستخدم، اختر اسماً آخر."
                            );

                            return;
                        }

                        Map<String, Object> data =
                                new HashMap<>();

                        data.put("uid", uid);

                        data.put("username", name);

                        data.put("usernameLower", lower);

                        data.put(
                                "createdAt",
                                FieldValue.serverTimestamp()
                        );

                        db.collection("users")
                                .document(uid)

                                .set(data, SetOptions.merge())

                                .addOnSuccessListener(v -> {

                                    username = name;

                                    showHome();

                                })

                                .addOnFailureListener(e -> {

                                    showCreateProfile();

                                    toast(
                                            "تعذر حفظ الحساب: " +
                                            errorText(e)
                                    );

                                });

                    })

                    .addOnFailureListener(e -> {

                        showCreateProfile();

                        toast("تعذر التحقق من الاسم.");

                    });
        });
    }

    // ==============================
    // الصفحة الرئيسية
    // ==============================

    private void showHome() {

        closeAllListeners();

        activeChat = "";

        screenVersion++;

        root.removeAllViews();

        addHeading("مراسل");

        addText("مرحباً، " + username);

        EditText search = addInput(
                "ابحث باسم المستخدم",
                InputType.TYPE_CLASS_TEXT
        );

        addButton("بحث عن مستخدم", () -> {

            String name = search.getText()
                    .toString()
                    .trim()
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

                        DocumentSnapshot user =
                                result.getDocuments().get(0);

                        String partnerUid =
                                user.getString("uid");

                        String partnerName =
                                user.getString("username");

                        if (partnerUid == null ||
                                partnerUid.equals(uid)) {

                            toast("لا يمكنك مراسلة نفسك.");

                            return;
                        }

                        openOrCreateChat(
                                partnerUid,
                                partnerName == null
                                        ? "مستخدم"
                                        : partnerName
                        );

                    })

                    .addOnFailureListener(e ->

                            toast(
                                    "فشل البحث: " + errorText(e)
                            )
                    );
        });

        addText("محادثاتك");

        ScrollView scroll = new ScrollView(this);

        chatList = new LinearLayout(this);

        chatList.setOrientation(
                LinearLayout.VERTICAL
        );

        scroll.addView(chatList);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );

        listenChats();

        addButton("تسجيل الخروج", () -> {

            closeAllListeners();

            auth.signOut();

            uid = "";

            username = "";

            activeChat = "";

            showEmailLogin();

            toast("تم تسجيل الخروج.");

        });
    }

    // ==============================
    // الاستماع للمحادثات
    // ==============================

    private void listenChats() {

        if (chatsListener != null) {

            chatsListener.remove();

            chatsListener = null;
        }

        final int version = screenVersion;

        chatsListener = db.collection("chats")

                .whereArrayContains("participants", uid)

                .addSnapshotListener((snapshot, error) -> {

                    if (version != screenVersion ||
                            chatList == null ||
                            isFinishing() ||
                            isDestroyed()) {

                        return;
                    }

                    if (error != null) {

                        toast("تعذر تحميل المحادثات.");

                        return;
                    }

                    if (snapshot == null) {
                        return;
                    }

                    chatList.removeAllViews();

                    if (snapshot.isEmpty()) {

                        TextView empty = makeText(
                                "لا توجد محادثات بعد.",
                                16,
                                WHITE
                        );

                        empty.setGravity(Gravity.CENTER);

                        chatList.addView(empty);

                        return;
                    }

                    for (DocumentSnapshot chat :
                            snapshot.getDocuments()) {

                        List<String> participants =
                                (List<String>) chat.get("participants");

                        if (participants == null) {
                            continue;
                        }

                        String other = null;

                        for (String p : participants) {

                            if (!uid.equals(p)) {

                                other = p;

                                break;
                            }
                        }

                        if (other == null) {
                            continue;
                        }

                        final String otherUid = other;

                        final String chatId = chat.getId();

                        db.collection("users")

                                .document(otherUid)

                                .get()

                                .addOnSuccessListener(user -> {

                                    if (version != screenVersion ||
                                            chatList == null) {

                                        return;
                                    }

                                    String fetchedName =
                                            user.getString("username");

                                    final String displayName =
                                            (fetchedName == null ||
                                            fetchedName.trim().isEmpty())
                                            ? "مستخدم"
                                            : fetchedName.trim();

                                    Button b = makeButton(
                                            "💬 " + displayName
                                    );

                                    b.setOnClickListener(v ->

                                            openChat(
                                                    chatId,
                                                    otherUid,
                                                    displayName
                                            )
                                    );

                                    chatList.addView(b);

                                });
                    }
                });
    }

    // ==============================
    // إنشاء محادثة جديدة
    // ==============================

    private void openOrCreateChat(
            String partnerUid,
            String partnerName) {

        String first =
                uid.compareTo(partnerUid) < 0
                        ? uid
                        : partnerUid;

        String second =
                uid.compareTo(partnerUid) < 0
                        ? partnerUid
                        : uid;

        String chatId = first + "_" + second;

        Map<String, Object> data = new HashMap<>();

        data.put(
                "participants",
                Arrays.asList(first, second)
        );

        data.put(
                "updatedAt",
                FieldValue.serverTimestamp()
        );

        db.collection("chats")

                .document(chatId)

                .set(data, SetOptions.merge())

                .addOnSuccessListener(v ->

                        openChat(
                                chatId,
                                partnerUid,
                                partnerName
                        )
                )

                .addOnFailureListener(e ->

                        toast(
                                "تعذر فتح المحادثة: " +
                                errorText(e)
                        )
                );
    }

    // ==============================
    // فتح المحادثة
    // ==============================

    private void openChat(
            String chatId,
            String partnerUid,
            String partnerName) {

        closeAllListeners();

        activeChat = chatId;

        final int version = ++screenVersion;

        root.removeAllViews();

        addHeading("محادثة: " + partnerName);

        addButton("رجوع", this::showHome);

        messagesScroll = new ScrollView(this);

        messagesContainer = new LinearLayout(this);

        messagesContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        messagesContainer.setPadding(
                4, 4, 4, 4
        );

        messagesScroll.addView(messagesContainer);

        root.addView(
                messagesScroll,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );

        LinearLayout bottom = new LinearLayout(this);

        bottom.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout row = new LinearLayout(this);

        row.setOrientation(
                LinearLayout.HORIZONTAL
        );

        messageInput = new EditText(this);

        messageInput.setHint("اكتب رسالتك...");

        messageInput.setTextColor(WHITE);

        messageInput.setHintTextColor(Color.LTGRAY);

        messageInput.setMaxLines(4);

        messageInput.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );

        row.addView(
                messageInput,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        Button send = makeButton("إرسال");

        row.addView(send);

        send.setOnClickListener(v -> sendMessage());

        bottom.addView(row);

        voiceButton = makeButton(
                "🎙️ تسجيل رسالة صوتية"
        );

        bottom.addView(voiceButton);

        voiceButton.setOnClickListener(v -> {

            if (isRecording) {

                stopVoiceRecording();

            } else {

                startVoiceRecording();
            }
        });

        root.addView(bottom);

        messagesListener = db.collection("chats")

                .document(chatId)

                .collection("messages")

                .orderBy(
                        "createdAt",
                        Query.Direction.ASCENDING
                )

                .addSnapshotListener((snapshot, error) -> {

                    if (version != screenVersion ||
                            !chatId.equals(activeChat) ||
                            messagesContainer == null ||
                            isFinishing() ||
                            isDestroyed()) {

                        return;
                    }

                    if (error != null) {

                        toast("تعذر تحميل الرسائل.");

                        return;
                    }

                    if (snapshot == null) {
                        return;
                    }

                    messagesContainer.removeAllViews();

                    for (DocumentSnapshot doc :
                            snapshot.getDocuments()) {

                        String sender =
                                doc.getString("senderId");

                        String type =
                                doc.getString("type");

                        boolean mine = uid.equals(sender);

                        if ("audio".equals(type)) {

                            TextView audio = makeText(
                                    "رسالة صوتية: يلزم رفع الملف إلى خادم لإرسالها",
                                    14,
                                    GOLD
                            );

                            audio.setGravity(
                                    mine
                                            ? Gravity.RIGHT
                                            : Gravity.LEFT
                            );

                            messagesContainer.addView(audio);

                        } else {

                            String body =
                                    doc.getString("text");

                            if (body == null) {
                                continue;
                            }

                            TextView bubble = makeText(
                                    body,
                                    16,
                                    WHITE
                            );

                            bubble.setPadding(
                                    12, 10, 12, 10
                            );

                            GradientDrawable shape =
                                    new GradientDrawable();

                            shape.setColor(
                                    mine
                                            ? GREEN
                                            : Color.rgb(52, 65, 59)
                            );

                            shape.setCornerRadius(16);

                            bubble.setBackground(shape);

                            LinearLayout.LayoutParams p =
                                    new LinearLayout.LayoutParams(
                                            -2,
                                            -2
                                    );

                            p.setMargins(0, 5, 0, 5);

                            p.gravity =
                                    mine
                                            ? Gravity.RIGHT
                                            : Gravity.LEFT;

                            messagesContainer.addView(
                                    bubble,
                                    p
                            );
                        }
                    }

                    messagesScroll.post(() ->

                            messagesScroll.fullScroll(
                                    View.FOCUS_DOWN
                            )
                    );
                });
    }

    // ==============================
    // إرسال رسالة نصية
    // ==============================

    private void sendMessage() {

        if (messageInput == null ||
                activeChat.isEmpty()) {

            return;
        }

        String body = messageInput.getText()
                .toString()
                .trim();

        if (body.isEmpty()) {
            return;
        }

        if (body.length() > 2000) {

            toast("الرسالة طويلة جداً.");

            return;
        }

        final String chatId = activeChat;

        Map<String, Object> data = new HashMap<>();

        data.put("senderId", uid);

        data.put("type", "text");

        data.put("text", body);

        data.put(
                "createdAt",
                FieldValue.serverTimestamp()
        );

        db.collection("chats")

                .document(chatId)

                .collection("messages")

                .add(data)

                .addOnSuccessListener(ref -> {

                    if (messageInput != null &&
                            chatId.equals(activeChat)) {

                        messageInput.setText("");
                    }

                    db.collection("chats")

                            .document(chatId)

                            .update(
                                    "updatedAt",
                                    FieldValue.serverTimestamp()
                            );
                })

                .addOnFailureListener(e ->

                        toast(
                                "تعذر إرسال الرسالة: " +
                                errorText(e)
                        )
                );
    }

    // ==============================
    // التسجيل الصوتي
    // ==============================

    private void startVoiceRecording() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.RECORD_AUDIO
                    },
                    REQUEST_RECORD_AUDIO
            );

            return;
        }

        try {

            File dir = getExternalFilesDir("voice");

            if (dir == null) {

                toast("تعذر الوصول لمجلد التسجيل.");

                return;
            }

            if (!dir.exists() && !dir.mkdirs()) {

                toast("تعذر إنشاء مجلد التسجيل.");

                return;
            }

            audioFile = new File(
                    dir,
                    "voice_" +
                    System.currentTimeMillis() +
                    ".m4a"
            );

            if (Build.VERSION.SDK_INT >= 31) {

                recorder = new MediaRecorder(this);

            } else {

                recorder = new MediaRecorder();
            }

            recorder.setAudioSource(
                    MediaRecorder.AudioSource.MIC
            );

            recorder.setOutputFormat(
                    MediaRecorder.OutputFormat.MPEG_4
            );

            recorder.setAudioEncoder(
                    MediaRecorder.AudioEncoder.AAC
            );

            recorder.setOutputFile(
                    audioFile.getAbsolutePath()
            );

            recorder.prepare();

            recorder.start();

            isRecording = true;

            if (voiceButton != null) {

                voiceButton.setText("إيقاف التسجيل");
            }

            toast("بدأ التسجيل.");

        } catch (Exception e) {

            isRecording = false;

            releaseRecorder();

            toast("تعذر بدء التسجيل.");
        }
    }

    private void stopVoiceRecording() {

        if (!isRecording || recorder == null) {
            return;
        }

        try {

            recorder.stop();

            isRecording = false;

            releaseRecorder();

            if (voiceButton != null) {

                voiceButton.setText(
                        "🎙️ تسجيل رسالة صوتية"
                );
            }

            toast(
                    "تم حفظ التسجيل على الجهاز. يلزم خادم آمن لرفعه وإرساله."
            );

        } catch (Exception e) {

            isRecording = false;

            releaseRecorder();

            toast("التسجيل لم يكتمل.");

        }
    }

    private void releaseRecorder() {

        if (recorder != null) {

            try {
                recorder.reset();
            } catch (Exception ignored) {}

            try {
                recorder.release();
            } catch (Exception ignored) {}

            recorder = null;
        }
    }

    // ==============================
    // صلاحية الميكروفون
    // ==============================

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == REQUEST_RECORD_AUDIO) {

            if (grantResults.length > 0 &&
                    grantResults[0] ==
                            PackageManager.PERMISSION_GRANTED) {

                startVoiceRecording();

            } else {

                toast(
                        "اسمح باستخدام الميكروفون لتسجيل الصوت."
                );
            }
        }
    }

    // ==============================
    // أدوات الواجهة
    // ==============================

    private void showLoading(String value) {

        root.removeAllViews();

        TextView text = makeText(
                value,
                21,
                GOLD
        );

        text.setGravity(Gravity.CENTER);

        root.addView(
                text,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );
    }

    private void addHeading(String value) {

        TextView text = makeText(
                value,
                27,
                GOLD
        );

        text.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        text.setGravity(Gravity.CENTER);

        text.setPadding(
                0, 12, 0, 12
        );

        root.addView(text);
    }

    private void addText(String value) {

        TextView text = makeText(
                value,
                16,
                WHITE
        );

        text.setGravity(Gravity.CENTER);

        text.setPadding(
                0, 8, 0, 10
        );

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

    private EditText addInput(
            String hint,
            int inputType) {

        EditText input = new EditText(this);

        input.setHint(hint);

        input.setTextColor(WHITE);

        input.setHintTextColor(Color.LTGRAY);

        input.setInputType(inputType);

        root.addView(
                input,
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                )
        );

        return input;
    }

    private Button makeButton(String value) {

        Button button = new Button(this);

        button.setText(value);

        button.setTextColor(BG);

        GradientDrawable shape =
                new GradientDrawable();

        shape.setColor(GOLD);

        shape.setCornerRadius(18);

        button.setBackground(shape);

        return button;
    }

    private void addButton(
            String value,
            Runnable action) {

        Button button = makeButton(value);

        button.setOnClickListener(v ->
                action.run()
        );

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(
                        -1,
                        -2
                );

        p.setMargins(
                0, 6, 0, 6
        );

        root.addView(button, p);
    }

    // ==============================
    // إغلاق المستمعين
    // ==============================

    private void closeAllListeners() {

        if (messagesListener != null) {

            messagesListener.remove();

            messagesListener = null;
        }

        if (chatsListener != null) {

            chatsListener.remove();

            chatsListener = null;
        }
    }

    // ==============================
    // رسائل الأخطاء
    // ==============================

    private String errorText(Exception e) {

        return e.getLocalizedMessage() == null
                ? "خطأ غير معروف"
                : e.getLocalizedMessage();
    }

    private void toast(String text) {

        Toast.makeText(
                this,
                text,
                Toast.LENGTH_LONG
        ).show();
    }

    // ==============================
    // إغلاق التطبيق
    // ==============================

    @Override
    protected void onDestroy() {

        screenVersion++;

        closeAllListeners();

        if (isRecording) {

            try {

                if (recorder != null) {
                    recorder.stop();
                }

            } catch (Exception ignored) {}

            isRecording = false;

            releaseRecorder();
        }

        super.onDestroy();
    }
}
