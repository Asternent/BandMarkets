package com.wristmarkets.sync;

import android.content.*;
import android.os.*;
import android.app.job.*;
import com.xiaomi.xms.wearable.Wearable;
import com.xiaomi.xms.wearable.node.*;
import com.xiaomi.xms.wearable.message.*;
import com.xiaomi.xms.wearable.auth.Permission;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class SyncController {
  private static SyncController instance;
  public static synchronized SyncController get(Context c){if(instance==null)instance=new SyncController(c.getApplicationContext());return instance;}
  final Context context;
  final MarketRepository repo;
  final Handler main=new Handler(Looper.getMainLooper());
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private final NodeApi nodes;
  private final MessageApi messages;
  private String nodeId="", listenerNode="", revision="";
  private JSONArray deviceList=new JSONArray();
  private boolean fetching=false, connecting=false, sending=false;
  private int connectGeneration=0, transferGeneration=0, sendAttempt=0;
  private long lastRequest=0;
  private String fetchStatus="尚未更新", bridgeStatus="尚未連接手環";
  private List<byte[]> packets;
  private int packetIndex;
  private SyncController(Context c) {
    context=c;repo=new MarketRepository(c);nodes=Wearable.getNodeApi(c);messages=Wearable.getMessageApi(c);
    if(repo.prefs.getLong("lastFetch",0)>0)fetchStatus="上次更新："+repo.prefs.getInt("successCount",0)+" / "+repo.instruments().size()+" 個市場成功";
  }
  public void refresh() { main.post(()->{
    if(fetching)return;
    fetching=true;fetchStatus="正在取得全球行情…";
    worker.execute(()->{try{int n=repo.refresh();main.post(()->{fetching=false;fetchStatus=n+" / "+repo.instruments().size()+" 個市場更新成功";connectAndSync();});}
      catch(Exception e){main.post(()->{fetching=false;fetchStatus="更新未完成，保留上次行情";});}});
  });}
  public void connectAndSync() {main.post(()->{
    if(connecting||sending)return;
    connecting=true;bridgeStatus="正在尋找已配對手環…";final int generation=++connectGeneration;
    main.postDelayed(()->{if(connecting&&generation==connectGeneration){connecting=false;connectGeneration++;bridgeStatus="連接逾時；請開啟小米運動健康並確認手環已連線";}},15000);
    try {nodes.getConnectedNodes().addOnSuccessListener(list->main.post(()->{
      if(generation!=connectGeneration)return;
      connecting=false;deviceList=new JSONArray();for(Node n:list){try{deviceList.put(new JSONObject().put("id",n.id).put("name",n.name));}catch(Exception ignored){}}
      if(list.isEmpty()){bridgeStatus="找不到已連線手環；請先在小米運動健康連線";return;}
      String preferred=repo.prefs.getString("device","");Node chosen=null;
      for(Node n:list)if(n.id.equals(preferred))chosen=n;
      if(chosen==null&&list.size()==1)chosen=list.get(0);
      if(chosen==null){bridgeStatus="請選擇要同步的手環";return;}
      final Node device=chosen;nodeId=device.id;
      bridgeStatus="已找到 "+device.name+"；正在建立通道";
      registerAndSend(generation);
    })).addOnFailureListener(e->main.post(()->{if(generation!=connectGeneration)return;connecting=false;bridgeStatus=reason(e);}));}
    catch(Exception e){connecting=false;bridgeStatus=reason(e);}
  });}
  private void registerAndSend(int generation){
    Wearable.getAuthApi(context).checkPermission(nodeId,Permission.DEVICE_MANAGER)
      .addOnSuccessListener(allowed->main.post(()->{if(generation!=connectGeneration)return;if(allowed)openChannel(generation);else bridgeStatus="需要手環通訊授權；請點下方「授權手環通訊」";}))
      .addOnFailureListener(e->main.post(()->{if(generation==connectGeneration)bridgeStatus="請先點「授權手環通訊」完成小米授權";}));
  }
  public void authorize(android.app.Activity activity){main.post(()->{
    if(nodeId.isEmpty()){connectAndSync();bridgeStatus="請先連線取得手環，再點授權手環通訊";return;}
    bridgeStatus="正在向小米申請手環通訊授權…";
    Wearable.getAuthApi(activity).requestPermission(nodeId,Permission.DEVICE_MANAGER)
      .addOnSuccessListener(granted->main.post(()->{
        // Permission is parcelled by the SDK. Re-check the actual grant; object identity and an empty newly-granted list are not authoritative.
        Wearable.getAuthApi(context).checkPermission(nodeId,Permission.DEVICE_MANAGER)
          .addOnSuccessListener(allowed->main.post(()->{if(allowed){bridgeStatus="通訊授權完成";openChannel(++connectGeneration);}else bridgeStatus="小米尚未授予手環通訊權限";}))
          .addOnFailureListener(e->main.post(()->bridgeStatus=reason(e)));
      }))
      .addOnFailureListener(e->main.post(()->bridgeStatus=reason(e)));
  });}
  private void openChannel(int generation){
    if(nodeId.equals(listenerNode)){sendSnapshot();return;}
    final String target=nodeId;
    messages.addListener(target,(id,bytes)->main.post(()->receive(id,bytes)))
      .addOnSuccessListener(v->main.post(()->{if(generation!=connectGeneration)return;listenerNode=target;sendSnapshot();}))
      .addOnFailureListener(e->main.post(()->{if(generation==connectGeneration)bridgeStatus=reason(e);}));
    main.postDelayed(()->{if(generation==connectGeneration&&!target.equals(listenerNode))bridgeStatus="通道建立逾時；此版小米運動健康可能未開放第三方互通";},12000);
  }
  private String reason(Exception e){
    android.util.Log.w("BandMarkets", "Xiaomi interconnect: "+e.getClass().getSimpleName()+" "+e.getMessage());
    String name=e.getClass().getSimpleName();
    if(name.contains("Signature"))return "小米拒絕簽章驗證；請確認安裝的是同一組 APK 與 RPK";
    if(name.contains("Permission"))return "小米拒絕存取；目前小米運動健康可能需要開發者接入授權";
    if(name.contains("NotInstalled"))return "請先安裝中國版小米運動健康及手環端腕上股市";
    return "手環連線失敗（"+name+"）；請確認小米運動健康與手環端都已開啟";
  }
  private void sendSnapshot(){
    if(sending)return;JSONArray quotes=repo.forBand();
    if(quotes.length()==0){bridgeStatus="通道已建立；請先更新行情";return;}
    boolean hasData=false;for(int i=0;i<quotes.length();i++)if(quotes.optJSONObject(i).optLong("time")>0)hasData=true;
    if(!hasData){bridgeStatus="尚無可同步的行情；請檢查手機網路";return;}
    try {
      long seq=Math.max(System.currentTimeMillis(),repo.prefs.getLong("sequence",0)+1);repo.prefs.edit().putLong("sequence",seq).apply();revision=String.valueOf(seq);
      JSONObject begin=new JSONObject().put("type","begin").put("v",2).put("id",revision).put("count",quotes.length()).put("generatedAt",seq).put("redUp",repo.prefs.getBoolean("redUp",true));
      packets=new ArrayList<>();packets.add(encode(begin));
      for(int i=0;i<quotes.length();i++)packets.add(encode(new JSONObject().put("type","quote").put("v",2).put("id",revision).put("index",i).put("quote",quotes.getJSONObject(i))));
      packets.add(encode(new JSONObject().put("type","commit").put("v",2).put("id",revision)));
      sending=true;sendAttempt=0;startPackets();
    }catch(Exception e){sending=false;bridgeStatus="無法準備同步資料";}
  }
  private byte[] encode(JSONObject j){return j.toString().getBytes(StandardCharsets.UTF_8);}
  private void startPackets(){
    packetIndex=0;bridgeStatus="正在傳送到手環…";final int generation=++transferGeneration;
    main.postDelayed(()->{if(sending&&generation==transferGeneration){if(sendAttempt++<1){startPackets();}else{sending=false;transferGeneration++;bridgeStatus="未收到手環確認；請打開手環端腕上股市後重試";}}},35000);
    nextPacket(generation);
  }
  private void nextPacket(int generation){
    if(!sending||generation!=transferGeneration)return;
    if(packetIndex>=packets.size()){bridgeStatus="資料已傳送；等待手環儲存確認…";return;}
    try {messages.sendMessage(nodeId,packets.get(packetIndex)).addOnSuccessListener(v->main.post(()->{
      if(generation!=transferGeneration||!sending)return;packetIndex++;main.postDelayed(()->nextPacket(generation),80);
    })).addOnFailureListener(e->main.post(()->{if(generation!=transferGeneration)return;sending=false;transferGeneration++;bridgeStatus=reason(e);}));}
    catch(Exception e){sending=false;transferGeneration++;bridgeStatus=reason(e);}
  }
  private void receive(String id,byte[] bytes){
    if(!id.equals(nodeId)||bytes.length>4096)return;
    try {JSONObject data=new JSONObject(new String(bytes,StandardCharsets.UTF_8));if(data.optInt("v")!=2)return;
      String type=data.optString("type");
      if(type.equals("ack")&&sending&&revision.equals(data.optString("id"))&&data.optInt("count")==packets.size()-2){
        sending=false;transferGeneration++;repo.prefs.edit().putLong("lastAck",System.currentTimeMillis()).apply();bridgeStatus="手環已確認收到並儲存行情";
      }else if(type.equals("refresh")||type.equals("hello")){
        long now=System.currentTimeMillis();if(now-lastRequest<10000)return;lastRequest=now;
        if(now-repo.prefs.getLong("lastFetch",0)>60000)refresh();else sendSnapshot();
      }else if(type.equals("nack")&&revision.equals(data.optString("id"))){sending=false;transferGeneration++;bridgeStatus="手環未接受資料；請重試同步";}
    }catch(Exception ignored){}
  }
  public void action(String action,String value){main.post(()->{
    switch(action){
      case "refresh":refresh();break;
      case "removeCustom":try{repo.removeCustom(value);connectAndSync();}catch(Exception e){bridgeStatus=e.getMessage();}break;
      case "sync":connectAndSync();break;
      case "select":{String[] p=value.split("\\|",2);if(p.length==2)repo.select(p[0],p[1].equals("1"));break;}
      case "redUp":repo.prefs.edit().putBoolean("redUp",value.equals("1")).apply();break;
      case "device":if(!value.equals(nodeId)&&!listenerNode.isEmpty()){messages.removeListener(listenerNode);listenerNode="";}repo.prefs.edit().putString("device",value).apply();connectAndSync();break;
      case "interval":{int minutes;try{minutes=Integer.parseInt(value);}catch(Exception e){return;}if(minutes!=0&&minutes!=15&&minutes!=30&&minutes!=60)return;repo.prefs.edit().putInt("interval",minutes).apply();schedule(context,minutes);break;}
    }
  });}
  public JSONObject state(){JSONObject out=new JSONObject();try{
    JSONArray quotes=repo.cached();if(quotes.length()==0)for(String[] m:repo.instruments())quotes.put(MarketRepository.empty(m));
    JSONArray selected=new JSONArray();for(String s:repo.selectedSet())selected.put(s);
    out.put("quotes",quotes).put("selected",selected).put("redUp",repo.prefs.getBoolean("redUp",true)).put("interval",repo.prefs.getInt("interval",0));
    out.put("fetching",fetching).put("sending",sending).put("connecting",connecting).put("fetchStatus",fetchStatus).put("bridgeStatus",bridgeStatus);
    out.put("lastFetch",repo.prefs.getLong("lastFetch",0)).put("lastAck",repo.prefs.getLong("lastAck",0)).put("devices",deviceList).put("device",nodeId);
  }catch(Exception ignored){}return out;}
  public static void schedule(Context c,int minutes){JobScheduler js=c.getSystemService(JobScheduler.class);js.cancel(8160);if(minutes==0)return;
    js.schedule(new JobInfo.Builder(8160,new ComponentName(c,SyncJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(minutes*60000L).setPersisted(true).setBackoffCriteria(60000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
  }
}
