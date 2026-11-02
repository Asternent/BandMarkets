const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),java=fs.readFileSync(path.join(root,'android/app/src/main/java/com/wristmarkets/sync/MarketRepository.java'),'utf8');
const markets=[...java.matchAll(/\{"([^"]+)","([^"]+)","([^"]+)"\}/g)].map(m=>({name:m[1],symbol:m[2],region:m[3]}));
markets.push({name:'Apple',symbol:'AAPL',region:'美洲'},{name:'台積電',symbol:'2330.TW',region:'亞洲'},{name:'騰訊',symbol:'0700.HK',region:'亞洲'});
const results=[];let next=0;
async function worker(){while(next<markets.length){const m=markets[next++];try{
 let res=await fetch('https://query1.finance.yahoo.com/v8/finance/chart/'+encodeURIComponent(m.symbol)+'?interval=1d&range=5d',{headers:{'User-Agent':'Mozilla/5.0'},signal:AbortSignal.timeout(25000)});
 if(!res.ok)throw Error('HTTP '+res.status);let json=await res.json(),r=json.chart.result[0];if(!r.meta.regularMarketPrice||!r.meta.regularMarketTime)throw Error('missing price/time');
 results.push({...m,ok:true,result:r});console.log('OK '+m.name+' '+r.meta.symbol);
 }catch(e){results.push({...m,ok:false,error:e.message});console.log('FAIL '+m.name+' '+e.message)}}}
(async()=>{await Promise.all(Array.from({length:4},worker));fs.mkdirSync(path.join(root,'test'),{recursive:true});fs.writeFileSync(path.join(root,'test/live-results.json'),JSON.stringify(results,null,2));console.log(results.filter(r=>r.ok).length+'/'+markets.length+' sources available');if(results.some(r=>!r.ok))process.exitCode=1})()
