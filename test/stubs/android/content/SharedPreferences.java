package android.content;
/** Minimal JVM-only API surface for repository behavior tests. */
public interface SharedPreferences {
  String getString(String key,String fallback);
  int getInt(String key,int fallback);
  long getLong(String key,long fallback);
  Editor edit();
  interface Editor {
    Editor putString(String key,String value);
    Editor putInt(String key,int value);
    Editor putLong(String key,long value);
    boolean commit();
    void apply();
  }
}
