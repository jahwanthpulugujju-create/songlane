// Original, deterministic oscillator sketches. No samples or third-party recordings.
import fs from 'node:fs';
import path from 'node:path';
const out=path.resolve(import.meta.dirname,'../app/src/main/assets/demo');
fs.mkdirSync(out,{recursive:true});
const rate=22050,seconds=45,n=rate*seconds;
const roots=[130.81,146.83,110,164.81,174.61,196,123.47,155.56];
for(let k=0;k<8;k++){
  const buffer=Buffer.alloc(44+n*2);
  buffer.write('RIFF');buffer.writeUInt32LE(36+n*2,4);buffer.write('WAVEfmt ',8);buffer.writeUInt32LE(16,16);buffer.writeUInt16LE(1,20);buffer.writeUInt16LE(1,22);buffer.writeUInt32LE(rate,24);buffer.writeUInt32LE(rate*2,28);buffer.writeUInt16LE(2,32);buffer.writeUInt16LE(16,34);buffer.write('data',36);buffer.writeUInt32LE(n*2,40);
  const ratios=[1,1.25,1.5,2,2.5];
  for(let i=0;i<n;i++){
    const t=i/rate,fade=Math.min(1,t/3,(seconds-t)/4);
    let v=0;
    for(let j=0;j<4;j++) v+=Math.sin(2*Math.PI*roots[k]*ratios[j]*t+0.12*Math.sin(t*(j+1)/3))*(0.07+0.025*Math.sin(t/(2+j)+j));
    const step=Math.floor(t/(k%2?0.375:0.75)),phase=t%(k%2?0.375:0.75);
    const freq=roots[k]*ratios[(step+k)%ratios.length]*2;
    v+=Math.sin(2*Math.PI*freq*t)*Math.exp(-phase*9)*(k%2?0.13:0.07);
    buffer.writeInt16LE(Math.round(Math.max(-1,Math.min(1,v*fade))*25000),44+i*2);
  }
  fs.writeFileSync(path.join(out,`${k}.wav`),buffer);
}
console.log('Generated eight original 45-second PCM sound sketches.');
