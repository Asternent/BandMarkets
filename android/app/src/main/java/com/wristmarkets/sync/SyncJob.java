package com.wristmarkets.sync;
import android.app.job.*;
import android.os.*;
public class SyncJob extends JobService {
  private final Handler handler=new Handler(Looper.getMainLooper());
  private Runnable finish;
  @Override public boolean onStartJob(JobParameters p){SyncController.get(this).refresh();finish=()->jobFinished(p,false);handler.postDelayed(finish,240000);return true;}
  @Override public boolean onStopJob(JobParameters p){if(finish!=null)handler.removeCallbacks(finish);return true;}
  @Override public void onDestroy(){if(finish!=null)handler.removeCallbacks(finish);super.onDestroy();}
}
