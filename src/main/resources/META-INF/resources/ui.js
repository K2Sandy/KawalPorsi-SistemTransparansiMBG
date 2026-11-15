// Progressive enhancements only. Java validates all monetary values and decisions.
(() => {
  if (window.kawalControlsInstalled) return;
  window.kawalControlsInstalled = true;
  const money = value => 'Rp' + Math.round(value).toLocaleString('id-ID');
  function calculate(){
    const area=document.querySelector('.entry');
    if(!area)return;
    let valid=true,total=0;
    area.querySelectorAll('#lines .line').forEach(row=>{
      const choice=row.querySelector('select').selectedOptions[0];
      const qty=Number(row.querySelector('[name=qty]').value), cost=Number(row.querySelector('[name=amount]').value);
      const complete=choice?.value && qty>0 && cost>0;
      if(complete) total+=cost; else valid=false;
      row.querySelector('[data-reference]').textContent=choice?.value ? choice.dataset.kind+' · Acuan '+money(choice.dataset.price)+'/kg'+(complete?' · Aktual '+money(cost/qty)+'/kg':''):'';
    });
    const portions=Number(area.querySelector('[name=portions]').value), base=Number(area.dataset.isb);
    const value=total/portions,delta=(value/base-1)*100;
    area.querySelector('#cost').textContent=portions && total?money(value):'—';
    area.querySelector('#total').textContent=money(total);
    area.querySelector('#evaluation').textContent=valid&&portions?'Deviasi biaya per porsi '+delta.toLocaleString('id-ID',{maximumFractionDigits:2})+'% · Ambang ±'+Number(area.dataset.threshold)+'%':'Lengkapi baris bahan dan jumlah porsi.';
    area.querySelector('#needle').style.left=(Number.isFinite(delta)?Math.max(0,Math.min(100,50+2*delta)):50)+'%';
  }
  document.addEventListener('input', calculate);
  document.addEventListener('change', calculate);
})();
