# BAS Storage Manager

تطبيق Android شخصي لإدارة مساحة الهاتف بسرعة، مع التركيز على الملفات الكبيرة، وسائط WhatsApp، Downloads، الصور والفيديو، والملفات المتكررة.

## النسخة الحالية 0.1
- قراءة المساحة الكلية/المستخدمة/المتاحة.
- فحص التخزين في Background Thread.
- دعم All Files Access للاستخدام الشخصي خارج Google Play.
- تصنيف الملفات: WhatsApp / Downloads / Images / Videos / Audio / Documents / Other.
- عرض أكبر الملفات.
- عرض أول 100 عنصر فقط في كل شاشة لتجنب بطء واجهة آلاف الملفات.
- اكتشاف الملفات المتكررة باستخدام SHA-256، مع حساب البصمة فقط لمجموعات الملفات المتساوية في الحجم.
- اختيار متعدد وحذف بعد شاشة مراجعة توضح العدد والمساحة المتوقعة.
- للوسائط على Android 11+ يستخدم MediaStore Delete Request حتى يبقى قرار الحذف بيد المستخدم.

## بناء APK من GitHub
1. افتح تبويب **Actions**.
2. Workflow باسم **Build Android APK** يعمل تلقائيًا عند push إلى main.
3. من نتيجة الـWorkflow نزّل Artifact باسم `BAS-Storage-Manager-debug-apk`.

## المرحلة التالية المخططة
- Pagination حقيقي 100 عنصر لكل صفحة.
- Cleanup Goal: مثال "أريد توفير 20 GB".
- Screenshots القديمة والفيديوهات القديمة كاقتراحات جاهزة.
- Cache للفحص لتسريع الفتح اللاحق.
- نقل/أرشفة إلى Google Drive.
- Gmail Manager كمرحلة منفصلة عبر Gmail API.

## ملاحظة Android
صلاحية MANAGE_EXTERNAL_STORAGE واسعة، ولذلك هذه النسخة مصممة كتطبيق شخصي sideload. نشرها على Google Play يحتاج مراجعة متطلبات All Files Access وقد يتطلب تصميمًا مختلفًا للصلاحيات.
