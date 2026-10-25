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

    private final int bg = Color.rgb(9, 20, 18);
    private final int panel = Color.rgb(20, 35, 31);
    private final int card = Color.rgb(31, 48, 42);
    private final int green = Color.rgb(0, 168, 107);
    private final int lightGreen = Color.rgb(37, 211, 102);
    private final int white = Color.WHITE;
    private final int muted = Color.rgb(170, 187, 179);

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

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private GradientDrawable gradientBackground() {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.rgb(8, 35, 27),
                        Color.rgb(9, 20, 18),
                        Color.rgb(13, 28, 24)
                });
        d.setCornerRadius(0);
        return d;
    }

    private TextView label(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(8, 8, 8, 8);
        return t;
    }

    private EditText field(String hint, boolean password) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextColor(white);
        e.setHintTextColor(muted);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setPadding(16, 8, 16, 8);
        e.setBackground(shape(card, 18));
        e.setInputType(password ? 129 : 33);

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, 54);
        p.setMargins(0, 6, 0, 6);
        e.setLayoutParams(p);
        return e;
    }

    private Button button(String title) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextColor(white);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(shape(green, 16));

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, 50);
        p.setMargins(0, 5, 0, 5);
        b.setLayoutParams(p);
        return b;
    }

    private TextView textButton(String title) {
        TextView t = label(title, 13, muted);
        t.setGravity(Gravity.CENTER);
        t.setPadding(4, 12, 4, 12);
        t.setBackground(shape(panel, 12));
        return t;
    }

    private void baseScreen() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(16, 12, 16, 10);
        root.setBackground(gradientBackground());
        setContentView(root);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && root != null) {
                imm.hideSoftInputFromWindow(
                        root.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
        }
    }

    private void addSpace(LinearLayout parent, int height) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(1, height));
    }

    private void addHeader(String title, String subtitle) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(12, 8, 12, 8);
        header.setBackground(shape(panel, 18));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);

        TextView name = label(title, 25, lightGreen);
        TextView sub = label(subtitle, 12, muted);

        titles.addView(name);
        titles.addView(sub);

        header.addView(titles,
                new LinearLayout.LayoutParams(0, -2, 1));

        TextView logo = label("م", 30, white);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(shape(green, 40));
        header.addView(logo,
                new LinearLayout.LayoutParams(48, 48));

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 0, 0, 12);
        root.addView(header, p);
    }

    private void showLogin() {
        removeMessageListener();
        baseScreen();

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        center.setPadding(8, 20, 8, 20);

        TextView logo = label("م", 46, white);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(shape(green, 60));
        center.addView(logo,
                new LinearLayout.LayoutParams(90, 90));

        TextView title = label("مراسل", 34, lightGreen);
        title.setGravity(Gravity.CENTER);
        center.addView(title);

        TextView subtitle = label(
                "تواصل مع من تحب بسهولة",
                15, muted);
        subtitle.setGravity(Gravity.CENTER);
        center.addView(subtitle);

        addSpace(center, 28);

        emailInput = field("البريد الإلكتروني", false);
        center.addView(emailInput);

        passwordInput = field("كلمة المرور — 6 أحرف على الأقل", true);
        center.addView(passwordInput);

        Button register = button("إنشاء حساب جديد");
        center.addView(register);

        Button login = button("تسجيل الدخول");
        login.setBackground(shape(card, 16));
        center.addView(login);

        root.addView(center,
                new LinearLayout.LayoutParams(-1, -1));

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
            passwordInput.setError(
                    "كلمة المرور يجب أن تكون 6 أحرف على الأقل");
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

        addHeader("مراسل", "ابقَ على تواصل مع أصدقائك");

        LinearLayout searchCard = new LinearLayout(this);
        searchCard.setOrientation(LinearLayout.VERTICAL);
        searchCard.setPadding(14, 12, 14, 12);
        searchCard.setBackground(shape(panel, 18));

        searchCard.addView(label(
                "ابدأ محادثة جديدة", 18, white));

        searchCard.addView(label(
                "ابحث بالبريد الإلكتروني للشخص الآخر",
                13, muted));

        otherEmailInput = field(
                "البريد الإلكتروني للشخص", false);
        searchCard.addView(otherEmailInput);

        Button open = button("🔎  بحث وفتح المحادثة");
        searchCard.addView(open);

        root.addView(searchCard);

        addSpace(root, 16);

        root.addView(label("المحادثات", 21, white));

        LinearLayout empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(12, 22, 12, 22);
        empty.setBackground(shape(panel, 18));

        TextView icon = label("💬", 42, lightGreen);
        icon.setGravity(Gravity.CENTER);
        empty.addView(icon);

        TextView emptyTitle = label(
                "رسائلك تبدأ من هنا", 18, white);
        emptyTitle.setGravity(Gravity.CENTER);
        empty.addView(emptyTitle);

        TextView emptyText = label(
                "ابحث عن شخص لديه حساب في مراسل لبدء المحادثة.",
                14, muted);
        emptyText.setGravity(Gravity.CENTER);
        empty.addView(emptyText);

        root.addView(empty,
                new LinearLayout.LayoutParams(-1, 0, 1));

        addBottomNavigation("المحادثات");

        open.setOnClickListener(v -> findPerson());
    }

    private void addBottomNavigation(String selected) {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(4, 6, 4, 6);
        nav.setBackground(shape(panel, 18));

        String[] names = {
                "المحادثات", "الحالات", "المجموعات", "الإعدادات"
        };
        String[] icons = {"💬", "◉", "👥", "⚙"};

        for (int i = 0; i < names.length; i++) {
            final String name = names[i];

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(2, 6, 2, 6);

            TextView icon = label(icons[i], 21,
                    selected.equals(name) ? lightGreen : muted);
            icon.setGravity(Gravity.CENTER);

            TextView text = label(names[i], 11,
                    selected.equals(name) ? lightGreen : muted);
            text.setGravity(Gravity.CENTER);

            item.addView(icon);
            item.addView(text);

            nav.addView(item,
                    new LinearLayout.LayoutParams(0, -2, 1));

            item.setOnClickListener(v -> {
                if (name.equals("المحادثات")) {
                    showContacts();
                } else if (name.equals("الإعدادات")) {
                    showSettings();
                } else {
                    showPlaceholder(name);
                }
            });
        }

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 10, 0, 0);
        root.addView(nav, p);
    }

    private void showPlaceholder(String section) {
        removeMessageListener();
        chatId = null;
        baseScreen();

        addHeader("مراسل", "تواصل بسهولة");

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(16, 24, 16, 24);
        content.setBackground(shape(panel, 20));

        String icon = section.equals("الحالات") ? "◉" : "👥";

        TextView symbol = label(icon, 46, lightGreen);
        symbol.setGravity(Gravity.CENTER);
        content.addView(symbol);

        TextView title = label(section, 24, white);
        title.setGravity(Gravity.CENTER);
        content.addView(title);

        TextView description = label(
                section.equals("الحالات")
                        ? "ستظهر الحالات هنا بعد إضافة ميزة نشر الحالات."
                        : "ستظهر المجموعات هنا بعد إضافة ميزة إنشاء المجموعات.",
                15, muted);
        description.setGravity(Gravity.CENTER);
        content.addView(description);

        root.addView(content,
                new LinearLayout.LayoutParams(-1, 0, 1));

        addBottomNavigation(section);
    }

    private void showSettings() {
        removeMessageListener();
        chatId = null;

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            showLogin();
            return;
        }

        baseScreen();
        addHeader("الإعدادات", "إدارة حسابك في مراسل");

        LinearLayout profile = new LinearLayout(this);
        profile.setOrientation(LinearLayout.VERTICAL);
        profile.setPadding(16, 16, 16, 16);
        profile.setBackground(shape(panel, 18));

        TextView avatar = label("👤", 40, lightGreen);
        avatar.setGravity(Gravity.CENTER);
        profile.addView(avatar);

        TextView email = label(
                user.getEmail() == null ? "" : user.getEmail(),
                15, white);
        email.setGravity(Gravity.CENTER);
        profile.addView(email);

        root.addView(profile);

        addSpace(root, 12);

        root.addView(label("الملف الشخصي", 18, white));
        root.addView(label(
                "الحساب مسجّل بالبريد الإلكتروني.",
                14, muted));

        Button chats = button("العودة إلى المحادثات");
        root.addView(chats);
        chats.setOnClickListener(v -> showContacts());

        Button logout = button("تسجيل الخروج");
        logout.setBackground(shape(card, 16));
        root.addView(logout);

        logout.setOnClickListener(v -> {
            removeMessageListener();
            auth.signOut();
            showLogin();
        });

        View spacer = new View(this);
        root.addView(spacer,
                new LinearLayout.LayoutParams(1, 0, 1));

        addBottomNavigation("الإعدادات");
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
                        toast("فشل البحث: "
                                + e.getLocalizedMessage()));
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
        header.setPadding(8, 6, 8, 6);
        header.setBackground(shape(panel, 18));

        Button back = button("رجوع");
        header.addView(back,
                new LinearLayout.LayoutParams(90, 48));

        TextView title = label("المحادثة", 20, lightGreen);
        title.setGravity(Gravity.CENTER);
        header.addView(title,
                new LinearLayout.LayoutParams(0, 50, 1));

        root.addView(header);
        back.setOnClickListener(v -> showContacts());

        scroll = new ScrollView(this);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(6, 12, 6, 12);
        scroll.addView(messages);

        LinearLayout.LayoutParams scrollParams =
                new LinearLayout.LayoutParams(-1, 0, 1);
        scrollParams.setMargins(0, 10, 0, 10);
        root.addView(scroll, scrollParams);

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(4, 4, 4, 4);

        messageInput = new EditText(this);
        messageInput.setHint("اكتب رسالتك...");
        messageInput.setTextColor(white);
        messageInput.setHintTextColor(muted);
        messageInput.setTextSize(15);
        messageInput.setPadding(14, 8, 14, 8);
        messageInput.setBackground(shape(card, 22));
        messageInput.setMinHeight(50);

        composer.addView(messageInput,
                new LinearLayout.LayoutParams(0, -2, 1));

        Button send = button("إرسال");
        LinearLayout.LayoutParams sendParams =
                new LinearLayout.LayoutParams(90, 50);
        sendParams.setMargins(6, 0, 0, 0);
        composer.addView(send, sendParams);

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

                    if (scroll != null) {
                        scroll.post(() ->
                                scroll.fullScroll(View.FOCUS_DOWN));
                    }
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
        bubble.setPadding(14, 11, 14, 11);
        bubble.setMaxWidth((int) (
                getResources().getDisplayMetrics().widthPixels * 0.75f));

        bubble.setBackground(shape(
                mine ? Color.rgb(0, 112, 75) : card, 18));

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
