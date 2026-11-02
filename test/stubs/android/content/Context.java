package android.content;
/** Minimal JVM-only API surface. Never included in the Android application. */
public abstract class Context {
  public static final int MODE_PRIVATE=0;
  public abstract SharedPreferences getSharedPreferences(String name,int mode);
}
