-injars war/WEB-INF/classes
-injars war/WEB-INF/lib/xmlenc-0.52.jar(!META-INF/MANIFEST.MF)
-injars war/WEB-INF/lib/eve-core-0.6.jar(!META-INF/MANIFEST.MF)
-libraryjars war/WEB-INF/lib/
-libraryjars war/WEB-INF/lib/javassist.jar
-libraryjars war/WEB-INF/lib/msgpack-0.6.7-SNAPSHOT.jar
-libraryjars war/WEB-INF/lib/memo.jar
-libraryjars war/WEB-INF/lib/uuid-3.3.jar
-libraryjars <java.home>/lib/rt.jar
-libraryjars /usr/share/java/tomcat-servlet-api-3.0.jar
-libraryjars war/WEB-INF/lib/appengine-api-1.0-sdk-1.7.0.jar
-libraryjars war/WEB-INF/lib/appengine-api-labs.jar
-outjars war/WEB-INF/lib/dialogHandler.jar


-keepattributes Exceptions,InnerClasses,Signature,Deprecated,
                SourceFile,LineNumberTable,*Annotation*,EnclosingMethod
-keepdirectories
-verbose
-target 1.6
-dontnote

-keep public class * implements javax.servlet.Servlet
-keep public class * implements javax.servlet.http.HTTPServlet
-keep public class com.almende.dialog.** {
    public protected *;
}
-keepclassmembernames class * {
    java.lang.Class class$(java.lang.String);
    java.lang.Class class$(java.lang.String, boolean);
}
-keepclasseswithmembernames class * {
    native <methods>;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
