import com.wristmarkets.sync.MarketRepository;
import android.content.*;
import org.json.*;
import java.util.*;
public class RepositoryStoreTest {
  static int checks=0;
  static void ok(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
  static final class Memory implements SharedPreferences {
    final Map<String,Object> values=new HashMap<>();
    public String getString(String k,String d){return values.containsKey(k)?(String)values.get(k):d;}
    public int getInt(String k,int d){return values.containsKey(k)?(Integer)values.get(k):d;}
    public long getLong(String k,long d){return values.containsKey(k)?(Long)values.get(k):d;}
    public Editor edit(){return new Editor(){final Map<String,Object> next=new HashMap<>();public Editor putString(String k,String v){next.put(k,v);return this;}public Editor putInt(String k,int v){next.put(k,v);return this;}public Editor putLong(String k,long v){next.put(k,v);return this;}public boolean commit(){values.putAll(next);return true;}public void apply(){commit();}};}
  }
  static final class TestContext extends Context {final Memory memory=new Memory();public SharedPreferences getSharedPreferences(String name,int mode){return memory;}}
  public static void main(String[] args)throws Exception{
    TestContext context=new TestContext();MarketRepository repo=new MarketRepository(context);ok(repo.instruments().size()==20,"default indexes preserved");ok(repo.selectedSet().size()==20,"default selection preserved");repo.select("NOTREAL",true);ok(!repo.selectedSet().contains("NOTREAL"),"unknown symbol cannot enter sync selection");
    JSONArray custom=new JSONArray().put(new JSONObject().put("name","Apple").put("symbol","AAPL").put("region","美洲")).put(new JSONObject().put("name","duplicate").put("symbol","^TWII").put("region","亞洲"));context.memory.edit().putString("customSymbols",custom.toString()).commit();ok(repo.instruments().size()==21,"built-in and custom deduplicated");repo.select("AAPL",true);ok(repo.selected("AAPL"),"custom selection persisted");
    context.memory.edit().putString("quotes",new JSONArray().put(MarketRepository.empty(new String[]{"Apple","AAPL","美洲"})).put(MarketRepository.empty(MarketRepository.MARKETS[0])).toString()).commit();ok(repo.forBand().length()==2,"custom and default included in actual sync payload");repo.removeCustom("AAPL");ok(!repo.isCustom("AAPL")&&!repo.selected("AAPL"),"remove updates list and selection");ok(repo.cached().length()==1,"removed custom cache deleted");
    context.memory.edit().putString("customSymbols",new JSONArray().put(new JSONObject().put("name","Apple").put("symbol","AAPL").put("region","美洲")).toString()).putString("selected","AAPL").commit();boolean blocked=false;try{repo.removeCustom("AAPL");}catch(Exception e){blocked=true;}ok(blocked,"last selected instrument cannot be removed");repo.select("^TWII",true);repo.removeCustom("AAPL");ok(repo.selected("^TWII"),"other selections survive removal");
    custom=new JSONArray();for(int i=0;i<20;i++)custom.put(new JSONObject().put("name","Test "+i).put("symbol","DEMO"+i).put("region","美洲"));context.memory.edit().putString("customSymbols",custom.toString()).commit();blocked=false;try{repo.addCustom("EXTRA");}catch(Exception e){blocked=e.getMessage().contains("40");}ok(blocked,"capacity checked before network");
    System.out.println(checks+" repository store checks passed");System.exit(0);
  }
}
