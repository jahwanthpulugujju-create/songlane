// Enrich the generated SBOM from first-party Maven POM license declarations.
import fs from 'node:fs';
import path from 'node:path';
const root=path.resolve(import.meta.dirname,'..');
const file=path.join(root,'docs/sbom.cdx.json');
const bom=JSON.parse(fs.readFileSync(file,'utf8'));
const queue=bom.components.filter(c=>!c.licenses?.length);
const unresolved=[];
const decode=s=>s.replace(/&amp;/g,'&').replace(/&lt;/g,'<').replace(/&gt;/g,'>').trim();
async function licensesAt(url,depth=0){
  const response=await fetch(url,{signal:AbortSignal.timeout(15000)});
  if(!response.ok)throw new Error(`HTTP ${response.status}`);
  const pom=await response.text();
  const section=pom.match(/<licenses>([\s\S]*?)<\/licenses>/)?.[1]||'';
  const licenses=[...section.matchAll(/<license>([\s\S]*?)<\/license>/g)].map(m=>{
    const name=decode(m[1].match(/<name>([\s\S]*?)<\/name>/)?.[1]||'');
    const link=decode(m[1].match(/<url>([\s\S]*?)<\/url>/)?.[1]||'');
    return {license:{name,...(link?{url:link}:{})}};
  }).filter(l=>l.license.name);
  if(licenses.length)return {licenses,evidence:url};
  const parent=pom.match(/<parent>([\s\S]*?)<\/parent>/)?.[1]||'';
  const group=parent.match(/<groupId>(.*?)<\/groupId>/)?.[1];
  const name=parent.match(/<artifactId>(.*?)<\/artifactId>/)?.[1];
  const version=parent.match(/<version>(.*?)<\/version>/)?.[1];
  if(depth<3&&[group,name,version].every(s=>s&&/^[a-zA-Z0-9._-]+$/.test(s))){
    return licensesAt(`https://repo.maven.apache.org/maven2/${group.replaceAll('.','/')}/${name}/${version}/${name}-${version}.pom`,depth+1);
  }
  throw new Error('No direct or verified inherited POM license');
}
async function worker(){
  while(queue.length){
    const component=queue.shift();
    const base=component.group.startsWith('androidx.')?'https://dl.google.com/dl/android/maven2':'https://repo.maven.apache.org/maven2';
    const url=`${base}/${component.group.replaceAll('.','/')}/${component.name}/${component.version}/${component.name}-${component.version}.pom`;
    try{
      const result=await licensesAt(url);
      component.licenses=result.licenses;
      component.properties=[{name:'songlane:license-evidence',value:result.evidence}];
    }catch(error){unresolved.push(`${component.group}:${component.name}:${component.version} — ${error.message}`)}
  }
}
await Promise.all(Array.from({length:6},worker));
fs.writeFileSync(file,JSON.stringify(bom,null,2)+'\n');
const lines=bom.components.map(c=>`- ${c.group}:${c.name}:${c.version} — ${c.licenses?.map(l=>l.license.name||l.license.id).join('; ')||'Unverified (requires review)'}`);
fs.writeFileSync(path.join(root,'docs/DEPENDENCY_LICENSES.md'),'# Runtime dependency license evidence\n\nGenerated from resolved artifacts and their cached or official Maven POM declarations. This does not replace upstream notices or legal review. Artifact hashes and evidence URLs are in sbom.cdx.json.\n\n'+lines.join('\n')+'\n');
console.log(`Resolved license evidence for ${bom.components.length-unresolved.length}/${bom.components.length} runtime components.`);
if(unresolved.length)console.log(unresolved.join('\n'));
