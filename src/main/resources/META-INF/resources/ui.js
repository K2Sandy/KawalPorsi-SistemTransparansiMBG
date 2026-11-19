// Progressive enhancements only. Java validates all monetary values and decisions.
(() => {
  if (window.kawalControlsInstalled) return;
  window.kawalControlsInstalled = true;
  const money = value => 'Rp' + Math.round(value).toLocaleString('id-ID');
  function calculate(){
    const area=document.querySelector('.entry');
    if(!area)return;
    const rows=area.querySelectorAll('#lines .line');
    let valid=rows.length>0,total=0;
    rows.forEach(row=>{
      const choice=row.querySelector('select').selectedOptions[0];
      const qtyField=row.querySelector('[name=qty]'), costField=row.querySelector('[name=amount]');
      const qty=Number(qtyField.value), cost=Number(costField.value);
      const complete=choice?.value && qtyField.validity.valid && costField.validity.valid;
      if(complete) total+=cost; else valid=false;
      row.querySelector('[data-reference]').textContent=choice?.value ? choice.dataset.kind+' · Acuan '+money(choice.dataset.price)+'/kg'+(complete?' · Aktual '+money(cost/qty)+'/kg':''):'';
    });
    const portions=Number(area.querySelector('[name=portions]').value), base=Number(area.dataset.isb);
    const complete=valid && Number.isInteger(portions) && portions>=1 && portions<=1000000;
    const value=total/portions,delta=(value/base-1)*100;
    area.querySelector('#cost').textContent=complete?money(value):'—';
    area.querySelector('#total').textContent=money(total);
    area.querySelector('#evaluation').textContent=complete?'Deviasi biaya per porsi '+delta.toLocaleString('id-ID',{maximumFractionDigits:2})+'% · Ambang ±'+Number(area.dataset.threshold)+'%':'Lengkapi baris bahan dan jumlah porsi yang valid.';
    area.querySelector('#needle').style.left=(complete?Math.max(0,Math.min(100,50+2*delta)):50)+'%';
  }
  document.addEventListener('input', calculate);
  document.addEventListener('change', calculate);
  document.addEventListener('click', e=>{
    const btn=e.target.closest('button');
    if(!btn)return;
    if(btn.hasAttribute('data-password')){
      const input=btn.parentNode.querySelector('input');input.type=input.type==='password'?'text':'password';btn.textContent=input.type==='password'?'Tampilkan':'Sembunyikan';
    }else if(btn.hasAttribute('data-add')){
      const rows=document.querySelector('#lines');if(rows.children.length<40)rows.append(document.querySelector('#line-template').content.cloneNode(true));calculate();
    }else if(btn.hasAttribute('data-remove')){
      const row=btn.closest('.line');if(row.parentNode.children.length>1)row.remove();else row.querySelectorAll('input,select').forEach(field=>field.value='');calculate();
    }else if(btn.hasAttribute('data-back'))history.back();
  });
  // Recalculate the summary when Flow attaches a new view.
  new MutationObserver(list=>{if(list.some(m=>[...m.addedNodes].some(n=>n.nodeType===1&&(n.matches('.entry')||n.querySelector('.entry')))))calculate();}).observe(document.documentElement,{childList:true,subtree:true});
  calculate();
})();
