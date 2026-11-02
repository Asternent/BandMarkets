import com.wristmarkets.sync.MarketRepository;
import org.json.*;
/** Explicitly opt-in network smoke test; never run by the offline suite. */
public class LiveRepositoryTest {
  public static void main(String[] args)throws Exception{
    RepositoryStoreTest.TestContext context=new RepositoryStoreTest.TestContext();MarketRepository repo=new MarketRepository(context);
    JSONArray found=repo.search("Apple");boolean apple=false;for(int i=0;i<found.length();i++)if(found.getJSONObject(i).getString("symbol").equals("AAPL"))apple=true;if(!apple)throw new AssertionError("Apple search did not include AAPL");
    for(String symbol:new String[]{"AAPL","2330.TW","0700.HK"}){
      JSONObject quote=repo.addCustom(symbol);if(!repo.selected(symbol)||!repo.isCustom(symbol)||quote.getJSONArray("history").length()<2)throw new AssertionError("Custom add/history failed: "+symbol);
      System.out.println("Live search/add/sync selection OK: "+symbol);
    }
    repo.addCustom("AAPL");if(repo.customs().length()!=3||repo.forBand().length()!=3)throw new AssertionError("Duplicate or sync payload mismatch");repo.removeCustom("AAPL");if(repo.customs().length()!=2||repo.forBand().length()!=2)throw new AssertionError("Remove did not update sync payload");
    System.out.println("Live repository flow passed (search, verified addition, five-day history, duplicate handling, selection, removal).");
  }
}
