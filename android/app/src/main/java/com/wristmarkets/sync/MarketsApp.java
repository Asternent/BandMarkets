package com.wristmarkets.sync;
import android.app.Application;
public class MarketsApp extends Application {
  @Override public void onCreate() { super.onCreate(); SyncController.get(this); }
}
