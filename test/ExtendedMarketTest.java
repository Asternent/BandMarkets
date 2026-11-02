import com.wristmarkets.sync.MarketRepository;
import org.json.*;
import java.time.*;
public class ExtendedMarketTest {
  static int checks=0;
  static void ok(boolean b,String why){if(!b)throw new AssertionError(why);checks++;}
  static long t(String iso){return Instant.parse(iso).getEpochSecond();}
  static JSONObject fixture()throws Exception{
    JSONArray times=new JSONArray();for(String date:new String[]{"2026-10-27T13:30:00Z","2026-10-28T13:30:00Z","2026-10-29T13:30:00Z","2026-10-30T13:30:00Z","2026-11-02T14:30:00Z"})times.put(t(date));
    JSONObject m=new JSONObject().put("symbol","DEMO").put("regularMarketPrice",106).put("regularMarketTime",t("2026-11-02T16:00:00Z")).put("dataGranularity","1d").put("chartPreviousClose",80).put("exchangeTimezoneName","America/New_York").put("currency","USD").put("instrumentType","EQUITY").put("regularMarketVolume",1234567).put("regularMarketDayHigh",107).put("regularMarketDayLow",104).put("fiftyTwoWeekHigh",120).put("fiftyTwoWeekLow",60);
    m.put("currentTradingPeriod",new JSONObject().put("regular",new JSONObject().put("start",t("2026-11-02T14:30:00Z")).put("end",t("2026-11-02T21:00:00Z"))));
    JSONObject bars=new JSONObject().put("close",new JSONArray("[100,102,101,104,106]")).put("open",new JSONArray("[99,101,102,103,105]")).put("high",new JSONArray("[101,103,103,105,107]")).put("low",new JSONArray("[98,100,100,102,104]")).put("volume",new JSONArray("[1000,2000,3000,4000,5000]"));
    return new JSONObject().put("meta",m).put("timestamp",times).put("indicators",new JSONObject().put("quote",new JSONArray().put(bars)));
  }
  public static void main(String[] args)throws Exception{
    long now=t("2026-11-02T16:05:00Z");String[] market={"測試股票","DEMO","美洲"};JSONObject f=fixture(),q=MarketRepository.parse(market,f,now);JSONArray h=q.getJSONArray("history");
    ok(q.getDouble("previous")==104,"prior trading close, not five-day chart anchor");ok(Math.abs(q.getDouble("percent")-1.923077)<0.00001,"daily return uses prior close");ok(h.length()==5,"five daily samples");ok(h.getJSONObject(4).getString("date").equals("2026-11-02"),"DST keeps exchange date");ok(h.getJSONObject(3).getString("date").equals("2026-10-30"),"weekend is not filled");ok(h.getJSONObject(4).getBoolean("partial"),"current trading day marked partial");ok(!h.getJSONObject(3).getBoolean("partial"),"past close final");ok(q.getDouble("open")==105,"current daily open");ok(q.getDouble("volume")==1234567,"reported volume preserved");ok(q.getDouble("periodHigh")==106&&q.getDouble("periodLow")==100,"chart extrema reflect closes");ok(q.getDouble("periodPercent")==6,"first-to-last chart change");ok(q.getJSONArray("trend").length()==5,"watch chart bounded");ok(q.toString().getBytes("UTF-8").length<4096,"watch message bounded");
    f.getJSONObject("meta").put("previousClose",105);ok(MarketRepository.parse(market,f,now).getDouble("previous")==105,"explicit previous close respected");
    f=fixture();f.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close").put(1,JSONObject.NULL);q=MarketRepository.parse(market,f,now);ok(q.getJSONArray("history").length()==4,"missing session is omitted, never zero filled");
    f=fixture();f.getJSONObject("meta").put("regularMarketTime",now+10000);boolean rejected=false;try{MarketRepository.parse(market,f,now);}catch(Exception e){rejected=true;}ok(rejected,"future quotes rejected");
    JSONObject search=new JSONObject().put("quotes",new JSONArray().put(new JSONObject().put("symbol","AAPL").put("quoteType","EQUITY").put("shortname","Apple")).put(new JSONObject().put("symbol","AAPL").put("quoteType","EQUITY")).put(new JSONObject().put("symbol","BTC-USD").put("quoteType","CRYPTOCURRENCY")).put(new JSONObject().put("symbol","../file").put("quoteType","EQUITY")));
    ok(MarketRepository.parseSearch(search).length()==1,"search rejects unsupported types, invalid symbols and duplicates");ok(MarketRepository.validSymbol("2330.TW")&&MarketRepository.validSymbol("0700.HK")&&MarketRepository.validSymbol("BRK-B"),"international symbols supported");ok(!MarketRepository.validSymbol("https://example.com")&&!MarketRepository.validSymbol("AAPL\n"),"search is not an arbitrary URL fetcher");
    System.out.println(checks+" extended market checks passed");
  }
}
