
package com.morasel.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import android.view.ViewGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.util.*;

public class MainActivity extends Activity {
    FirebaseAuth auth;
    FirebaseFirestore db;
    LinearLayout page, messages;
    EditText emailInput, messageInput;
    String uid, chatId, otherUid;
    ListenerRegistration messageListener;

    int green = Color.rgb(7, 75, 53);

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() == null) {
            loginScreen();
        } else {
            uid = auth.getCurrentUser().getUid();
            homeScreen();
        }
    }

    TextView label(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(green);
        t.setPadding(12, 14, 12, 14);
        return t;
    }

    Button btn(String s, Runnable r) {
        Button b = new Button(this);
        b.setText(s);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    void startPage(String title) {
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(14, 12, 14, 12);
        page.addView(label(title, 23));
        setContentView(page);
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    void loginScreen() {
        startPage("مراسل - تسجيل الدخول");

        EditText email = new EditText(this);
        email.setHint("البريد الإلكتروني");
        page.addView(email);

        EditText pass = new EditText(this);
        pass.setHint("كلمة المرور");
        pass.setInputType(129);
        page.addView(pass);

        EditText name = new EditText(this);
        name.setHint("اسم المستخدم عند إنشاء الحساب");
        page.addView(name);

        page.addView(btn("إنشاء حساب", () -> {
            String e = email.getText().toString().trim();
            String p = pass.getText().toString();
            String n = name.getText().toString().trim();

            if (e.isEmpty() || p.length() < 6 || n.isEmpty()) {
                toast("أدخل الاسم والبريد وكلمة مرور من 6 أحرف على الأقل");
                return;
            }

            auth.createUserWithEmailAndPassword(e, p)
                .addOnSuccessListener(result -> {
                    uid = auth.getCurrentUser().getUid();
                    Map<String, Object> user = new HashMap<>();
                    user.put("uid", uid);
                    user.put("email", e);
                    user.put("username", n);
                    user.put("online", true);
                    user.put("lastSeen", FieldValue.serverTimestamp());

                    db.collection("users").document(uid).set(user)
                        .addOnSuccessListener(v -> homeScreen())
                        .addOnFailureListener(err -> toast(err.getMessage()));
                })
                .addOnFailureListener(err -> toast(err.getMessage()));
        }));

        page.addView(btn("تسجيل الدخول", () -> {
            auth.signInWithEmailAndPassword(
                    email.getText().toString().trim(),
                    pass.getText().toString())
                .addOnSuccessListener(result -> {
                    uid = auth.getCurrentUser().getUid();
                    homeScreen();
                })
                .addOnFailureListener(err -> toast(err.getMessage()));
        }));
    }

    void homeScreen() {
        startPage("مراسل 💬");

        emailInput = new EditText(this);
        emailInput.setHint("بريد المستخدم الذي تريد مراسلته");
        page.addView(emailInput);

        page.addView(btn("البحث وفتح المحادثة", this::findUser));
        page.addView(btn("تسجيل الخروج", () -> {
            auth.signOut();
            loginScreen();
        }));
    }

    void findUser() {
        String email = emailInput.getText().toString().trim();
        if (email.isEmpty()) {
            toast("أدخل بريد المستخدم");
            return;
        }

        db.collection("users")
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .addOnSuccessListener(snap -> {
                if (snap.isEmpty()) {
                    toast("لم يتم العثور على المستخدم. يجب أن يكون قد أنشأ حسابًا.");
                    return;
                }

                DocumentSnapshot user = snap.getDocuments().get(0);
                otherUid = user.getString("uid");

                if (otherUid == null || otherUid.equals(uid)) {
                    toast("اختر حساب مستخدم آخر");
                    return;
                }

                chatId = uid.compareTo(otherUid) < 0
                    ? uid + "_" + otherUid
                    : otherUid + "_" + uid;

                DocumentReference chat =
                    db.collection("chats").document(chatId);

                chat.get().addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        openChat();
                        return;
                    }

                    Map<String, Object> data = new HashMap<>();
                    data.put("participants", Arrays.asList(uid, otherUid));
                    data.put("lastMessage", "");
                    data.put("createdAt", FieldValue.serverTimestamp());

                    chat.set(data)
                        .addOnSuccessListener(v -> openChat())
                        .addOnFailureListener(err -> toast(err.getMessage()));
                });
            })
            .addOnFailureListener(err -> toast(err.getMessage()));
    }

    void openChat() {
        startPage("المحادثة");

        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(messages);
        page.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        messageInput = new EditText(this);
        messageInput.setHint("اكتب رسالتك...");
        bar.addView(messageInput, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        bar.addView(btn("إرسال", this::sendMessage));
        page.addView(bar);
        page.addView(btn("رجوع", this::homeScreen));

        if (messageListener != null) {
            messageListener.remove();
        }

        messageListener = db.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener((snap, error) -> {
                if (error != null) {
                    toast("تعذر تحميل الرسائل: " + error.getMessage());
                    return;
                }
                if (snap == null) return;

                messages.removeAllViews();
                for (DocumentSnapshot d : snap.getDocuments()) {
                    String text = d.getString("text");
                    String sender = d.getString("senderId");
                    TextView bubble = label(
                        (uid.equals(sender) ? "أنا: " : "المستخدم: ")
                        + (text == null ? "" : text), 16);
                    messages.addView(bubble);
                }
            });
    }

    void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;

        Map<String, Object> msg = new HashMap<>();
        msg.put("text", text);
        msg.put("senderId", uid);
        msg.put("createdAt", FieldValue.serverTimestamp());

        db.collection("chats").document(chatId)
            .collection("messages").add(msg)
            .addOnSuccessListener(ref -> {
                messageInput.setText("");
                db.collection("chats").document(chatId)
                    .update("lastMessage", text,
                            "lastMessageAt", FieldValue.serverTimestamp());
            })
            .addOnFailureListener(err -> toast(
                "فشل الإرسال: " + err.getMessage()));
    }

    @Override
    protected void onDestroy() {
        if (messageListener != null) messageListener.remove();
        super.onDestroy();
    }
}
