package com.morasel.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.*;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private LinearLayout root, chatList, messagesContainer;
    private EditText messageInput, phoneInput, codeInput;
    private ScrollView messagesScroll;
    private Button voiceButton;
    private ListenerRegistration chatsListener, messagesListener;

    private String uid = "", username = "", activeChat = "";
    private String verificationId = "", pendingPhone = "";
    private PhoneAuthProvider.ForceResendingToken resendToken;
    private int screenVersion = 0;
    private boolean firstMessagesSnapshot = true;
    private boolean isRecording = false;
    private MediaRecorder recorder;
    private File audioFile;
    private MediaPlayer audioPlayer;

    private static final int REQUEST_RECORD_AUDIO = 501;
    private static final int BG = Color.rgb(17, 35, 28);
    private static final int GREEN = Color.rgb(24, 104, 68);
    private static final int GOLD = Color.rgb(239, 193, 77);
    private static final int WHITE = Color.WHITE;

    /*
     * Dosya:
     * لا تضع مفاتيح Dosya السرية داخل APK.
     * ضع لاحقاً عنوان خادمك الوسيط الآمن هنا.
     * لا يمكن تنفيذ الرفع والتنزيل بأمان من التطبيق مباشرة بمفتاح سري.
     */
    private static final String DOSYA_BACKEND_URL = "YOUR_SECURE_BACKEND_URL";

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

    // ---------------- تسجيل الدخول برقم الهاتف ----------------

    private void login() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null && !user.isAnonymous()) {
            uid = user.getUid();
            loadProfile();
        } else {
            showPhoneLogin();
        }
    }

    private void showPhoneLogin() {
        closeAllListeners();
        root.removeAllViews();
        addHeading("مراسل");
        addText("تسجيل الدخول برقم الهاتف");

        phoneInput = addInput("رقم الهاتف بصيغة دولية", InputType.TYPE_CLASS_PHONE);
        phoneInput.setText("+967");

        addButton("إرسال رمز SMS", this::sendVerificationCode);

        codeInput = addInput("رمز التحقق المكوّن من 6 أرقام",
                InputType.TYPE_CLASS_NUMBER);
        addButton("تأكيد الرمز والدخول", this::verifyCode);
        addText("يمكنك تغيير +967 إلى مفتاح بلدك.");
    }

    private void sendVerificationCode() {
        if (phoneInput == null) return;
        String phone = phoneInput.getText().toString().trim();
        if (!phone.matches("^\\+[1-9][0-9]{7,14}$")) {
            toast("أدخل الرقم بالصيغة الدولية، مثال: +967XXXXXXXXX");
            return;
        }
        pendingPhone = phone;
        showLoading("جارٍ إرسال رمز التحقق...");

        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(PhoneAuthCredential credential) {
                        signInWithPhoneCredential(credential);
                    }

                    @Override
                    public void onVerificationFailed(FirebaseException e) {
                        showPhoneLogin();
                        phoneInput.setText(pendingPhone);
                        toast("تعذر إرسال الرمز: " + errorText(e));
                    }

                    @Override
                    public void onCodeSent(String id,
                            PhoneAuthProvider.ForceResendingToken token) {
                        verificationId = id;
                        resendToken = token;
                        showPhoneLogin();
                        phoneInput.setText(pendingPhone);
                        toast("تم إرسال الرمز. أدخله ثم اضغط تأكيد.");
                    }
                })
                .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void verifyCode() {
        if (verificationId.isEmpty()) {
            toast("اطلب رمز التحقق أولاً.");
            return;
        }
        String code = codeInput == null ? "" :
                codeInput.getText().toString().trim();
        if (code.length() != 6) {
            toast("أدخل رمز التحقق المكوّن من 6 أرقام.");
            return;
        }
        signInWithPhoneCredential(
                PhoneAuthProvider.getCredential(verificationId, code));
    }

    private void signInWithPhoneCredential(PhoneAuthCredential credential) {
        runOnUiThread(() -> showLoading("جارٍ التحقق من رقم الهاتف..."));
        auth.signInWithCredential(credential)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        showPhoneLogin();
                        toast("تعذر الحصول على الحساب.");
                        return;
                    }
                    uid = user.getUid();
                    Map<String, Object> data = new HashMap<>();
                    data.put("uid", uid);
                    if (user.getPhoneNumber() != null)
                        data.put("phoneNumber", user.getPhoneNumber());
                    data.put("lastLoginAt", FieldValue.serverTimestamp());

                    db.collection("users").document(uid)
                            .set(data, SetOptions.merge())
                            .addOnSuccessListener(v -> loadProfile())
                            .addOnFailureListener(e -> {
                                showPhoneLogin();
                                toast("تم التحقق لكن تعذر حفظ الحساب.");
                            });
                })
                .addOnFailureListener(e -> {
                    showPhoneLogin();
                    toast("رمز التحقق غير صحيح أو انتهت صلاحيته.");
                });
    }

    // ---------------- الملف الشخصي ----------------

    private void loadProfile() {
        if (uid.isEmpty()) {
            showPhoneLogin();
            return;
        }
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (isFinishing() || isDestroyed()) return;
                    String name = doc.getString("username");
                    if (name != null && !name.trim().isEmpty()) {
                        username = name.trim();
                        showHome();
                    } else {
                        showCreateProfile();
                    }
                })
                .addOnFailureListener(e -> {
                    showPhoneLogin();
                    toast("فشل تحميل الحساب: " + errorText(e));
                });
    }

    private void showCreateProfile() {
        closeAllListeners();
        root.removeAllViews();
        addHeading("إنشاء حساب");
        addText("اختر اسم مستخدم يستطيع الآخرون البحث عنه.");
        EditText nameInput = addInput("اسم المستخدم", InputType.TYPE_CLASS_TEXT);

        addButton("حفظ ومتابعة", () -> {
            String name = nameInput.getText().toString().trim();
            if (name.length() < 3) {
                toast("اسم المستخدم يجب أن يكون 3 أحرف على الأقل.");
                return;
            }
            String lower = name.toLowerCase(Locale.ROOT);
            showLoading("جارٍ التحقق من الاسم...");
            db.collection("users").whereEqualTo("usernameLower", lower).get()
                    .addOnSuccessListener(found -> {
                        boolean taken = false;
                        for (DocumentSnapshot d : found.getDocuments()) {
                            if (!uid.equals(d.getId())) {
                                taken = true;
                                break;
                            }
                        }
                        if (taken) {
                            showCreateProfile();
                            toast("الاسم مستخدم، اختر اسماً آخر.");
                            return;
                        }
                        Map<String, Object> data = new HashMap<>();
                        data.put("uid", uid);
                        data.put("username", name);
                        data.put("usernameLower", lower);
                        data.put("createdAt", FieldValue.serverTimestamp());
                        db.collection("users").document(uid)
                                .set(data, SetOptions.merge())
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

    // ---------------- الرئيسية والبحث ----------------

    private void showHome() {
        closeAllListeners();
        activeChat = "";
        screenVersion++;
        root.removeAllViews();
        addHeading("مراسل");
        addText("مرحباً، " + username);

        EditText search = addInput("ابحث باسم المستخدم", InputType.TYPE_CLASS_TEXT);
        addButton("بحث عن مستخدم", () -> {
            String name = search.getText().toString().trim()
                    .toLowerCase(Locale.ROOT);
            if (name.isEmpty()) {
                toast("اكتب اسم المستخدم.");
                return;
            }
            db.collection("users").whereEqualTo("usernameLower", name)
                    .limit(1).get()
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
                        openOrCreateChat(partnerUid,
                                partnerName == null ? "مستخدم" : partnerName);
                    })
                    .addOnFailureListener(e -> toast("فشل البحث."));
        });

        addText("محادثاتك");
        ScrollView scroll = new ScrollView(this);
        chatList = new LinearLayout(this);
        chatList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(chatList);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        listenChats();
    }

    private void listenChats() {
        if (chatsListener != null) chatsListener.remove();
        final int version = screenVersion;
        chatsListener = db.collection("chats")
                .whereArrayContains("participants", uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (version != screenVersion || chatList == null ||
                            isFinishing() || isDestroyed()) return;
                    if (error != null) {
                        toast("تعذر تحميل المحادثات.");
                        return;
                    }
                    if (snapshot == null) return;
                    chatList.removeAllViews();
                    if (snapshot.isEmpty()) {
                        TextView empty = makeText("لا توجد محادثات بعد.", 16, WHITE);
                        empty.setGravity(Gravity.CENTER);
                        chatList.addView(empty);
                        return;
                    }
                    for (DocumentSnapshot chat : snapshot.getDocuments()) {
                        List<String> participants =
                                (List<String>) chat.get("participants");
                        if (participants == null) continue;
                        String other = null;
                        for (String p : participants) {
                            if (!uid.equals(p)) {
                                other = p;
                                break;
                            }
                        }
                        if (other == null) continue;
                        String otherUid = other;
                        String chatId = chat.getId();
                        db.collection("users").document(otherUid).get()
                                .addOnSuccessListener(user -> {
                                    if (version != screenVersion || chatList == null)
                                        return;
                                    String otherName = user.getString("username");
                                    if (otherName == null) otherName = "مستخدم";
                                    Button b = makeButton("💬 " + otherName);
                                    b.setOnClickListener(v ->
                                            openChat(chatId, otherUid, otherName));
                                    chatList.addView(b);
                                });
                    }
                });
    }

    private void openOrCreateChat(String partnerUid, String partnerName) {
        String first = uid.compareTo(partnerUid) < 0 ? uid : partnerUid;
        String second = uid.compareTo(partnerUid) < 0 ? partnerUid : uid;
        String chatId = first + "_" + second;
        Map<String, Object> data = new HashMap<>();
        data.put("participants", Arrays.asList(first, second));
        data.put("updatedAt", FieldValue.serverTimestamp());
        db.collection("chats").document(chatId).set(data, SetOptions.merge())
                .addOnSuccessListener(v -> openChat(chatId, partnerUid, partnerName))
                .addOnFailureListener(e -> toast("تعذر فتح المحادثة."));
    }

    // ---------------- شاشة المحادثة والرسائل ----------------

    private void openChat(String chatId, String partnerUid, String partnerName) {
        closeAllListeners();
        activeChat = chatId;
        firstMessagesSnapshot = true;
        final int version = ++screenVersion;
        root.removeAllViews();
        addHeading("محادثة: " + partnerName);
        addButton("رجوع", this::showHome);

        messagesScroll = new ScrollView(this);
        messagesContainer = new LinearLayout(this);
        messagesContainer.setOrientation(LinearLayout.VERTICAL);
        messagesContainer.setPadding(4, 4, 4, 4);
        messagesScroll.addView(messagesContainer);
        root.addView(messagesScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        messageInput = new EditText(this);
        messageInput.setHint("اكتب رسالتك...");
        messageInput.setTextColor(WHITE);
        messageInput.setHintTextColor(Color.LTGRAY);
        messageInput.setMaxLines(4);
        messageInput.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        row.addView(messageInput, new LinearLayout.LayoutParams(0, -2, 1));
        Button send = makeButton("إرسال");
        row.addView(send);
        send.setOnClickListener(v -> sendMessage());
        bottom.addView(row);

        voiceButton = makeButton("🎙️ تسجيل رسالة صوتية");
        bottom.addView(voiceButton);
        voiceButton.setOnClickListener(v -> {
            if (isRecording) stopVoiceRecording();
            else startVoiceRecording();
        });
        root.addView(bottom);

        messagesListener = db.collection("chats").document(chatId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (version != screenVersion || !chatId.equals(activeChat) ||
                            messagesContainer == null || isFinishing() || isDestroyed())
                        return;
                    if (error != null) {
                        toast("تعذر تحميل الرسائل.");
                        return;
                    }
                    if (snapshot == null) return;
                    messagesContainer.removeAllViews();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String sender = doc.getString("senderId");
                        String type = doc.getString("type");
                        boolean mine = uid.equals(sender);
                        if ("audio".equals(type)) {
                            TextView audio = makeText(
                                    "رسالة صوتية (يتطلب ربط Dosya بخادم آمن)", 15, GOLD);
                            audio.setGravity(mine ? Gravity.RIGHT : Gravity.LEFT);
                            messagesContainer.addView(audio);
                        } else {
                            String body = doc.getString("text");
                            if (body == null) continue;
                            TextView bubble = makeText(body, 16, WHITE);
                            bubble.setPadding(12, 10, 12, 10);
                            GradientDrawable shape = new GradientDrawable();
                            shape.setColor(mine ? GREEN : Color.rgb(52, 65, 59));
                            shape.setCornerRadius(16);
                            bubble.setBackground(shape);
                            LinearLayout.LayoutParams p =
                                    new LinearLayout.LayoutParams(-2, -2);
                            p.setMargins(0, 5, 0, 5);
                            p.gravity = mine ? Gravity.RIGHT : Gravity.LEFT;
                            messagesContainer.addView(bubble, p);
                        }
                    }
                    messagesScroll.post(() ->
                            messagesScroll.fullScroll(View.FOCUS_DOWN));
                });
    }

    private void sendMessage() {
        if (messageInput == null || activeChat.isEmpty()) return;
        String body = messageInput.getText().toString().trim();
        if (body.isEmpty()) return;
        if (body.length() > 2000) {
            toast("الرسالة طويلة جداً.");
            return;
        }
        final String chatId = activeChat;
        Map<String, Object> data = new HashMap<>();
        data.put("senderId", uid);
        data.put("type", "text");
        data.put("text", body);
        data.put("createdAt", FieldValue.serverTimestamp());
        db.collection("chats").document(chatId).collection("messages")
                .add(data)
                .addOnSuccessListener(ref -> {
                    if (messageInput != null && chatId.equals(activeChat))
                        messageInput.setText("");
                    db.collection("chats").document(chatId)
                            .update("updatedAt", FieldValue.serverTimestamp());
                })
                .addOnFailureListener(e -> toast("تعذر إرسال الرسالة."));
    }

    // ---------------- تسجيل الصوت محلياً ----------------
    // الرفع والتنزيل يحتاجان Backend آمن. لا تضع Dosya API secret داخل التطبيق.

    private void startVoiceRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO);
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
            audioFile = new File(dir, "voice_" +
                    System.currentTimeMillis() + ".m4a");
            if (Build.VERSION.SDK_INT >= 31) recorder = new MediaRecorder(this);
            else recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(audioFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            isRecording = true;
            voiceButton.setText("إيقاف التسجيل");
            toast("بدأ التسجيل.");
        } catch (Exception e) {
            isRecording = false;
            releaseRecorder();
            toast("تعذر بدء التسجيل.");
        }
    }

    private void stopVoiceRecording() {
        if (!isRecording || recorder == null) return;
        try {
            recorder.stop();
            isRecording = false;
            releaseRecorder();
            if (voiceButton != null)
                voiceButton.setText("🎙️ تسجيل رسالة صوتية");
            toast("تم حفظ التسجيل على الجهاز. يلزم إعداد خادم آمن لرفعه وإرساله.");
        } catch (Exception e) {
            isRecording = false;
            releaseRecorder();
            toast("التسجيل لم يكتمل.");
        }
    }

    private void releaseRecorder() {
        if (recorder != null) {
            try { recorder.reset(); } catch (Exception ignored) {}
            try { recorder.release(); } catch (Exception ignored) {}
            recorder = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecording();
            } else {
                toast("اسمح باستخدام الميكروفون لتسجيل الصوت.");
            }
        }
    }

    // ---------------- الواجهة والمساعدة ----------------

    private void showLoading(String value) {
        root.removeAllViews();
        TextView text = makeText(value, 21, GOLD);
        text.setGravity(Gravity.CENTER);
        root.addView(text, new LinearLayout.LayoutParams(-1, 0, 1));
    }

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

    private TextView makeText(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private EditText addInput(String hint, int inputType) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(WHITE);
        input.setHintTextColor(Color.LTGRAY);
        input.setInputType(inputType);
        root.addView(input, new LinearLayout.LayoutParams(-1, -2));
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

    private void addButton(String value, Runnable action) {
        Button button = makeButton(value);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 6, 0, 6);
        root.addView(button, p);
    }

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

    private String errorText(Exception e) {
        return e.getLocalizedMessage() == null ?
                "خطأ غير معروف" : e.getLocalizedMessage();
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        screenVersion++;
        closeAllListeners();
        if (isRecording) {
            try {
                if (recorder != null) recorder.stop();
            } catch (Exception ignored) {}
            isRecording = false;
            releaseRecorder();
        }
        if (audioPlayer != null) {
            try {
                if (audioPlayer.isPlaying()) audioPlayer.stop();
            } catch (Exception ignored) {}
            try { audioPlayer.release(); } catch (Exception ignored) {}
            audioPlayer = null;
        }
        super.onDestroy();
    }
}
