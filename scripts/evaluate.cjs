// Inicia un servidor aislado y mide únicamente su proceso; nunca detiene servidores del usuario.
const fs=require('node:fs'),net=require('node:net'),path=require('node:path');
const {spawn,spawnSync}=require('node:child_process');
const crypto=require('node:crypto');
process.chdir(path.resolve(__dirname,'..'));
(async()=>{
 const javaProbe=spawnSync(process.env.PRIB_JAVA||'java',['-XshowSettings:properties','-version'],{encoding:'utf8',windowsHide:true});
 if(javaProbe.status!==0)throw Error(javaProbe.stderr||'Java no disponible');
 const home=javaProbe.stderr.match(/java.home\s*=\s*(.+)/)?.[1].trim();if(!home)throw Error('No se identificó java.home');
 const java=path.join(home,'bin',process.platform==='win32'?'java.exe':'java');
 const port=await new Promise(resolve=>{const s=net.createServer();s.listen(0,'127.0.0.1',()=>{const p=s.address().port;s.close(()=>resolve(p));});});
 fs.mkdirSync('build',{recursive:true});
 const server=spawn(java,['-Xmx256m','-cp','build/classes','prib.PribServer',String(port),process.env.PRIB_DATA||'data'],{windowsHide:true});
 const exited=new Promise(resolve=>server.once('exit',resolve));let sampler,samples='',sampleExit;
 try {
  await new Promise((resolve,reject)=>{
   const timeout=setTimeout(()=>reject(Error('Servidor no inició')),15000);
   server.stdout.on('data',d=>{if(d.toString().includes('PRIB:')){clearTimeout(timeout);resolve();}});
   server.stderr.on('data',d=>process.stderr.write(d));server.on('error',reject);
   server.once('exit',code=>{clearTimeout(timeout);reject(Error('Servidor terminó '+code));});
  });
  if(process.platform==='win32') {
   const command=`while ($true) { $p = Get-Process -Id ${server.pid} -ErrorAction SilentlyContinue; if (!$p) { break }; [pscustomobject]@{time=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds();workingSetBytes=$p.WorkingSet64;privateBytes=$p.PrivateMemorySize64;cpuSeconds=$p.TotalProcessorTime.TotalSeconds} | ConvertTo-Json -Compress; Start-Sleep -Milliseconds 500 }`;
   sampler=spawn(process.env.PRIB_POWERSHELL||'powershell.exe',['-NoProfile','-Command',command],{windowsHide:true});
   sampleExit=new Promise(resolve=>sampler.once('exit',resolve));sampler.stdout.on('data',d=>samples+=d);sampler.stderr.on('data',d=>process.stderr.write(d));
  }
  await new Promise((resolve,reject)=>{
   const test=spawn(process.execPath,['scripts/browser/evaluation.cjs'],{windowsHide:true,stdio:'inherit',env:{...process.env,PRIB_URL:'http://localhost:'+port}});
   test.on('error',reject);test.once('exit',code=>code===0?resolve():reject(Error('Evaluación falló '+code)));
  });
  const report=JSON.parse(fs.readFileSync('build/evaluation-stage7.json','utf8'));
  report.environment.java=javaProbe.stderr.match(/java.version\s*=\s*(.+)/)?.[1].trim();report.environment.serverMaxHeapBytes=256*1024*1024;
  const measurements=samples.trim().split(/\r?\n/).filter(Boolean).map(l=>JSON.parse(l));
  report.resources.serverProcessSamples=measurements;
  report.resources.peakObservedServerWorkingSetBytes=measurements.length?Math.max(...measurements.map(m=>m.workingSetBytes)):null;
  report.resources.peakObservedServerPrivateBytes=measurements.length?Math.max(...measurements.map(m=>m.privateBytes)):null;
  report.resources.serverMemoryNote='Windows Get-Process cada 500 ms; working set residente y private bytes del proceso, no heap. Picos entre muestras pueden perderse. El heap está limitado con -Xmx256m.';
  const sources={};
  for(const dir of ['src/main/java/prib','web','scripts/browser'])for(const file of fs.readdirSync(dir).sort()){
   const name=dir+'/'+file;if(fs.statSync(name).isFile())sources[name]=crypto.createHash('sha256').update(fs.readFileSync(name)).digest('hex');
  }
  for(const name of ['scripts/evaluate.cjs','scripts/evaluar.ps1'])sources[name]=crypto.createHash('sha256').update(fs.readFileSync(name)).digest('hex');
  report.sourceSha256=sources;
  fs.writeFileSync('build/evaluation-stage7.json',JSON.stringify(report,null,2)+'\n');
  const columns=['scenario','image','latencyMs','binaryBytes','fullEquivalentBytes','savingFraction','serverControlBytes','clientControlBytes'];
  fs.writeFileSync('build/evaluation-stage7.csv',columns.join(',')+'\n'+report.rows.map(r=>columns.map(c=>r[c]).join(',')).join('\n')+'\n');
  console.log('Reporte: build/evaluation-stage7.json; memoria servidor:',report.resources.peakObservedServerWorkingSetBytes);
 }finally{server.kill();await exited;if(sampler){sampler.kill();await sampleExit;}}
})().catch(e=>{console.error(e);process.exitCode=1;});
