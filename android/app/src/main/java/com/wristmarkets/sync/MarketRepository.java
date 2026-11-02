package com.wristmarkets.sync;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class MarketRepository {
  public static final int MAX_INSTRUMENTS=40;
  public static final String[][] MARKETS={
    {"台灣加權","^TWII","亞洲"},{"標普 500","^GSPC","美洲"},{"那斯達克","^IXIC","美洲"},
    {"道瓊工業","^DJI","美洲"},{"費城半導體","^SOX","美洲"},{"香港恆生","^HSI","亞洲"},
    {"上證綜指","000001.SS","亞洲"},{"深證成指","399001.SZ","亞洲"},{"日經 225","^N225","亞洲"},
    {"韓國 KOSPI","^KS11","亞洲"},{"印度 SENSEX","^BSESN","亞洲"},{"澳洲 ASX 200","^AXJO","亞太"},
    {"新加坡海峽","^STI","亞洲"},{"英國 FTSE 100","^FTSE","歐洲"},{"德國 DAX","^GDAXI","歐洲"},
    {"法國 CAC 40","^FCHI","歐洲"},{"歐洲 STOXX 50","^STOXX50E","歐洲"},
    {"加拿大 TSX","^GSPTSE","美洲"},{"巴西 IBOVESPA","^BVSP","美洲"},{"南非40淨報酬","^JN0U.JO","非洲"}
  };
  public final SharedPreferences prefs;
  private final ExecutorService pool=Executors.newFixedThreadPool(4);
  public MarketRepository(Context c){prefs=c.getSharedPreferences("markets",Context.MODE_PRIVATE);}
  public JSONArray cached(){return array(prefs.getString("quotes","[]"));}
  public JSONArray customs(){return array(prefs.getString("customSymbols","[]"));}
  private static JSONArray array(String s){try{return new JSONArray(s);}catch(Exception e){return new JSONArray();}}
  public List<String[]> instruments(){List<String[]> list=new ArrayList<>();Set<String> seen=new HashSet<>();for(String[] m:MARKETS){list.add(m);seen.add(m[1]);}JSONArray custom=customs();for(int i=0;i<custom.length();i++){JSONObject q=custom.optJSONObject(i);if(q!=null&&validSymbol(q.optString("symbol"))&&seen.add(q.optString("symbol")))list.add(new String[]{q.optString("name"),q.optString("symbol"),q.optString("region","其他")});}return list;}
  public Set<String> selectedSet(){String saved=prefs.getString("selected",null);if(saved!=null)return new HashSet<>(Arrays.asList(saved.split(",")));Set<String> set=new HashSet<>();for(String[] m:MARKETS)set.add(m[1]);return set;}
  public synchronized void select(String symbol,boolean checked){Set<String> set=selectedSet();boolean known=false;for(String[] m:instruments())if(m[1].equals(symbol))known=true;if(!known)return;if(checked)set.add(symbol);else if(set.size()>1)set.remove(symbol);prefs.edit().putString("selected",String.join(",",set)).apply();}
  public boolean selected(String symbol){return selectedSet().contains(symbol);}
  public boolean isCustom(String symbol){JSONArray list=customs();for(int i=0;i<list.length();i++)if(symbol.equals(list.optJSONObject(i).optString("symbol")))return true;return false;}
  public JSONArray forBand(){JSONArray out=new JSONArray(),all=cached();Set<String> set=selectedSet();for(int i=0;i<all.length();i++){JSONObject q=all.optJSONObject(i);if(q!=null&&set.contains(q.optString("symbol")))out.put(q);}return out;}
  public static boolean validSymbol(String s){return s!=null&&s.matches("[A-Za-z0-9^.=\\-]{1,24}");}
  private static String shortName(String s){if(s==null||s.trim().isEmpty())return "未命名";return s.length()>28?s.substring(0,28):s;}

  public JSONArray search(String query)throws Exception{
    query=query.trim();if(query.isEmpty()||query.length()>80)throw new IOException("請輸入 1–80 個字元");
    Exception failure=null;
    for(String host:new String[]{"query1.finance.yahoo.com","query2.finance.yahoo.com"})try{return parseSearch(request("https://"+host+"/v1/finance/search?q="+URLEncoder.encode(query,"UTF-8")+"&quotesCount=15&newsCount=0&enableFuzzyQuery=true"));}catch(Exception e){failure=e;}
    throw new IOException("搜尋暫時無法使用；可以改用完整股票代碼加入，例如 AAPL 或 2330.TW",failure);
  }
  public static JSONArray parseSearch(JSONObject response)throws Exception{
    JSONArray rows=response.optJSONArray("quotes"),out=new JSONArray();Set<String> seen=new HashSet<>();
    if(rows==null)return out;
    for(int i=0;i<rows.length()&&out.length()<15;i++){JSONObject q=rows.optJSONObject(i);if(q==null)continue;String type=q.optString("quoteType"),symbol=q.optString("symbol");if(!Arrays.asList("EQUITY","ETF","INDEX","MUTUALFUND").contains(type)||!validSymbol(symbol)||!seen.add(symbol))continue;
      out.put(new JSONObject().put("symbol",symbol).put("name",q.optString("longname",q.optString("shortname",symbol))).put("exchange",q.optString("exchDisp",q.optString("exchange"))).put("type",type));}
    return out;
  }
  public JSONObject addCustom(String symbol)throws Exception{
    symbol=symbol.trim().toUpperCase(Locale.US);if(!validSymbol(symbol))throw new IOException("代碼格式不正確；例如 AAPL、0700.HK 或 2330.TW");
    synchronized(this){boolean exists=false;List<String[]> list=instruments();for(String[] m:list)if(m[1].equals(symbol))exists=true;if(!exists&&list.size()>=MAX_INSTRUMENTS)throw new IOException("最多關注 40 個項目；請先移除一檔自選股票");}
    JSONObject result=download(symbol),meta=result.getJSONObject("meta");String type=meta.optString("instrumentType");
    if(!Arrays.asList("EQUITY","ETF","INDEX","MUTUALFUND").contains(type))throw new IOException("僅支援股票、ETF、指數與基金");
    String name=shortName(meta.optString("shortName",meta.optString("longName",symbol))),region=region(meta.optString("exchangeTimezoneName"));
    JSONObject quote=parse(new String[]{name,symbol,region},result,System.currentTimeMillis()/1000);
    synchronized(this){
      List<String[]> known=instruments();for(String[] m:known)if(m[1].equals(symbol)){select(symbol,true);return quote;}
      if(known.size()>=MAX_INSTRUMENTS)throw new IOException("最多關注 40 個項目；請先移除一檔自選股票");
      JSONArray custom=customs();custom.put(new JSONObject().put("name",name).put("symbol",symbol).put("region",region));
      quote.put("custom",true);JSONArray all=cached();all.put(quote);Set<String> set=selectedSet();set.add(symbol);
      if(!prefs.edit().putString("customSymbols",custom.toString()).putString("quotes",all.toString()).putString("selected",String.join(",",set)).commit())throw new IOException("無法儲存自選股票");
    }return quote;
  }
  public synchronized void removeCustom(String symbol)throws Exception{
    if(!isCustom(symbol))return;Set<String> selected=selectedSet();if(selected.contains(symbol)&&selected.size()==1)throw new IOException("請先選取另一個指數或股票再移除");
    JSONArray custom=customs(),next=new JSONArray(),all=cached(),quotes=new JSONArray();for(int i=0;i<custom.length();i++)if(!symbol.equals(custom.optJSONObject(i).optString("symbol")))next.put(custom.optJSONObject(i));for(int i=0;i<all.length();i++)if(!symbol.equals(all.optJSONObject(i).optString("symbol")))quotes.put(all.optJSONObject(i));selected.remove(symbol);
    if(!prefs.edit().putString("customSymbols",next.toString()).putString("quotes",quotes.toString()).putString("selected",String.join(",",selected)).commit())throw new IOException("無法移除自選股票");
  }
  public int refresh()throws Exception{
    JSONArray old=cached();Map<String,JSONObject> previous=new HashMap<>();for(int i=0;i<old.length();i++){JSONObject q=old.optJSONObject(i);previous.put(q.optString("symbol"),q);}
    List<String[]> requested=instruments();List<Future<JSONObject>> tasks=new ArrayList<>();
    for(String[] m:requested)tasks.add(pool.submit(()->{try{return parse(m,download(m[1]),System.currentTimeMillis()/1000);}catch(Exception e){JSONObject q=previous.containsKey(m[1])?new JSONObject(previous.get(m[1]).toString()):empty(m);q.put("error",e instanceof HttpFailure?"來源回應 "+e.getMessage():"行情暫時無法取得");return q;}}));
    Map<String,JSONObject> fetched=new HashMap<>();int successful=0;for(int i=0;i<tasks.size();i++){JSONObject q=tasks.get(i).get(75,TimeUnit.SECONDS);fetched.put(requested.get(i)[1],q);if(q.optString("error").isEmpty())successful++;}
    synchronized(this){
      // Reconcile against the current list so an in-flight refresh cannot resurrect a removed stock or discard a newly added one.
      JSONArray current=cached(),out=new JSONArray();Map<String,JSONObject> newest=new HashMap<>();for(int i=0;i<current.length();i++){JSONObject q=current.optJSONObject(i);newest.put(q.optString("symbol"),q);}
      for(String[] m:instruments()){JSONObject q=fetched.containsKey(m[1])?fetched.get(m[1]):newest.get(m[1]);if(q==null)q=empty(m);q.put("custom",isCustom(m[1]));out.put(q);}
      if(!prefs.edit().putString("quotes",out.toString()).putLong("lastFetch",System.currentTimeMillis()).putInt("successCount",successful).commit())throw new IOException("無法儲存行情");
    }return successful;
  }
  public static JSONObject empty(String[] m)throws JSONException{return new JSONObject().put("name",shortName(m[0])).put("symbol",m[1]).put("region",m[2]).put("time",0).put("trend",new JSONArray()).put("history",new JSONArray()).put("error","尚未取得行情");}
  private static final class HttpFailure extends IOException{HttpFailure(int code){super(String.valueOf(code));}}
  private static JSONObject request(String address)throws Exception{
    HttpURLConnection con=(HttpURLConnection)new URL(address).openConnection();con.setConnectTimeout(9000);con.setReadTimeout(12000);con.setRequestProperty("User-Agent","Mozilla/5.0 BandMarkets/0.3");con.setRequestProperty("Accept","application/json");
    try{int code=con.getResponseCode();if(code!=200)throw new HttpFailure(code);ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int n;try(InputStream in=con.getInputStream()){while((n=in.read(chunk))!=-1){bytes.write(chunk,0,n);if(bytes.size()>1500000)throw new IOException("回應過大");}}return new JSONObject(bytes.toString("UTF-8"));}finally{con.disconnect();}
  }
  private static JSONObject download(String symbol)throws Exception{Exception failure=null;for(String host:new String[]{"query1.finance.yahoo.com","query2.finance.yahoo.com"})try{return request("https://"+host+"/v8/finance/chart/"+URLEncoder.encode(symbol,"UTF-8")+"?range=5d&interval=1d").getJSONObject("chart").getJSONArray("result").getJSONObject(0);}catch(Exception e){failure=e;}throw failure;}
  private static String region(String zone){if(zone.startsWith("America/"))return "美洲";if(zone.startsWith("Europe/"))return "歐洲";if(zone.startsWith("Africa/"))return "非洲";if(zone.startsWith("Australia/")||zone.startsWith("Pacific/"))return "亞太";if(zone.startsWith("Asia/"))return "亞洲";return "其他";}
  private static String date(long seconds,TimeZone zone){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setTimeZone(zone);return f.format(new Date(seconds*1000));}
  private static boolean positive(double v){return Double.isFinite(v)&&v>0;}
  private static void optional(JSONObject out,String key,double value)throws JSONException{if(Double.isFinite(value)&&value>=0)out.put(key,round(value));}
  private static void cell(JSONObject row,JSONObject bars,String from,String to,int i)throws JSONException{JSONArray a=bars.optJSONArray(from);if(a!=null){double v=a.optDouble(i,Double.NaN);if(Double.isFinite(v)&&v>=0)row.put(to,round(v));}}
  public static JSONObject parse(String[] market,JSONObject result,long now)throws Exception{
    JSONObject m=result.getJSONObject("meta");if(!market[1].equals(m.getString("symbol")))throw new IOException("報價代碼不符");
    double price=m.getDouble("regularMarketPrice");long time=m.getLong("regularMarketTime");if(!positive(price)||time<=0||time>now+300)throw new IOException("無效報價");
    String zoneName=m.optString("exchangeTimezoneName","UTC");TimeZone zone=TimeZone.getTimeZone(zoneName);String quoteDate=date(time,zone);
    JSONObject bars=result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0);JSONArray closes=bars.optJSONArray("close"),timestamps=result.optJSONArray("timestamp"),history=new JSONArray();
    boolean daily="1d".equals(m.optString("dataGranularity"));double derivedPrevious=Double.NaN;
    JSONObject period=m.optJSONObject("currentTradingPeriod"),regular=period==null?null:period.optJSONObject("regular");boolean active=regular!=null&&now>=regular.optLong("start")&&now<regular.optLong("end");
    TreeMap<String,JSONObject> days=new TreeMap<>();
    if(daily&&closes!=null&&timestamps!=null)for(int i=0;i<Math.min(closes.length(),timestamps.length());i++){
      double close=closes.optDouble(i,Double.NaN);long t=timestamps.optLong(i,0);if(!positive(close)||t<=0||t>now+300)continue;String day=date(t,zone);if(day.compareTo(quoteDate)>0)continue;
      JSONObject row=new JSONObject().put("date",day).put("time",t).put("close",round(close)).put("partial",active&&day.equals(quoteDate));cell(row,bars,"open","open",i);cell(row,bars,"high","high",i);cell(row,bars,"low","low",i);cell(row,bars,"volume","volume",i);days.put(day,row);
    }
    for(Map.Entry<String,JSONObject> entry:days.entrySet())if(entry.getKey().compareTo(quoteDate)<0)derivedPrevious=entry.getValue().getDouble("close");
    double previous;
    if(m.has("previousClose")&&!m.isNull("previousClose"))previous=m.getDouble("previousClose");else if(daily)previous=derivedPrevious;else previous=m.optDouble("chartPreviousClose",Double.NaN);
    if(!positive(previous)&&days.size()==1)previous=m.optDouble("chartPreviousClose",Double.NaN);
    if(!positive(previous))throw new IOException("無法確認前一交易日收盤價");
    List<JSONObject> rows=new ArrayList<>(days.values());for(int i=Math.max(0,rows.size()-5);i<rows.size();i++)history.put(rows.get(i));
    JSONObject q=empty(market);q.put("value",round(price)).put("previous",round(previous)).put("change",round(price-previous)).put("percent",round((price-previous)/previous*100)).put("time",time).put("fetchedAt",now).put("zone",zoneName).put("currency",m.optString("currency","")).put("exchange",m.optString("fullExchangeName",m.optString("exchangeName",""))).put("type",m.optString("instrumentType","INDEX"));
    int digits=Math.max(2,Math.min(6,m.optInt("priceHint",2)));if(price<1)digits=Math.max(4,digits);q.put("priceDigits",digits);
    optional(q,"high",m.optDouble("regularMarketDayHigh",Double.NaN));optional(q,"low",m.optDouble("regularMarketDayLow",Double.NaN));optional(q,"week52High",m.optDouble("fiftyTwoWeekHigh",Double.NaN));optional(q,"week52Low",m.optDouble("fiftyTwoWeekLow",Double.NaN));
    if(!rows.isEmpty()){JSONObject latest=rows.get(rows.size()-1);if(latest.getString("date").equals(quoteDate))optional(q,"open",latest.optDouble("open",Double.NaN));}
    double volume=m.optDouble("regularMarketVolume",Double.NaN);if(volume>0||("EQUITY".equals(q.optString("type"))&&volume==0))optional(q,"volume",volume);
    q.put("session",active?(now-time>1800?"報價更新落後":"交易時段"):"休市／盤外").put("error","").put("history",history);
    JSONArray trend=new JSONArray();if(history.length()>0){double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;for(int i=0;i<history.length();i++){double v=history.getJSONObject(i).getDouble("close");min=Math.min(min,v);max=Math.max(max,v);}for(int i=0;i<history.length();i++){double v=history.getJSONObject(i).getDouble("close");trend.put(max==min?50:(int)Math.round((v-min)/(max-min)*100));}q.put("periodHigh",round(max)).put("periodLow",round(min));if(history.length()>1){double first=history.getJSONObject(0).getDouble("close"),last=history.getJSONObject(history.length()-1).getDouble("close");q.put("periodPercent",round((last-first)/first*100));}}
    q.put("trend",trend);return q;
  }
  private static double round(double value){return Math.round(value*1000000.0)/1000000.0;}
}
