(function(){
  var $=function(s,r){return (r||document).querySelector(s)},$$=function(s,r){return Array.prototype.slice.call((r||document).querySelectorAll(s))};
  var reduce=matchMedia('(prefers-reduced-motion: reduce)').matches;
  var fine=matchMedia('(hover:hover) and (pointer:fine)').matches;

  /* 로고 링 = 스크롤 진행도 */
  var prog=$('#prog');
  var onScroll=function(){
    var max=document.documentElement.scrollHeight-innerHeight;
    var p=max>0?Math.min(1,Math.max(0,scrollY/max)):.75;
    prog.setAttribute('stroke-dasharray',(6+p*94).toFixed(1)+' 100');
  };
  addEventListener('scroll',onScroll,{passive:true});addEventListener('resize',onScroll);onScroll();

  /* 밤하늘 — 반짝이는 별과 가끔 지나가는 별똥별 */
  var cv=$('#sky'),sky=null;
  if(cv&&cv.getContext){
    sky=(function(){
      var ctx=cv.getContext('2d'),W=0,H=0,stars=[],met=null,next=1200,on=false,raf=0;
      function size(){
        var d=Math.min(2,devicePixelRatio||1);W=cv.clientWidth;H=cv.clientHeight;
        cv.width=Math.round(W*d);cv.height=Math.round(H*d);ctx.setTransform(d,0,0,d,0,0);
        var n=Math.min(220,Math.round(W*H/8500));stars=[];
        for(var i=0;i<n;i++)stars.push({x:Math.random()*W,y:Math.random()*H,r:.5+Math.random()*1.2,p:Math.random()*6.28,s:.5+Math.random()*1.6,four:Math.random()<.1});
      }
      function star4(x,y,r){ctx.beginPath();ctx.moveTo(x,y-r);ctx.quadraticCurveTo(x,y,x+r,y);ctx.quadraticCurveTo(x,y,x,y+r);ctx.quadraticCurveTo(x,y,x-r,y);ctx.quadraticCurveTo(x,y,x,y-r);ctx.fill()}
      function draw(t){
        ctx.clearRect(0,0,W,H);
        for(var i=0;i<stars.length;i++){var s=stars[i];
          ctx.globalAlpha=.18+.6*(.5+.5*Math.sin(t/1000*s.s+s.p));
          if(s.four){ctx.fillStyle='#7CF0E2';star4(s.x,s.y,s.r*3.4)}
          else{ctx.fillStyle='#EEF4F6';ctx.beginPath();ctx.arc(s.x,s.y,s.r,0,6.28);ctx.fill()}
        }
        if(!met&&on&&t>next){var g=Math.random()<.3;met={x:Math.random()*W*.75,y:-30,vx:7+Math.random()*4,vy:4+Math.random()*2.5,c:g?'244,183,64':'124,240,226'}}
        if(met){
          var tx=met.x-met.vx*16,ty=met.y-met.vy*16,gr=ctx.createLinearGradient(met.x,met.y,tx,ty);
          gr.addColorStop(0,'rgba(255,255,255,.95)');gr.addColorStop(.25,'rgba('+met.c+',.8)');gr.addColorStop(1,'rgba('+met.c+',0)');
          ctx.globalAlpha=1;ctx.strokeStyle=gr;ctx.lineWidth=2.2;ctx.lineCap='round';ctx.beginPath();ctx.moveTo(met.x,met.y);ctx.lineTo(tx,ty);ctx.stroke();
          met.x+=met.vx;met.y+=met.vy;
          if(met.y>H+80||met.x>W+80){met=null;next=t+2600+Math.random()*4200}
        }
        ctx.globalAlpha=1;
      }
      function loop(t){draw(t);if(on)raf=requestAnimationFrame(loop)}
      size();draw(0);
      addEventListener('resize',function(){size();if(!on)draw(0)});
      return {start:function(){if(!on){on=true;raf=requestAnimationFrame(loop)}},stop:function(){on=false;cancelAnimationFrame(raf)}};
    })();
  }

  if(reduce||!('IntersectionObserver' in window))return;

  if(sky)new IntersectionObserver(function(es){es[0].isIntersecting?sky.start():sky.stop()}).observe(cv);

  /* 앱 이름은 글자 하나씩 올라온다 */
  var name=$('#appName');
  if(name){
    var txt=name.textContent;name.textContent='';
    txt.split('').forEach(function(ch,i){var s=document.createElement('span');s.textContent=ch===' '?' ':ch;s.style.setProperty('--i',i);s.setAttribute('aria-hidden','true');name.appendChild(s)});
    name.classList.add('arm');
  }
  var io=new IntersectionObserver(function(es){es.forEach(function(e){
    if(!e.isIntersecting)return;
    e.target.classList.add('in');io.unobserve(e.target);
  })},{threshold:.4});
  if(name)io.observe(name);

  if(!fine)return;

  /* 포인터를 따라 기우는 링 · 폰 화면, 남색 판의 불빛, 끌려오는 버튼 */
  var hero=$('#hero'),orb=$('#orb');
  if(hero&&orb){
    hero.addEventListener('pointermove',function(e){var r=hero.getBoundingClientRect();
      orb.style.setProperty('--ry',(((e.clientX-r.left)/r.width-.5)*22).toFixed(2)+'deg');
      orb.style.setProperty('--rx',((.5-(e.clientY-r.top)/r.height)*16).toFixed(2)+'deg')});
    hero.addEventListener('pointerleave',function(){orb.style.setProperty('--rx','0deg');orb.style.setProperty('--ry','0deg')});
  }
  var app=$('#app');
  if(app)app.addEventListener('pointermove',function(e){var r=app.getBoundingClientRect();
    app.style.setProperty('--mx',(e.clientX-r.left)+'px');app.style.setProperty('--my',(e.clientY-r.top)+'px')});
  $$('.frame').forEach(function(t){
    t.addEventListener('pointermove',function(e){var r=t.getBoundingClientRect(),x=(e.clientX-r.left)/r.width,y=(e.clientY-r.top)/r.height;
      t.classList.add('live');
      t.style.setProperty('--ry',((x-.5)*14).toFixed(2)+'deg');t.style.setProperty('--rx',((.5-y)*12).toFixed(2)+'deg');
      t.style.setProperty('--gx',(x*100).toFixed(1)+'%');t.style.setProperty('--gy',(y*100).toFixed(1)+'%')});
    t.addEventListener('pointerleave',function(){t.classList.remove('live');t.style.setProperty('--rx','0deg');t.style.setProperty('--ry','0deg')});
  });
  $$('.btn').forEach(function(b){
    b.addEventListener('pointermove',function(e){var r=b.getBoundingClientRect();
      b.style.setProperty('--bx',((e.clientX-r.left-r.width/2)*.22).toFixed(1)+'px');
      b.style.setProperty('--by',((e.clientY-r.top-r.height/2)*.3).toFixed(1)+'px')});
    b.addEventListener('pointerleave',function(){b.style.setProperty('--bx','0px');b.style.setProperty('--by','0px')});
  });
})();
