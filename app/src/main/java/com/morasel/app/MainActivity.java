
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
import com.google.firebase.firestore.*;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private LinearLayout root, content;
    private String uid;

    private int green = Color.rgb(7, 75, 53);
    private int gold = Color.rgb(235, 190, 75);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() == null) {
            showLogin();
            return;
        }

        uid = auth.getCurrentUser().getUid();
        updateOnline(true);
        showHome();
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(12, 14, 12, 14);
        return t;
    }

    private Button button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setBackground(shape(green, 24));
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void base(String title) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 12, 16, 12);
        root.setBackgroundColor(Color.rgb(245, 247, 244));

        TextView heading = text(title, 25, gold);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setBackground(shape(green, 18));
        root.addView(heading);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 12, 0, 12);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                -1, 0, 1));

        setContentView(root);
    }

    private void showLogin() {
        base("مراسل | تسجيل الدخول");

        EditText name = new EditText(this);
        name.setHint("اسم المستخدم");
        content.addView(name);

        EditText email = new EditText(this);
        email.setHint("البريد الإلكتروني");
        email.setInputType(33);
        content.addView(email);

        EditText password = new EditText(this);
        password.setHint("كلمة المرور");
        password.setInputType(129);
        content.addView(password);

        content.addView(button("إنشاء حساب", () -> {
            String n = name.getText().toString().trim();
            String e = email.getText().toString().trim();
            String p = password.getText().toString();

            if (n.isEmpty() || e.isEmpty() || p.length() < 6) {
                msg("أدخل الاسم والبريد وكلمة مرور من 6 أحرف على الأقل");
                return;
            }

            auth.createUserWithEmailAndPassword(e, p)
                .addOnSuccessListener(result -> {
                    uid = auth.getCurrentUser().getUid();

                    Map<String, Object> user = new HashMap<>();
                    user.put("uid", uid);
                    user.put("username", n);
                    user.put("email", e);
                    user.put("photoUrl", "");
                    user.put("online", true);
                    user.put("lastSeen", FieldValue.serverTimestamp());

                    db.collection("users").document(uid).set(user)
                        .addOnSuccessListener(v -> showHome())
                        .addOnFailureListener(err -> msg(err.getMessage()));
                })
                .addOnFailureListener(err -> msg(err.getMessage()));
        }));

        content.addView(button("تسجيل الدخول", () -> {
            String e = email.getText().toString().trim();
            String p = password.getText().toString();

            auth.signInWithEmailAndPassword(e, p)
                .addOnSuccessListener(result -> {
                    uid = auth.getCurrentUser().getUid();
                    updateOnline(true);
                    showHome();
                })
                .addOnFailureListener(err -> msg(err.getMessage()));
        }));
    }

    private void showHome() {
        base("مراسل 💬");

        content.addView(text("أهلًا بك في مراسل", 21, green));

        content.addView(button("💬 المحادثات السابقة", this::showChats));
        content.addView(button("👤 الملف الشخصي", this::showProfile));
        content.addView(button("👥 المجموعات", this::showGroups));
        content.addView(button("📞 المكالمات", this::showCalls));

        content.addView(button("تسجيل الخروج", () -> {
            updateOnline(false);
            auth.signOut();
            showLogin();
        }));
    }

    private void showProfile() {
        base("الملف الشخصي");

        db.collection("users").document(uid).get()
            .addOnSuccessListener(doc -> {
                EditText name = new EditText(this);
                name.setHint("اسم المستخدم");
                name.setText(doc.getString("username"));
                content.addView(name);

                content.addView(text(
                    "البريد: " + auth.getCurrentUser().getEmail(),
                    16, green));

                content.addView(text(
                    "الصورة الشخصية: تحتاج إعداد رفع الصور",
                    14, green));

                content.addView(button("حفظ الاسم", () -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) {
                        msg("اكتب اسم المستخدم");
                        return;
                    }
                    db.collection("users").document(uid)
                        .update("username", n)
                        .addOnSuccessListener(v -> msg("تم حفظ الاسم"))
                        .addOnFailureListener(err -> msg(err.getMessage()));
                }));
            });

        content.addView(button("رجوع", this::showHome));
    }

    private void showChats() {
        base("المحادثات");

        db.collection("chats")
            .whereArrayContains("participants", uid)
            .addSnapshotListener((snap, error) -> {
                if (error != null) {
                    msg(error.getMessage());
                    return;
                }
                content.removeAllViews();

                if (snap == null || snap.isEmpty()) {
                    content.addView(text(
                        "لا توجد محادثات حتى الآن",
                        17, green));
                    return;
                }

                for (DocumentSnapshot d : snap.getDocuments()) {
                    String last = d.getString("lastMessage");
                    content.addView(text(
                        "محادثة\n" + (last == null ? "ابدأ الحديث" : last),
                        17, green));
                }
            });

        content.addView(button("رجوع", this::showHome));
    }

    private void showGroups() {
        base("المجموعات");

        EditText groupName = new EditText(this);
        groupName.setHint("اسم المجموعة");
        content.addView(groupName);

        content.addView(button("إنشاء مجموعة", () -> {
            String name = groupName.getText().toString().trim();
            if (name.isEmpty()) {
                msg("اكتب اسم المجموعة");
                return;
            }

            Map<String, Object> group = new HashMap<>();
            group.put("name", name);
            group.put("createdBy", uid);
            group.put("members", java.util.Arrays.asList(uid));
            group.put("createdAt", FieldValue.serverTimestamp());

            db.collection("groups").add(group)
                .addOnSuccessListener(ref -> msg("تم إنشاء المجموعة"))
                .addOnFailureListener(err -> msg(err.getMessage()));
        }));

        db.collection("groups")
            .whereArrayContains("members", uid)
            .addSnapshotListener((snap, error) -> {
                if (error != null) return;
                if (snap == null) return;

                for (DocumentSnapshot d : snap.getDocuments()) {
                    String n = d.getString("name");
                    content.addView(text(
                        "👥 " + (n == null ? "مجموعة" : n),
                        17, green));
                }
            });

        content.addView(button("رجوع", this::showHome));
    }

    private void showCalls() {
        base("المكالمات");

        content.addView(text(
            "المكالمات الصوتية والمرئية تحتاج إعداد خدمة اتصال وربطها بالمستخدمين.",
            17, green));

        content.addView(button("مكالمة صوتية", () ->
            msg("يلزم إعداد خدمة الاتصال أولًا")));

        content.addView(button("مكالمة مرئية", () ->
            msg("يلزم إعداد خدمة الاتصال أولًا")));

        content.addView(button("رجوع", this::showHome));
    }

    private void updateOnline(boolean online) {
        if (uid == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("online", online);
        data.put("lastSeen", FieldValue.serverTimestamp());

        db.collection("users").document(uid).set(
            data, SetOptions.merge());
    }

    private void msg(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        updateOnline(false);
        super.onDestroy();
    }
}
