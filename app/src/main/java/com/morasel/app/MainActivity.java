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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private LinearLayout root;
    private LinearLayout chatList;
    private EditText messageInput;
    private LinearLayout messagesContainer;
    private ScrollView messagesScroll;
    private Button voiceButton;

    private ListenerRegistration chatsListener;
    private ListenerRegistration messagesListener;

    private String uid = "";
    private String username = "";
    private String activeChat = "";

    private int screenVersion = 0;

    private boolean firstMessagesSnapshot = true;
    private boolean chatSoundEnabled = true;
    private boolean isRecording = false;

    private MediaRecorder recorder;
    private File audioFile;
    private MediaPlayer audioPlayer;

    private final ExecutorService voiceExecutor =
            Executors.newSingleThreadExecutor();

    /*
     * Dosya settings
     *
     * للتجربة فقط:
     * ضع مفتاح الرفع في DOSYA_UPLOAD_KEY.
     * ضع مفتاح القراءة في DOSYA_READ_KEY.
     *
     * لا تنشر APK يحتوي على مفاتيحك.
     */
    private static final String DOSYA_API =
            "https://api.dosya.dev/api";

    private static final String DOSYA_WORKSPACE =
            "ws_vPZ6ow2qDd4mUQcg8OFgs";

    private static final String DOSYA_UPLOAD_KEY =
            "dos_xxabf4yMt7LJnYaLKYVkw3kHv1d72U4I";

    private static final String DOSYA_READ_KEY =
            "dos_zedIiRuTipA4AbjDpVegv3hk2MRBdw63";

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

    // =====================================================
    // الأصوات القصيرة الموجودة في موارد التطبيق
    // =====================================================

    private void playSound(int resourceId) {
        MediaPlayer player = null;

        try {
            player = MediaPlayer.create(this, resourceId);

            if (player == null) return;

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

    // =====================================================
    // تسجيل الدخول
    // =====================================================

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
                    toast("تعذر الدخول. تحقق من إعدادات Firebase.");
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
             private void loadProfile() {
    if (uid == null || uid.trim().isEmpty()) {
        showWelcome();
        toast("معرف المستخدم غير موجود.");
        return;
    }

    db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener(doc -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                String savedName = doc.getString("username");

                if (doc.exists()
                        && savedName != null
                        && !savedName.trim().isEmpty()) {

                    username = savedName.trim();
                    showHome();

                } else {
                    showCreateProfile();
                }
            })
            .addOnFailureListener(e -> {
                android.util.Log.e(
                        "Morasel",
                        "Profile loading failed",
                        e
                );

                showWelcome();

                String error = e.getMessage();

                toast("فشل تحميل الحساب: "
                        + (error == null
                        ? "خطأ غير معروف"
                        : error));
            });
             }

    // =====================================================
    // شاشة التحميل والترحيب
    // =====================================================

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

    // =====================================================
    // إنشاء الحساب
    // =====================================================

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

    // =====================================================
    // الشاشة الرئيسية
    // =====================================================

    private void showHome() {
        closeAllListeners();

        activeChat = "";
        messageInput = null;
        chatList = null;
        messagesContainer = null;
        screenVersion++;

        root.removeAllViews();

        addHeading("مراسل");
        addText("مرحبًا، " + username);

        EditText search = addInput(
                "ابحث باسم المستخدم",
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
                            toast("فشل البحث. تحقق من الإنترنت."));
        });

        addText("محادثاتك");

        ScrollView scroll = new ScrollView(this);

        chatList = new LinearLayout(this);
        chatList.setOrientation(LinearLayout.VERTICAL);

        scroll.addView(chatList);

        root.addView(scroll,
                new LinearLayout.LayoutParams(-1, 0, 1));

        listenChats();
    }

    // =====================================================
    // تحميل المحادثات
    // =====================================================

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
                                            openChat(chatId, otherUid, finalName));

                                    chatList.addView(button);
                                });
                    }
                });
    }

    // =====================================================
    // إنشاء أو فتح محادثة
    // =====================================================

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

    // =====================================================
    // شاشة المحادثة
    // =====================================================

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

        addHeading("محادثة: " + partnerName);
        addButton("رجوع", this::showHome);

        messagesScroll = new ScrollView(this);

        messagesContainer = new LinearLayout(this);
        messagesContainer.setOrientation(LinearLayout.VERTICAL);
        messagesContainer.setPadding(4, 4, 4, 4);

        messagesScroll.addView(messagesContainer);

        root.addView(messagesScroll,
                new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textRow = new LinearLayout(this);
        textRow.setOrientation(LinearLayout.HORIZONTAL);
        textRow.setGravity(Gravity.CENTER_VERTICAL);

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

        textRow.addView(messageInput,
                new LinearLayout.LayoutParams(0, -2, 1));

        Button send = makeButton("إرسال");
        textRow.addView(send);

        send.setOnClickListener(v -> sendMessage());

        bottom.addView(textRow);

        voiceButton = makeButton("🎙️ بدء تسجيل الصوت");
        bottom.addView(voiceButton,
                new LinearLayout.LayoutParams(-1, -2));

        voiceButton.setOnClickListener(v -> {
            if (isRecording) {
                stopVoiceRecording();
            } else {
                startVoiceRecording();
            }
        });

        LinearLayout.LayoutParams bottomParams =
                new LinearLayout.LayoutParams(-1, -2);

        bottomParams.bottomMargin =
                (int) (8 * getResources().getDisplayMetrics().density);

        root.addView(bottom, bottomParams);

        playChatSound();

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
                        toast("تعذر تحميل الرسائل.");
                        return;
                    }

                    if (snapshot == null) return;

                    boolean incomingMessage = false;

                    if (!firstMessagesSnapshot) {
                        for (DocumentChange change :
                                snapshot.getDocumentChanges()) {

                            if (change.getType()
                                    == DocumentChange.Type.ADDED) {

                                String sender =
                                        change.getDocument()
                                                .getString("senderId");

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

                    messagesContainer.removeAllViews();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String sender = doc.getString("senderId");
                        String type = doc.getString("type");
                        String body = doc.getString("text");

                        boolean mine = uid.equals(sender);

                        if ("audio".equals(type)) {
                            String fileId = doc.getString("audioFileId");

                            if (fileId == null || fileId.trim().isEmpty()) {
                                continue;
                            }

                            Button play = makeButton(
                                    mine ? "▶️ تشغيل صوتي" : "▶️ تشغيل الرسالة الصوتية"
                            );

                            LinearLayout.LayoutParams params =
                                    new LinearLayout.LayoutParams(-2, -2);

                            params.setMargins(0, 5, 0, 5);
                            params.gravity = mine
                                    ? Gravity.END
                                    : Gravity.START;

                            messagesContainer.addView(play, params);

                            play.setOnClickListener(v ->
                                    downloadAndPlayAudio(fileId));

                        } else if (body != null) {
                            TextView bubble = makeText(body, 16, WHITE);
                            bubble.setPadding(12, 10, 12, 10);

                            GradientDrawable shape = new GradientDrawable();
                            shape.setColor(mine
                                    ? GREEN
                                    : Color.rgb(52, 65, 59));
                            shape.setCornerRadius(16);
                            bubble.setBackground(shape);

                            LinearLayout.LayoutParams params =
                                    new LinearLayout.LayoutParams(-2, -2);

                            params.setMargins(0, 5, 0, 5);
                            params.gravity = mine
                                    ? Gravity.END
                                    : Gravity.START;

                            messagesContainer.addView(bubble, params);
                        }
                    }

                    messagesScroll.post(() ->
                            messagesScroll.fullScroll(View.FOCUS_DOWN));
                });
    }

    // =====================================================
    // تسجيل الصوت
    // =====================================================

    private void startVoiceRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    501
            );
            return;
        }

        try {
            File dir = getExternalFilesDir("voice");

            if (dir == null) {
                toast("تعذر الوصول إلى مجلد التسجيل.");
                return;
            }

            if (!dir.exists() && !dir.mkdirs()) {
                toast("تعذر إنشاء مجلد التسجيل.");
                return;
            }

            audioFile = new File(
                    dir,
                    "voice_" + System.currentTimeMillis() + ".m4a"
            );

            if (Build.VERSION.SDK_INT >= 31) {
                recorder = new MediaRecorder(this);
            } else {
                recorder = new MediaRecorder();
            }

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(audioFile.getAbsolutePath());

            recorder.prepare();
            recorder.start();

            isRecording = true;

            if (voiceButton != null) {
                voiceButton.setText("⏹️ إيقاف التسجيل وإرسال");
            }

            toast("جارٍ تسجيل الصوت...");

        } catch (Exception e) {
            isRecording = false;
            releaseRecorder();
            toast("تعذر بدء تسجيل الصوت.");
        }
    }

    private void stopVoiceRecording() {
        if (!isRecording || recorder == null) return;

        try {
            recorder.stop();
            isRecording = false;
            releaseRecorder();

            if (voiceButton != null) {
                voiceButton.setText("🎙️ بدء تسجيل الصوت");
            }

            if (audioFile == null || !audioFile.exists()
                    || audioFile.length() == 0) {
                toast("ملف الصوت فارغ.");
                return;
            }

            toast("جارٍ رفع الصوت إلى Dosya...");
            uploadVoiceAndSend(audioFile);

        } catch (RuntimeException e) {
            isRecording = false;
            releaseRecorder();

            if (voiceButton != null) {
                voiceButton.setText("🎙️ بدء تسجيل الصوت");
            }

            toast("التسجيل قصير جدًا أو لم يكتمل.");
        }
    }

    private void releaseRecorder() {
        if (recorder != null) {
            try {
                recorder.reset();
            } catch (Exception ignored) {
            }

            try {
                recorder.release();
            } catch (Exception ignored) {
            }

            recorder = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);

        if (requestCode == 501) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecording();
            } else {
                toast("يجب السماح باستخدام الميكروفون لتسجيل الصوت.");
            }
        }
    }

    // =====================================================
    // رفع التسجيل إلى Dosya ثم حفظ الرسالة في Firebase
    // =====================================================

    private void uploadVoiceAndSend(File file) {
        if (file == null || !file.exists()) {
            toast("ملف الصوت غير موجود.");
            return;
        }

        final String chatId = activeChat;
        final String senderId = uid;

        if (chatId.isEmpty() || senderId.isEmpty()) {
            toast("افتح محادثة أولًا.");
            return;
        }

        voiceExecutor.execute(() -> {
            HttpURLConnection conn = null;

            try {
                if (DOSYA_UPLOAD_KEY.equals("PUT_UPLOAD_KEY_HERE")
                        || DOSYA_UPLOAD_KEY.trim().isEmpty()) {
                    throw new IOException(
                            "أدخل مفتاح Dosya للرفع في DOSYA_UPLOAD_KEY.");
                }

                URL initUrl = new URL(DOSYA_API + "/upload/init");
                conn = (HttpURLConnection) initUrl.openConnection();

                conn.setRequestMethod("POST");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(30000);
                conn.setDoOutput(true);

                conn.setRequestProperty(
                        "Authorization", "Bearer " + DOSYA_UPLOAD_KEY);
                conn.setRequestProperty(
                        "Content-Type", "application/json");

                JSONObject init = new JSONObject();
                init.put("file_name", file.getName());
                init.put("file_size", file.length());
                init.put("workspace_id", DOSYA_WORKSPACE);
                init.put("mime_type", "audio/mp4");

                try (OutputStream out = conn.getOutputStream()) {
                    out.write(init.toString().getBytes("UTF-8"));
                }

                int status = conn.getResponseCode();
                String initResponse = readConnection(conn);

                if (status < 200 || status >= 300) {
                    throw new IOException(
                            "فشل بدء الرفع إلى Dosya: " + initResponse);
                }

                JSONObject initResult = new JSONObject(initResponse);

                if (!initResult.optBoolean("ok", false)) {
                    throw new IOException(
                            initResult.optString("error", "فشل بدء الرفع."));
                }

                String uploadPath = initResult.getString("upload_url");

                URL uploadUrl;

                if (uploadPath.startsWith("http://")
                        || uploadPath.startsWith("https://")) {
                    uploadUrl = new URL(uploadPath);
                } else {
                    uploadUrl = new URL(
                            "https://api.dosya.dev" + uploadPath);
                }

                conn.disconnect();

                conn = (HttpURLConnection) uploadUrl.openConnection();
                conn.setRequestMethod("PUT");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(60000);
                conn.setDoOutput(true);

                conn.setRequestProperty(
                        "Authorization", "Bearer " + DOSYA_UPLOAD_KEY);
                conn.setRequestProperty(
                        "Content-Type", "application/octet-stream");

                conn.setFixedLengthStreamingMode(file.length());

                try (InputStream in = new FileInputStream(file);
                     OutputStream out = conn.getOutputStream()) {

                    byte[] buffer = new byte[8192];
                    int count;

                    while ((count = in.read(buffer)) != -1) {
                        out.write(buffer, 0, count);
                    }
                }

                status = conn.getResponseCode();
                String uploadResponse = readConnection(conn);

                if (status < 200 || status >= 300) {
                    throw new IOException(
                            "فشل رفع الملف: " + uploadResponse);
                }

                JSONObject uploadResult =
                        new JSONObject(uploadResponse);

                if (!uploadResult.optBoolean("ok", false)) {
                    throw new IOException("Dosya لم يؤكد نجاح الرفع.");
                }

                JSONObject uploadedFile =
                        uploadResult.getJSONObject("file");

                String fileId = uploadedFile.getString("id");

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    Map<String, Object> message = new HashMap<>();
                    message.put("senderId", senderId);
                    message.put("type", "audio");
                    message.put("audioFileId", fileId);
                    message.put("createdAt",
                            FieldValue.serverTimestamp());

                    db.collection("chats")
                            .document(chatId)
                            .collection("messages")
                            .add(message)
                            .addOnSuccessListener(ref -> {
                                playSendSound();

                                db.collection("chats")
                                        .document(chatId)
                                        .update(
                                                "updatedAt",
                                                FieldValue.serverTimestamp()
                                        );

                                toast("تم رفع الصوت وإرسال الرسالة.");
                            })
                        .addOnFailureListener(e -> {
    android.util.Log.e(
            "Morasel",
            "Audio message Firestore error",
            e
    );

    toast("فشل حفظ الصوت: " + e.getMessage());
}); 
                });

            } catch (Exception e) {
                String message = e.getMessage();

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    toast("تعذر رفع الصوت: "
                            + (message == null ? "خطأ غير معروف" : message));
                });

            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    // =====================================================
    // تنزيل الصوت من Dosya وتشغيله
    // =====================================================

    private void downloadAndPlayAudio(String fileId) {
        if (DOSYA_READ_KEY.equals("PUT_READ_KEY_HERE")
                || DOSYA_READ_KEY.trim().isEmpty()) {
            toast("أدخل مفتاح القراءة في DOSYA_READ_KEY.");
            return;
        }

        toast("جارٍ تنزيل الرسالة الصوتية...");

        voiceExecutor.execute(() -> {
            HttpURLConnection conn = null;

            try {
                URL url = new URL(
                        DOSYA_API + "/files/" + fileId + "/download");

                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(60000);

                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty(
                        "Authorization", "Bearer " + DOSYA_READ_KEY);

                int status = conn.getResponseCode();

                String downloadUrl = conn.getHeaderField("Location");

                if (status != HttpURLConnection.HTTP_MOVED_TEMP
                        && status != HttpURLConnection.HTTP_MOVED_PERM
                        && status != 307
                        && status != 308) {

                    String error = readConnection(conn);
                    throw new IOException(
                            "تعذر الحصول على رابط الصوت: " + error);
                }

                if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
                    throw new IOException("لم يرجع Dosya رابط التنزيل.");
                }

                conn.disconnect();
                conn = null;

                URL signedUrl = new URL(downloadUrl);
                URLConnection downloadConnection =
                        signedUrl.openConnection();

                downloadConnection.setConnectTimeout(20000);
                downloadConnection.setReadTimeout(60000);

                File cacheFile = File.createTempFile(
                        "morasel_voice_", ".m4a", getCacheDir());

                try (InputStream in =
                             downloadConnection.getInputStream();
                     FileOutputStream out =
                             new FileOutputStream(cacheFile)) {

                    byte[] buffer = new byte[8192];
                    int count;

                    while ((count = in.read(buffer)) != -1) {
                        out.write(buffer, 0, count);
                    }
                }

                runOnUiThread(() -> playAudioFile(cacheFile));

            } catch (Exception e) {
                String error = e.getMessage();

                runOnUiThread(() ->
                        toast("تعذر تشغيل الصوت: "
                                + (error == null ? "خطأ غير معروف" : error)));

            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void playAudioFile(File file) {
        stopAudioPlayer();

        try {
            audioPlayer = new MediaPlayer();

            audioPlayer.setDataSource(file.getAbsolutePath());

            audioPlayer.setOnPreparedListener(player -> {
                player.start();
                toast("جارٍ تشغيل الرسالة الصوتية.");
            });

            audioPlayer.setOnCompletionListener(player -> {
                stopAudioPlayer();
                file.delete();
            });

            audioPlayer.setOnErrorListener((player, what, extra) -> {
                stopAudioPlayer();
                file.delete();
                toast("تعذر تشغيل ملف الصوت.");
                return true;
            });

            audioPlayer.prepareAsync();

        } catch (Exception e) {
            stopAudioPlayer();
            file.delete();
            toast("تعذر فتح التسجيل.");
        }
    }

    private void stopAudioPlayer() {
        if (audioPlayer != null) {
            try {
                if (audioPlayer.isPlaying()) {
                    audioPlayer.stop();
                }
            } catch (Exception ignored) {
            }

            try {
                audioPlayer.release();
            } catch (Exception ignored) {
            }

            audioPlayer = null;
        }
    }

    private String readConnection(HttpURLConnection conn)
            throws IOException {

        InputStream stream;

        int status = conn.getResponseCode();

        if (status >= 400) {
            stream = conn.getErrorStream();
        } else {
            stream = conn.getInputStream();
        }

        if (stream == null) return "";

        try (InputStream in = stream;
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[4096];
            int count;

            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }

            return out.toString("UTF-8");
        }
    }

    // =====================================================
    // إرسال رسالة نصية
    // =====================================================

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
        message.put("type", "text");
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

    // =====================================================
    // إغلاق مستمعي Firebase
    // =====================================================

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

    // =====================================================
    // عناصر الواجهة
    // =====================================================

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

        if (isRecording) {
            isRecording = false;
            releaseRecorder();
        }

        stopAudioPlayer();
        voiceExecutor.shutdownNow();

        chatList = null;
        messageInput = null;
        messagesContainer = null;
        messagesScroll = null;
        voiceButton = null;

        super.onDestroy();
    }
                  }
