import com.wristmarkets.sync.MarketRepository;
import org.json.*;
import java.nio.file.*;
public class MarketParserTest {
  static int count=0;
  static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);count++;}
  public static void main(String[] args)throws Exception {
    JSONArray inputs=new JSONArray(Files.readString(Path.of(args[0])));JSONArray outputs=new JSONArray();
    for(int i=0;i<inputs.length();i++){
      JSONObject input=inputs.getJSONObject(i);if(!input.optBoolean("ok"))continue;
      String[] market={input.getString("name"),input.getString("symbol"),input.getString("region")};
      JSONObject raw=input.getJSONObject("result"),quote=MarketRepository.parse(market,raw,System.currentTimeMillis()/1000);
      check(quote.getDouble("value")>0,"price must be positive");check(Math.abs(quote.getDouble("percent")-quote.getDouble("change")/quote.getDouble("previous")*100)<0.02,"percentage uses previous close");
      check(quote.getJSONArray("trend").length()<=20,"bounded watch payload");check(quote.toString().getBytes("UTF-8").length<1800,"quote packet bounded");outputs.put(quote);
    }
    JSONObject result=new JSONObject(inputs.getJSONObject(0).getJSONObject("result").toString());String[] market={inputs.getJSONObject(0).getString("name"),inputs.getJSONObject(0).getString("symbol"),inputs.getJSONObject(0).getString("region")};
    result.getJSONObject("meta").put("previousClose",0);boolean rejected=false;try{MarketRepository.parse(market,result,System.currentTimeMillis()/1000);}catch(Exception e){rejected=true;}check(rejected,"zero previous close must fail");
    JSONObject raw=new JSONObject(inputs.getJSONObject(0).getJSONObject("result").toString());raw.getJSONObject("meta").put("regularMarketTime",System.currentTimeMillis()/1000+4000);rejected=false;try{MarketRepository.parse(market,raw,System.currentTimeMillis()/1000);}catch(Exception e){rejected=true;}check(rejected,"future quote must fail");
    JSONObject ui=new JSONObject().put("quotes",outputs).put("selected",new JSONArray()).put("redUp",true).put("fetchStatus",outputs.length()+" 個市場更新成功").put("bridgeStatus","實機連線待驗證").put("lastFetch",System.currentTimeMillis()).put("lastAck",0).put("devices",new JSONArray()).put("interval",0);
    for(int i=0;i<outputs.length();i++)ui.getJSONArray("selected").put(outputs.getJSONObject(i).getString("symbol"));
    Files.writeString(Path.of(args[1]),ui.toString());System.out.println(count+" market data checks passed");
  }
}
