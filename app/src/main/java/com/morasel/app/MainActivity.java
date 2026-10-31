
package com.morasel.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider.OnVerificationStateChangedCallbacks;
import com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    private final int BG = Color.rgb(15, 29, 43);
    private final int PANEL = Color.rgb(25, 45, 62);
    private final int BLUE = Color.rgb(24, 104, 150);
    private final int GOLD = Color.rgb(239, 193, 77);
    private final int WHITE = Color.WHITE;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private LinearLayout root;
    private EditText emailInput, passwordInput;
    private EditText phoneInput, codeInput;

    private String verificationId = "";
    private ForceResendingToken resendToken;

    private String currentChatId = "";

    private ListenerRegistration messagesListener;
    private ListenerRegistration chatsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser user = auth.getCurrentUser();

        if (user != null) {
            loadProfile();
        } else {
            showLogin();
        }
    }

    // -------------------------------
    // أدوات تصميم الواجهة
    // -------------------------------

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private void startScreen(String title) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 20, 20, 20);
        root.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);

        setContentView(scroll);

        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextColor(GOLD);
        heading.setTextSize(27);
        heading.setGravity(Gravity.CENTER);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(5, 15, 5, 25);

        root.addView(heading);
    }

    private TextView text(String value, int size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(WHITE);
        t.setTextSize(size);
        t.setPadding(8, 12, 8, 12);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private EditText edit(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextColor(WHITE);
        e.setHintTextColor(Color.LTGRAY);
        e.setTextSize(16);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setPadding(15, 12, 15, 12);
        e.setBackground(shape(PANEL, 15));

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(
                        -1, -2);
        p.setMargins(0, 7, 0, 7);
        root.addView(e, p);

        return e;
    }

    private void button(String value, Runnable action) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(WHITE);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setBackground(shape(BLUE, 15));

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 7, 0, 7);

        root.addView(b, p);

        b.setOnClickListener(v -> action.run());
    }

    private void message(String value) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }

    // -------------------------------
    // شاشة تسجيل الدخول
    // -------------------------------

    private void showLogin() {
        closeListeners();

        startScreen("مراسل");
        root.addView(text("مرحباً بك في تطبيق مراسل", 18));
        root.addView(text("اختر طريقة تسجيل الدخول", 15));

        button("تسجيل الدخول بالبريد الإلكتروني", this::showEmailLogin);
        button("تسجيل الدخول برقم الهاتف", this::showPhoneLogin);
    }

    // -------------------------------
    // تسجيل الدخول بالبريد الإلكتروني
    // -------------------------------

    private void showEmailLogin() {
        startScreen("الدخول بالبريد الإلكتروني");

        emailInput = edit(
                "البريد الإلكتروني",
                android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        );

        passwordInput = edit(
                "كلمة المرور",
                android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        button("تسجيل الدخول", this::loginEmail);
        button("إنشاء حساب جديد", this::registerEmail);
        button("نسيت كلمة المرور؟", this::resetPassword);
        button("العودة", this::showLogin);
    }

    private void loginEmail() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (email.isEmpty() || password.isEmpty()) {
            message("أدخل البريد وكلمة المرور");
            return;
        }

        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    message("تم تسجيل الدخول");
                    saveAuthenticatedUser();
                })
                .addOnFailureListener(e ->
                        message("فشل تسجيل الدخول: " + e.getMessage()));
    }

    private void registerEmail() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (email.isEmpty() || password.length() < 6) {
            message("أدخل بريداً صحيحاً وكلمة مرور من 6 أحرف على الأقل");
            return;
        }

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    message("تم إنشاء الحساب");
                    saveAuthenticatedUser();
                })
                .addOnFailureListener(e ->
                        message("تعذر إنشاء الحساب: " + e.getMessage()));
    }

    private void resetPassword() {
        String email = emailInput.getText().toString().trim();

        if (email.isEmpty()) {
            message("أدخل بريدك الإلكتروني أولاً");
            return;
        }

        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(v ->
                        message("تم إرسال رابط استعادة كلمة المرور إلى بريدك"))
                .addOnFailureListener(e ->
                        message("تعذر إرسال الرابط: " + e.getMessage()));
    }

    // -------------------------------
    // تسجيل الدخول برقم الهاتف
    // -------------------------------

    private void showPhoneLogin() {
        startScreen("الدخول برقم الهاتف");

        root.addView(text(
                "اكتب رقمك مع مفتاح الدولة، مثال: +9677XXXXXXXX",
                14
        ));

        phoneInput = edit(
                "+967XXXXXXXXX",
                android.text.InputType.TYPE_CLASS_PHONE
        );

        button("إرسال رمز التحقق", this::sendPhoneCode);
        button("العودة", this::showLogin);
    }

    private void sendPhoneCode() {
        String phone = phoneInput.getText().toString().trim();

        if (phone.isEmpty()) {
            message("أدخل رقم الهاتف");
            return;
        }

        if (!phone.startsWith("+")) {
            message("اكتب الرقم الدولي مع مفتاح الدولة مثل +967");
            return;
        }

        PhoneAuthOptions options =
                PhoneAuthOptions.newBuilder(auth)
                        .setPhoneNumber(phone)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(this)
                        .setCallbacks(phoneCallbacks)
                        .build();

        PhoneAuthProvider.verifyPhoneNumber(options);

        message("جارٍ طلب رمز التحقق");
    }

    private final OnVerificationStateChangedCallbacks phoneCallbacks =
            new OnVerificationStateChangedCallbacks() {

        @Override
        public void onVerificationCompleted(PhoneAuthCredential credential) {
            signInPhone(credential);
        }

        @Override
        public void onVerificationFailed(
                com.google.firebase.FirebaseException e) {

            message("فشل إرسال الرمز: " + e.getMessage());
        }

        @Override
        public void onCodeSent(
                String id,
                ForceResendingToken token) {

            verificationId = id;
            resendToken = token;

            showCodeScreen();
        }
    };

    private void showCodeScreen() {
        startScreen("التحقق من رقم الهاتف");

        root.addView(text("أدخل رمز التحقق الذي وصلك برسالة SMS", 15));

        codeInput = edit(
                "رمز التحقق",
                android.text.InputType.TYPE_CLASS_NUMBER
        );

        button("تأكيد الرمز", () -> {
            String code = codeInput.getText().toString().trim();

            if (code.isEmpty() || verificationId.isEmpty()) {
                message("أدخل رمز التحقق");
                return;
            }

            PhoneAuthCredential credential =
                    PhoneAuthProvider.getCredential(verificationId, code);

            signInPhone(credential);
        });

        button("العودة", this::showPhoneLogin);
    }

    private void signInPhone(PhoneAuthCredential credential) {
        auth.signInWithCredential(credential)
                .addOnSuccessListener(result -> {
                    message("تم تسجيل الدخول بالهاتف");
                    saveAuthenticatedUser();
                })
                .addOnFailureListener(e ->
                        message("تعذر التحقق: " + e.getMessage()));
    }

    // -------------------------------
    // حفظ بيانات المستخدم
    // -------------------------------

    private void saveAuthenticatedUser() {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            message("لم يتم العثور على الحساب");
            return;
        }

        Map<String, Object> data = new HashMap<>();

        data.put("uid", user.getUid());
        data.put("email", user.getEmail() == null ? "" : user.getEmail());
        data.put("phone", user.getPhoneNumber() == null ? "" : user.getPhoneNumber());
        data.put("lastLoginAt", FieldValue.serverTimestamp());

        db.collection("users")
                .document(user.getUid())
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(v -> loadProfile())
                .addOnFailureListener(e -> {
                    message("تم تسجيل الدخول لكن تعذر حفظ البيانات: "
                            + e.getMessage());
                });
    }

    // -------------------------------
    // تحميل الملف الشخصي
    // -------------------------------

    private void loadProfile() {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            showLogin();
            return;
        }

        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(document -> {

                    if (document.exists()
                            && document.getString("username") != null) {
                        showHome();
                    } else {
                        showCreateProfile();
                    }

                })
                .addOnFailureListener(e ->
                        message("تعذر قراءة بيانات الحساب: " + e.getMessage()));
    }

    // -------------------------------
    // إنشاء اسم المستخدم
    // -------------------------------

    private void showCreateProfile() {
        startScreen("إنشاء ملفك الشخصي");

        root.addView(text("اختر اسماً يظهر للمستخدمين", 16));

        EditText username = edit(
                "اسم المستخدم",
                android.text.InputType.TYPE_CLASS_TEXT
        );

        button("حفظ الاسم", () -> {
            String name = username.getText().toString().trim();

            if (name.length() < 3) {
                message("يجب أن يكون الاسم 3 أحرف على الأقل");
                return;
            }

            saveUsername(name);
        });

        button("تسجيل الخروج", this::logout);
    }

    private void saveUsername(String name) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) return;

        String lower = name.toLowerCase();

        db.collection("users")
                .whereEqualTo("usernameLower", lower)
                .get()
                .addOnSuccessListener(result -> {

                    boolean taken = false;

                    for (com.google.firebase.firestore.DocumentSnapshot d
                            : result.getDocuments()) {

                        if (!d.getId().equals(user.getUid())) {
                            taken = true;
                            break;
                        }
                    }

                    if (taken) {
                        message("اسم المستخدم مستخدم بالفعل");
                        return;
                    }

                    Map<String, Object> data = new HashMap<>();
                    data.put("uid", user.getUid());
                    data.put("username", name);
                    data.put("usernameLower", lower);

                    db.collection("users")
                            .document(user.getUid())
                            .set(data,
                                    com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener(v -> {
                                message("تم حفظ الملف الشخصي");
                                showHome();
                            })
                            .addOnFailureListener(e ->
                                    message("تعذر حفظ الاسم: " + e.getMessage()));
                })
                .addOnFailureListener(e ->
                        message("تعذر فحص الاسم: " + e.getMessage()));
    }

    // -------------------------------
    // الصفحة الرئيسية
    // -------------------------------

    private void showHome() {
        closeListeners();

        startScreen("مراسل");

        root.addView(text("أهلاً بك في مراسل", 20));

        EditText search = edit(
                "ابحث باسم المستخدم",
                android.text.InputType.TYPE_CLASS_TEXT
        );

        button("بحث عن مستخدم", () ->
                searchUsers(search.getText().toString().trim()));

        root.addView(text("المحادثات", 18));

        LinearLayout chatsArea = new LinearLayout(this);
        chatsArea.setOrientation(LinearLayout.VERTICAL);
        root.addView(chatsArea);

        FirebaseUser user = auth.getCurrentUser();

        if (user != null) {
            chatsListener = db.collection("chats")
                    .whereArrayContains("participants", user.getUid())
                    .addSnapshotListener((snapshot, error) -> {

                        if (error != null || snapshot == null) return;

                        chatsArea.removeAllViews();

                        for (com.google.firebase.firestore.DocumentSnapshot doc
                                : snapshot.getDocuments()) {

                            String chatId = doc.getId();

                            Button b = new Button(this);
                            b.setText("فتح المحادثة");
                            b.setAllCaps(false);

                            chatsArea.addView(b);

                            b.setOnClickListener(v ->
                                    openChat(chatId));
                        }
                    });
        }

        button("تسجيل الخروج", this::logout);
    }

    // -------------------------------
    // البحث عن المستخدمين
    // -------------------------------

    private void searchUsers(String name) {
        if (name.isEmpty()) {
            message("اكتب اسم المستخدم");
            return;
        }

        db.collection("users")
                .whereEqualTo("usernameLower", name.toLowerCase())
                .get()
                .addOnSuccessListener(result -> {

                    FirebaseUser me = auth.getCurrentUser();

                    if (me == null) return;

                    if (result.isEmpty()) {
                        message("لم يتم العثور على المستخدم");
                        return;
                    }

                    for (com.google.firebase.firestore.DocumentSnapshot doc
                            : result.getDocuments()) {

                        String otherUid = doc.getId();

                        if (otherUid.equals(me.getUid())) {
                            message("هذا حسابك أنت");
                            continue;
                        }

                        String username = doc.getString("username");

                        new android.app.AlertDialog.Builder(this)
                                .setTitle(username == null ? "مستخدم" : username)
                                .setMessage("هل تريد بدء محادثة؟")
                                .setPositiveButton("مراسلة", (dialog, which) ->
                                        createChat(otherUid))
                                .setNegativeButton("إلغاء", null)
                                .show();
                    }
                })
                .addOnFailureListener(e ->
                        message("فشل البحث: " + e.getMessage()));
    }

    // -------------------------------
    // إنشاء محادثة
    // -------------------------------

    private void createChat(String otherUid) {
        FirebaseUser me = auth.getCurrentUser();

        if (me == null) return;

        String myUid = me.getUid();

        String chatId = myUid.compareTo(otherUid) < 0
                ? myUid + "_" + otherUid
                : otherUid + "_" + myUid;

        Map<String, Object> data = new HashMap<>();
        java.util.List<String> participants = new java.util.ArrayList<>();
        participants.add(myUid);
        participants.add(otherUid);

        data.put("participants", participants);
        data.put("createdAt", FieldValue.serverTimestamp());

        db.collection("chats")
                .document(chatId)
                .set(data,
                        com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(v -> openChat(chatId))
                .addOnFailureListener(e ->
                        message("تعذر إنشاء المحادثة: " + e.getMessage()));
    }

    // -------------------------------
    // فتح المحادثة
    // -------------------------------

    private void openChat(String chatId) {
        currentChatId = chatId;

        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }

        startScreen("المحادثة");

        LinearLayout messagesArea = new LinearLayout(this);
        messagesArea.setOrientation(LinearLayout.VERTICAL);
        root.addView(messagesArea);

        EditText input = edit(
                "اكتب رسالتك هنا",
                android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );

        button("إرسال الرسالة", () -> {
            String value = input.getText().toString().trim();

            if (!value.isEmpty()) {
                sendMessage(value);
                input.setText("");
            }
        });

        button("العودة إلى الرئيسية", this::showHome);

        messagesListener = db.collection("chats")
                .document(chatId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null || snapshot == null) {
                        return;
                    }

                    messagesArea.removeAllViews();

                    for (com.google.firebase.firestore.DocumentSnapshot doc
                            : snapshot.getDocuments()) {

                        String value = doc.getString("text");

                        if (value == null) continue;

                        String sender = doc.getString("senderId");

                        TextView t = text(
                                (auth.getUid().equals(sender) ? "أنت: " : "المستخدم: ")
                                        + value,
                                16
                        );

                        t.setBackground(shape(PANEL, 12));

                        LinearLayout.LayoutParams p =
                                new LinearLayout.LayoutParams(-1, -2);

                        p.setMargins(0, 5, 0, 5);
                        messagesArea.addView(t, p);
                    }
                });
    }

    // -------------------------------
    // إرسال رسالة نصية
    // -------------------------------

    private void sendMessage(String value) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null || currentChatId.isEmpty()) return;

        Map<String, Object> data = new HashMap<>();

        data.put("senderId", user.getUid());
        data.put("text", value);
        data.put("type", "text");
        data.put("createdAt", FieldValue.serverTimestamp());

        db.collection("chats")
                .document(currentChatId)
                .collection("messages")
                .add(data)
                .addOnFailureListener(e ->
                        message("تعذر إرسال الرسالة: " + e.getMessage()));
    }

    // -------------------------------
    // تسجيل الخروج
    // -------------------------------

    private void logout() {
        closeListeners();
        auth.signOut();
        currentChatId = "";
        showLogin();
    }

    private void closeListeners() {
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }

        if (chatsListener != null) {
            chatsListener.remove();
            chatsListener = null;
        }
    }

    @Override
    protected void onDestroy() {
        closeListeners();
        super.onDestroy();
    }
}
