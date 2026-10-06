// Demostración offline aislada; usa una fixture de pocos cientos de KiB.
const fs=require('node:fs'),path=require('node:path'),net=require('node:net');
const {spawn,spawnSync}=require('node:child_process');
process.chdir(path.resolve(__dirname,'..'));
const tests=[];
const network=process.argv.includes('--network');
async function run(command,args,label,env=process.env) {
 const result=await new Promise((resolve,reject)=>{
  const child=spawn(command,args,{windowsHide:true,env});let output='';
  child.stdout.on('data',d=>{output+=d;process.stdout.write(d);});child.stderr.on('data',d=>{output+=d;process.stderr.write(d);});
  child.on('error',reject);child.once('exit',code=>resolve({label,code,output}));
 });tests.push(result);if(result.code!==0)throw Error(label+' falló: '+result.code);return result.output;
}
(async()=>{
 const probe=spawnSync(process.env.PRIB_JAVA||'java',['-XshowSettings:properties','-version'],{windowsHide:true,encoding:'utf8'});
 if(probe.status!==0)throw Error(probe.stderr||'Java no disponible');
 const home=probe.stderr.match(/java.home\s*=\s*(.+)/)?.[1].trim();if(!home)throw Error('No se identificó JDK');
 const java=path.join(home,'bin',process.platform==='win32'?'java.exe':'java');
 for(const name of ['ClientCacheTest','DeltaCodecTest'])await run(java,['-Xmx256m','-cp','build/classes','prib.'+name],name);
 const output=await run(java,['-Xmx256m','-cp','build/classes','prib.DeltaProtocolTest'],'DeltaProtocolTest');
 const fixture=output.match(/Fixture navegador: (.+)/)?.[1].trim();
 if(!fixture||!fs.existsSync(fixture))throw Error('No se generó la fixture');
 for(const name of ['test-cache','test-delta','test-metrics','test-sha256'])await run(process.execPath,['scripts/'+name+'.cjs'],name);
 const port=await new Promise(resolve=>{const s=net.createServer();s.listen(0,'127.0.0.1',()=>{const p=s.address().port;s.close(()=>resolve(p));});});
 const server=spawn(java,['-Xmx256m','-Dprib.network='+network,'-cp','build/classes','prib.PribServer',String(port),fixture],{windowsHide:true});
 const exited=new Promise(resolve=>{server.once('exit',resolve);server.once('error',resolve);});
 try {
  await new Promise((resolve,reject)=>{
   const timer=setTimeout(()=>reject(Error('Servidor no inició')),15000);
   server.stdout.on('data',d=>{if(d.toString().includes('PRIB:')){clearTimeout(timer);resolve();}});
   server.stderr.on('data',d=>process.stderr.write(d));server.once('error',e=>{clearTimeout(timer);reject(e);});
   server.once('exit',code=>{clearTimeout(timer);reject(Error('Servidor terminó '+code));});
  });
  const env={...process.env,PRIB_URL:'http://localhost:'+port};
  await run(process.execPath,['scripts/browser/delta.cjs'],'Chrome cuatro modos y cuatro recuperaciones',env);
  await run(process.execPath,['scripts/browser/demo.cjs'],'Chrome créditos, generaciones y reconexión',env);
  if(network) {
   // Preferir Radmin; una interfaz IPv4 local permite comprobar también HTTP no seguro.
   const adapters=Object.entries(require('node:os').networkInterfaces()).sort(([a],[b])=>Number(/radmin/i.test(b))-Number(/radmin/i.test(a)));
   const address=adapters.flatMap(([,items])=>items||[]).find(item=>item.family==='IPv4'&&!item.internal)?.address;
   if(!address)throw Error('La prueba de red requiere una interfaz IPv4 activa');
   const remote={...env,PRIB_URL:'http://'+address+':'+port};
   await run(process.execPath,['scripts/browser/network.cjs'],'Chrome localhost e IPv4 simultáneos',remote);
   await run(process.execPath,['scripts/browser/delta.cjs'],'Chrome HTTP IPv4: modos y recuperación SHA-256',remote);
   await run(process.execPath,['scripts/browser/demo.cjs'],'Chrome HTTP IPv4: créditos y sesiones',remote);
  }
  const report={date:new Date().toISOString(),fixture:'512x128; semilla 6106; cuatro bloques RGB',java:probe.stderr.match(/java.version\s*=\s*(.+)/)?.[1].trim(),node:process.version,
   tests,browser:JSON.parse(fs.readFileSync('build/demo-browser.json','utf8')),
   screenshots:['build/browser-check/etapa-6-delta.png','build/browser-check/etapa-6-recuperacion.png','build/browser-check/etapa-8-creditos.png']};
  fs.writeFileSync('build/demo-stage8.json',JSON.stringify(report,null,2)+'\n');
  console.log('PASS demostración completa: build/demo-stage8.json');
 }finally{server.kill();await exited;}
})().catch(e=>{console.error(e);process.exitCode=1;});
