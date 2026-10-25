
package com.morasel.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.text.InputType;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String verificationId = "";
    private String currentUid = "";
    private String currentPhone = "";
    private String currentName = "";
    private String currentChatId = "";
    private String otherUid = "";
    private String otherName = "";

    private LinearLayout root;
    private EditText phoneInput, codeInput, nameInput;
    private EditText searchInput, messageInput;
    private LinearLayout messagesLayout;
    private ListenerRegistration messagesListener;

    private final int dark = Color.rgb(18, 34, 29);
    private final int green = Color.rgb(20, 95, 65);
    private final int gold = Color.rgb(239, 190, 75);
    private final int white = Color.WHITE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            FirebaseApp app = FirebaseApp.initializeApp(this);

            if (app == null) {
                showStartupError(
                        "تعذر إعداد Firebase. تحقق من google-services.json"
                );
                return;
            }

            auth = FirebaseAuth.getInstance(app);
            db = FirebaseFirestore.getInstance(app);

            if (auth.getCurrentUser() != null) {
                currentUid = auth.getCurrentUser().getUid();
                loadMyProfile();
            } else {
                showPhoneScreen();
            }

        } catch (Exception e) {
            showStartupError(
                    "خطأ في تشغيل التطبيق: "
                            + e.getClass().getSimpleName()
                            + ": " + e.getMessage()
            );
        }
    }

    private void showStartupError(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(18);
        view.setPadding(24, 32, 24, 32);
        view.setTextColor(Color.RED);
        setContentView(view);
    }

    private void setupScreen(String title) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 24, 20, 20);
        root.setBackgroundColor(dark);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(0, 10, 0, 10);

        TextView heading = new TextView(this);
        heading.setText("مراسل");
        heading.setTextColor(gold);
        heading.setTextSize(32);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        content.addView(heading);

        TextView subtitle = new TextView(this);
        subtitle.setText(title);
        subtitle.setTextColor(white);
        subtitle.setTextSize(18);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 12, 0, 22);
        content.addView(subtitle);

        scroll.addView(content);
        root.addView(
                scroll,
                new LinearLayout.LayoutParams(-1, 0, 1f)
        );

        setContentView(root);
        root.setTag(content);
    }

    private LinearLayout content() {
        return (LinearLayout) root.getTag();
    }

    private EditText input(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextColor(white);
        e.setHintTextColor(Color.LTGRAY);
        e.setTextSize(16);
        e.setSingleLine(true);
        e.setInputType(type);
        e.setPadding(16, 12, 16, 12);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(36, 53, 46));
        bg.setCornerRadius(14);
        bg.setStroke(1, green);
        e.setBackground(bg);

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 7, 0, 7);
        content().addView(e, lp);
        return e;
    }

    private void button(String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(dark);
        b.setTextSize(15);
        b.setTypeface(null, Typeface.BOLD);
        b.setAllCaps(false);
        b.setBackgroundColor(gold);

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 8, 0, 8);
        content().addView(b, lp);

        b.setOnClickListener(v -> {
            try {
                action.run();
            } catch (Exception e) {
                message("حدث خطأ: "
                        + e.getClass().getSimpleName()
                        + " - " + e.getMessage());
            }
        });
    }

    private void message(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    // شاشة تسجيل الدخول برقم الهاتف
    private void showPhoneScreen() {
        setupScreen("تسجيل الدخول برقم الهاتف");

        TextView info = new TextView(this);
        info.setText(
                "أدخل رقم هاتفك الدولي ثم اطلب رمز التحقق عبر SMS."
        );
        info.setTextColor(white);
        info.setTextSize(15);
        content().addView(info);

        phoneInput = input(
                "رقم الهاتف مثال +9677XXXXXXXX",
                InputType.TYPE_CLASS_PHONE
        );

        codeInput = input(
                "رمز التحقق SMS",
                InputType.TYPE_CLASS_NUMBER
        );

        button("إرسال رمز التحقق", this::sendCode);
        button("تأكيد الرمز والدخول", this::verifyCode);
    }

    // طلب رمز التحقق
    private void sendCode() {
        if (auth == null) {
            message("Firebase غير مهيأ. تحقق من إعدادات التطبيق.");
            return;
        }

        if (phoneInput == null) {
            message("حقل رقم الهاتف غير موجود.");
            return;
        }

        String phone = phoneInput.getText().toString().trim();

        if (!phone.matches("^\\+[1-9][0-9]{7,14}$")) {
            message("أدخل الرقم الدولي، مثال: +9677XXXXXXXX");
            return;
        }

        try {
            PhoneAuthOptions options =
                    PhoneAuthOptions.newBuilder(auth)
                            .setPhoneNumber(phone)
                            .setTimeout(60L, TimeUnit.SECONDS)
                            .setActivity(this)
                            .setCallbacks(
                                    new PhoneAuthProvider
                                            .OnVerificationStateChangedCallbacks() {

                                @Override
                                public void onVerificationCompleted(
                                        PhoneAuthCredential credential) {
                                    signInWithCredential(credential);
                                }

                                @Override
                                public void onVerificationFailed(
                                        FirebaseException e) {
                                    message(
                                            "فشل التحقق: "
                                                    + e.getClass()
                                                    .getSimpleName()
                                                    + ": "
                                                    + e.getMessage()
                                    );
                                }

                                @Override
                                public void onCodeSent(
                                        String id,
                                        PhoneAuthProvider
                                                .ForceResendingToken token) {
                                    verificationId = id;
                                    message(
                                            "تم إرسال الرمز. تحقق من رسائل SMS."
                                    );
                                }
                            })
                            .build();

            PhoneAuthProvider.verifyPhoneNumber(options);

            message("جارٍ طلب رمز التحقق...");

        } catch (Exception e) {
            message(
                    "تعذر طلب رمز التحقق: "
                            + e.getClass().getSimpleName()
                            + " - " + e.getMessage()
            );
        }
    }

    // تأكيد رمز SMS
    private void verifyCode() {
        String code = codeInput.getText().toString().trim();

        if (verificationId.isEmpty()) {
            message("اضغط إرسال رمز التحقق أولاً.");
            return;
        }

        if (code.isEmpty()) {
            message("أدخل رمز التحقق.");
            return;
        }

        try {
            PhoneAuthCredential credential =
                    PhoneAuthProvider.getCredential(
                            verificationId,
                            code
                    );

            signInWithCredential(credential);

        } catch (Exception e) {
            message("تعذر قراءة الرمز: " + e.getMessage());
        }
    }

    // تسجيل الدخول بعد التحقق من الهاتف
    private void signInWithCredential(
            PhoneAuthCredential credential) {

        auth.signInWithCredential(credential)
                .addOnSuccessListener(result -> {
                    if (auth.getCurrentUser() == null) {
                        message("تعذر إكمال تسجيل الدخول.");
                        return;
                    }

                    currentUid =
                            auth.getCurrentUser().getUid();

                    currentPhone =
                            auth.getCurrentUser().getPhoneNumber();

                    loadMyProfile();
                })
                .addOnFailureListener(e ->
                        message(
                                "فشل تسجيل الدخول: " + e.getMessage()
                        ));
    }

    // تحميل الحساب
    private void loadMyProfile() {
        if (auth.getCurrentUser() == null) {
            showPhoneScreen();
            return;
        }

        currentUid = auth.getCurrentUser().getUid();
        currentPhone = auth.getCurrentUser().getPhoneNumber();

        db.collection("users")
                .document(currentUid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        currentName = doc.getString("username");

                        if (currentName == null
                                || currentName.trim().isEmpty()) {
                            showCreateProfile();
                        } else {
                            showHome();
                        }
                    } else {
                        showCreateProfile();
                    }
                })
                .addOnFailureListener(e ->
                        message(
                                "تعذر تحميل الحساب: " + e.getMessage()
                        ));
    }

    // إنشاء اسم المستخدم
    private void showCreateProfile() {
        setupScreen("إنشاء حساب جديد");

        nameInput = input(
                "اكتب اسم المستخدم",
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );

        button("حفظ الحساب", () -> {
            String name =
                    nameInput.getText().toString().trim();

            if (name.isEmpty()) {
                message("اكتب اسم المستخدم أولاً.");
                return;
            }

            Map<String, Object> user = new HashMap<>();
            user.put("uid", currentUid);
            user.put("phoneNumber", currentPhone);
            user.put("username", name);
            user.put("online", true);
            user.put("createdAt", FieldValue.serverTimestamp());

            db.collection("users")
                    .document(currentUid)
                    .set(user, SetOptions.merge())
                    .addOnSuccessListener(v -> {
                        currentName = name;
                        showHome();
                    })
                    .addOnFailureListener(e ->
                            message(
                                    "تعذر حفظ الحساب: "
                                            + e.getMessage()
                            ));
        });
    }

    // الصفحة الرئيسية
    private void showHome() {
        setupScreen("مرحباً " + currentName);

        TextView profile = new TextView(this);
        profile.setText(
                "اسمك: " + currentName
                        + "\nرقمك: " + currentPhone
        );
        profile.setTextColor(white);
        profile.setTextSize(16);
        profile.setPadding(0, 8, 0, 16);
        content().addView(profile);

        searchInput = input(
                "رقم المستخدم الدولي",
                InputType.TYPE_CLASS_PHONE
        );

        button("بحث عن مستخدم", this::findUser);

        button("تسجيل الخروج", () -> {
            if (messagesListener != null) {
                messagesListener.remove();
                messagesListener = null;
            }

            auth.signOut();

            currentUid = "";
            currentPhone = "";
            currentName = "";
            currentChatId = "";

            showPhoneScreen();
        });
    }

    // البحث عن مستخدم برقم الهاتف
    private void findUser() {
        String phone =
                searchInput.getText().toString().trim();

        if (phone.isEmpty()) {
            message("أدخل رقم الهاتف الذي تريد البحث عنه.");
            return;
        }

        if (phone.equals(currentPhone)) {
            message("لا يمكنك بدء محادثة مع رقمك نفسه.");
            return;
        }

        db.collection("users")
                .whereEqualTo("phoneNumber", phone)
                .limit(1)
                .get()
                .addOnSuccessListener(result -> {
                    if (result.isEmpty()) {
                        message(
                                "لم يتم العثور على مستخدم بهذا الرقم."
                        );
                        return;
                    }

                    DocumentSnapshot user =
                            result.getDocuments().get(0);

                    otherUid = user.getId();
                    otherName = user.getString("username");

                    if (otherName == null
                            || otherName.isEmpty()) {
                        otherName = phone;
                    }

                    openOrCreateChat();
                })
                .addOnFailureListener(e ->
                        message("فشل البحث: " + e.getMessage()));
    }

    // إنشاء محادثة أو فتح محادثة موجودة
    private void openOrCreateChat() {
        ArrayList<String> participants = new ArrayList<>();
        participants.add(currentUid);
        participants.add(otherUid);
        Collections.sort(participants);

        currentChatId =
                participants.get(0) + "_" + participants.get(1);

        Map<String, Object> chat = new HashMap<>();
        chat.put("participants", participants);
        chat.put("createdAt", FieldValue.serverTimestamp());

        db.collection("chats")
                .document(currentChatId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        openChat();
                    } else {
                        db.collection("chats")
                                .document(currentChatId)
                                .set(chat)
                                .addOnSuccessListener(v -> openChat())
                                .addOnFailureListener(e ->
                                        message(
                                                "تعذر إنشاء المحادثة: "
                                                        + e.getMessage()
                                        ));
                    }
                })
                .addOnFailureListener(e ->
                        message(
                                "تعذر فتح المحادثة: "
                                        + e.getMessage()
                        ));
    }

    // شاشة المحادثة
    private void openChat() {
        setupScreen("المحادثة مع " + otherName);

        ScrollView scroll = new ScrollView(this);

        messagesLayout = new LinearLayout(this);
        messagesLayout.setOrientation(LinearLayout.VERTICAL);
        messagesLayout.setPadding(4, 4, 4, 4);

        scroll.addView(messagesLayout);

        content().addView(
                scroll,
                new LinearLayout.LayoutParams(-1, 0, 1f)
        );

        messageInput = input(
                "اكتب رسالتك هنا",
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );

        button("إرسال الرسالة", this::sendMessage);
        button("رجوع", this::backHome);

        if (messagesListener != null) {
            messagesListener.remove();
        }

        messagesListener = db.collection("chats")
                .document(currentChatId)
                .collection("messages")
                .orderBy(
                        "createdAt",
                        Query.Direction.ASCENDING
                )
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        message(
                                "تعذر تحميل الرسائل: "
                                        + error.getMessage()
                        );
                        return;
                    }

                    if (snapshot == null
                            || messagesLayout == null) {
                        return;
                    }

                    messagesLayout.removeAllViews();

                    for (DocumentSnapshot doc :
                            snapshot.getDocuments()) {

                        String text = doc.getString("text");
                        String sender = doc.getString("senderId");

                        boolean mine = currentUid.equals(sender);

                        TextView bubble = new TextView(this);
                        bubble.setText(
                                (mine ? "أنت: " : otherName + ": ")
                                        + (text == null ? "" : text)
                        );
                        bubble.setTextColor(white);
                        bubble.setTextSize(16);
                        bubble.setPadding(14, 10, 14, 10);

                        GradientDrawable bg =
                                new GradientDrawable();

                        bg.setColor(
                                mine ? green
                                        : Color.rgb(55, 65, 58)
                        );
                        bg.setCornerRadius(16);
                        bubble.setBackground(bg);

                        LinearLayout.LayoutParams lp =
                                new LinearLayout.LayoutParams(-1, -2);
                        lp.setMargins(0, 5, 0, 5);

                        messagesLayout.addView(bubble, lp);
                    }
                });
    }

    private void backHome() {
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }

        showHome();
    }

    // إرسال رسالة
    private void sendMessage() {
        String text =
                messageInput.getText().toString().trim();

        if (text.isEmpty()) {
            message("اكتب رسالة أولاً.");
            return;
        }

        if (text.length() > 2000) {
            message("الرسالة طويلة جداً؛ اختصرها.");
            return;
        }

        Map<String, Object> msg = new HashMap<>();
        msg.put("text", text);
        msg.put("senderId", currentUid);
        msg.put("createdAt", FieldValue.serverTimestamp());

        db.collection("chats")
                .document(currentChatId)
                .collection("messages")
                .add(msg)
                .addOnSuccessListener(ref -> {
                    messageInput.setText("");

                    Map<String, Object> update = new HashMap<>();
                    update.put("lastMessage", text);
                    update.put(
                            "lastMessageAt",
                            FieldValue.serverTimestamp()
                    );

                    db.collection("chats")
                            .document(currentChatId)
                            .update(update)
                            .addOnFailureListener(e ->
                                    message(
                                            "أُرسلت الرسالة، لكن تعذر تحديث آخر رسالة."
                                    ));
                })
                .addOnFailureListener(e ->
                        message(
                                "فشل إرسال الرسالة: "
                                        + e.getMessage()
                        ));
    }

    @Override
    protected void onDestroy() {
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }

        super.onDestroy();
    }
}
