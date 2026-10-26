أصوات تطبيق مراسل
=================
الملفات:
- send_message.wav: صوت إرسال الرسالة
- receive_message.wav: صوت استقبال رسالة داخل المحادثة
- notification_sound.wav: صوت الإشعار
- chat_sound.wav: صوت دردشة قصير

طريقة الاستخدام:
انسخ ملفات WAV إلى:
app/src/main/res/raw/

ثم استدعها في Android باستخدام:
MediaPlayer.create(this, R.raw.send_message)
MediaPlayer.create(this, R.raw.receive_message)
MediaPlayer.create(this, R.raw.notification_sound)
MediaPlayer.create(this, R.raw.chat_sound)

ملاحظة: أسماء الموارد تُكتب بحروف صغيرة وشرطة سفلية فقط.
